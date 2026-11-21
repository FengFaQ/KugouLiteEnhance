# 啄木鸟 APK —— 酷狗概念版增强 LSP 模块逆向分析

> 本文是对参考样本 `啄木鸟_1.0.5.apk` 的静态逆向分析记录，目的是提取其中"酷狗概念版（酷概念版 / Kugou Lite）"相关的 **hook 类型**，并据此整理出一个**独立的、结构清晰的 LSPosed 模块工程**。
>
> 本文只描述分析与技术实现，不含任何对用户数据的收集、上传或滥用逻辑；所有结论均来自对本地样本的反编译与字符串分析。

---

## 1. 样本信息

| 项目 | 值 |
|---|---|
| 文件 | `啄木鸟_1.0.5.apk` |
| 大小 | 23,957,100 字节 |
| SHA-256 | `8C18B6B0DE1541410EF7F109BB337AF134A8906B41C95626D9FF263091392C3D` |
| 版本 | V1.0.5（versionCode 6），日期 2026-06-13 |
| 类型 | Xposed / LSPosed 框架模块（多目标：微信、小红书、酷狗概念版、汽水音乐、酷我音乐、抖音、QQ 等） |
| 目标包名（酷狗概念版） | `com.kugou.android.lite`（需按实际版本核对） |

## 2. 分析环境与方法

- 宿主为 Windows，但无 Java / jadx / unzip；借助 **WSL（StepOS-Ubuntu，Ubuntu 24.04.3，root，WSL2）** 完成分析。
- 工具链：`unzip`（解包）、`jadx v1.5.0`（反编译，`-d _decompiled --no-res -j 4`）、`strings`（原生库与 DEX 字符串）、Python 3（辅助脚本）。
- 产物目录：
  - `_extracted/`：APK 解包内容（classes*.dex、lib/、assets/、bsh/ 等）。
  - `_decompiled/sources/`：jadx 反编译出的 Java 源码（约 4043 个类，575 个反编译错误，源于重度混淆）。
  - `_tools/`：jadx 工具。

## 3. APK 结构

```
classes.dex          15.9 MB   —— 主 dex（框架 + 各目标 hook）
classes2.dex          1.8 MB
classes3.dex          2.26 MB
lib/arm64-v8a/
  libdexkit.so         290 KB  —— DexKit 动态 dex 查找（native 桥）
  libkgsignaturehook.so 15 KB  —— 酷狗签名校验绕过（native）
  libshadowhook.so      90 KB  —— 字节盾 inline hook 引擎
  libshadowhook_nothing.so
  libSkeruy.so         4.9 MB  —— 通用 native hook / RegisterNatives 辅助
  libJunZi.so          5.1 MB
  libnoactivehook.so    17 KB
  libnop.so           913 KB
assets/update.json            —— 更新日志（各目标功能清单）
assets/WPINFO.DAT             —— 加密配置 blob（base64）
bsh/{commands,servlet,util}   —— BeanShell 脚本引擎与命令集（用于运行时脚本能力）
```

## 4. 框架识别

从 DEX 字符串与 import 确认，样本基于以下组件：

| 组件 | 证据 | 用途 |
|---|---|---|
| **libxposed API** | `io.github.libxposed.api.XposedModule`、`XposedModuleInterface.PackageReadyParam` | 现代 LSPosed 模块入口 |
| **XposedHelpers** | `de.robv.android.xposed.XposedHelpers`、`XC_MethodHook`、`XC_MethodReplacement`、`Unhook` | 传统方法 hook |
| **DexKit** | `org.luckypray.dexkit.DexKitBridge`、`libdexkit.so`、`hookByDexKit` | 运行时按特征动态定位被混淆的类/方法 |
| **shadowhook** | `com.bytedance.shadowhook`、`libshadowhook.so` | SO 层 inline hook（签名校验绕过） |
| **BeanShell** | `bsh.*`、`bsh/` 资源目录 | 运行时脚本执行能力 |

## 5. 酷狗概念版 hook 包结构

目标代码集中在 `LXposed/Woodpecker/kugoulite/`，主要类：

- **入口/调度**：`MainHook`、`MainHook$1/$2/$3`
- **签名校验绕过**：`SignatureHook`（+ native `libkgsignaturehook.so`）
- **启动页广告**：`SplashHook`（+ `SplashHook$1`）
- **设置入口注入**：`SettingEntry`（+$1..$4）
- **动态 Activity（反检测/桩 Activity）**：`DynamicActivity`（内部 `ActivityManagerHandler`、`PackageManagerHandler`、`ProxyHandlerCallback`）、`WoodpeckerActivity`（含 Compose `SettingsFragment`）
- **功能实现类（混淆命名）**：`C1030a`..`C1045p`、`A`–`P`、`a`–`z`、`q`..`z`、`AbstractC1037h`/`AbstractC1038i` 等

## 6. 主入口与调度流程

`MainHook` 是 `final class`，持有静态状态：

```java
MODULE_PATH, currentActivityName, Context kgApplication,
volatile DexKitBridge sDexKitBridge, H signinManager, TAG
```

其内部类 `AnonymousClass1 extends XC_MethodHook` 在 `PackageReady` 阶段集中调度各功能初始化（方法号为 jadx 生成）：

| 调度方法 | 目标 | 推断功能 |
|---|---|---|
| `m8751` | `SignatureHook.init(...)` | 签名校验绕过 |
| `m8752` | `t.init(...)` | 广告/入口拦截（含 `hookGateByCache`） |
| `m8753` | `C1032c.o(...)` | 通用 dexkit 查询/工具 |
| `m8754` | `K.enable(...)` | 关闭自动更新检测（`hookUpdateMethod`） |
| `m8755` | `SplashHook.enable(...)` | 去启动页广告 |
| `m8757` | `C1044o.init(...)` | Activity 启动拦截（`hookActivityLaunch`）→ 跳过引导页 |
| `m8761` | `SettingEntry.init(...)` | 模块设置入口 |
| `m8762` | `C1041l.c(...)` | 辅助 |
| `m8760/63/66` | `MainHook.c/d/e(...)` | 通用工具/回调 |
| `m8765` | `J.init(...)` | `hookReturnFalse` → 拦截广告/布尔判断 |
| `m8767` | `x.init(...)` | `hookReturnNull` → 拦截广告返回对象 |
| `m8768` | `P.init(...)` | `hookReturnVoid` |
| `m8769` | `C1045p.init(...)` | `hookActivityLaunchers` → Activity 启动链拦截 |
| `m8771` | `C1042m.init(...)` | `hookFilterLabCard` → VIP 到期卡片 / 概念实验室卡片过滤 |
| `m8772` | `C1043n.b(...)` | 辅助 |
| `m8764` | `findAndHookMethod(String,ClassLoader,String,Object[])` 包装 | 通用反射 hook 辅助 |

## 7. 签名校验绕过机制（SignatureHook）

`SignatureHook` 是**唯一暴露大量 native 方法**的类，对应更新日志"绕过 JAVA 层和 SO 层签名校验（可直接内置）"：

```java
native void   a(Class cls);                       // native 桥注册
native String b();                                // 取目标类名
native boolean c(Method m), d(Field f), e(Method m), f(Method m); // 检测校验点
native String  g(C1033d, String), h();            // 字符串/类名解析
native void   nativeInstall();                    // 安装 native hook
```

Java 侧辅助方法：

- `forceLegalFlag(Class)`：遍历字段，用 `c()/d()` 识别校验字段，通过 `C0484.m21614(field,true)` + `C0386.m18729(...)` 强制修改可访问/合法标志。
- `hookBooleanCheckMethods(Class)`：遍历方法，识别布尔校验方法，用 `XC_MethodReplacement.returnConstant(...)` 使其恒返回真/假。
- `hookStaticVoidContextMethods(Class)`：hook 静态 `void(Context)` 校验方法，回调 `new z(cls,1)`。
- `hookDetectClass(PackageReadyParam, String)`：`XposedHelpers.findClass` 定位校验类后依次执行上述处理。
- `installNativeHook()`：`System.loadLibrary` 加载 `libkgsignaturehook.so` → `nativeInstall()` → 置位 `f11655a`。
- `preloadDexKitCache(...)`：为混淆类/方法建立 DexKit 缓存。

字符串证据：`Java_Xposed_Woodpecker_kugoulite_SignatureHook_nativeInstall`（JNI 导出符号）。

## 8. 启动页广告（SplashHook）

```java
static volatile boolean sHooked;
static int versionCode; static String versionName;
native boolean a();  native void b();  native void c(Context);
native void enable(PackageReadyParam);   // 去启动页广告（重写逻辑）
native void logVersion(Context);
```

内部类 `AnonymousClass1 extends XC_MethodHook` 的 `afterHookedMethod` 也是 native 实现——说明广告跳过动作在 SO 层完成（对抗 Java 层检测）。

## 9. 反混淆观察

参考样本使用了两种保护措施（**独立模块不复制这些保护**，以保持可读可维护）：

1. **控制流平坦化**：大量方法体是 `short[]`（XOR 加密表）+ `while/switch` 分发循环，jadx 无法还原，表现为 `UnsupportedOperationException: Method not decompiled` + 字节码 dump。
2. **字符串加密**：通过 `C0173.m6839`、`C0296.m16177` 等 helper 在运行时解密，静态看不到真实的类名/方法名字符串。

因此**精确的目标类名/方法名无法静态还原**——参考样本本身也是通过 DexKit 在运行时按特征动态查找的。独立模块采用同一思路（见第 11 节）。

## 10. 更新日志中的功能清单（酷狗概念版 V1.0.5）

去除首页 Banner 广告 / 去除 TMEAB 广告 / 重写去除启动页广告逻辑 / 关闭自动更新检测 / 去除播放页左下角小广告 / 重写 H5 界面拦截 / 重写跳过引导页逻辑 / 绕过 JAVA 层和 SO 层签名校验 / 关闭青年弹窗 / 拦截 VIP 到期卡片 / 移除发现页概念实验室 / 去除搜索页 Banner 广告。

## 11. 独立模块设计结论

由于精确符号不可静态恢复，独立模块采用 **"hook 类型 + 运行时特征发现"** 架构：

- 定义清晰的 `BaseHook` 抽象与 `HookManager` 调度（对应参考的 `MainHook` 调度表）。
- 用 `DexKitHelper` 按"方法名 / 字符串常量 / 返回类型"等特征在运行时定位目标（对应 `hookByDexKit` / `hookByCache`）。
- 每种功能一个独立 Hook 类（广告、启动页、引导页、更新、H5、VIP 卡片、概念实验室、青年弹窗、签名校验）。
- 特征配置外置到 `assets/hook_config.json`，便于按酷狗概念版版本调整，无需改代码。
- 开关由 `HookConfig` 统一管理（对应 `SettingEntry` 的配置意图）。

详见 `HOOK_TYPES.md` 与 `KugouLiteEnhance/` 工程。
