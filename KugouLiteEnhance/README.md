# KugouLiteEnhance —— 独立酷狗概念版增强模块

基于对参考样本 `啄木鸟_1.0.5.apk` 的静态逆向分析，提取 `LXposed/Woodpecker/kugoulite/` 包中的 **hook 类型** 后独立实现的 LSPosed 模块。

> 这是一个**独立实现**：不复用参考样本的代码、不捆绑其专有 native 库、不复制其混淆与反检测逻辑。只实现同类的功能抽象（见项目根目录 `HOOK_TYPES.md`、`ANALYSIS.md`）。

## 目录结构

```
KugouLiteEnhance/
├── settings.gradle
├── build.gradle
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── xposed_init          # 入口声明
        │   └── hook_config.json     # 所有 hook 的类型 + 目标特征（配置驱动）
        ├── res/values/{strings,arrays}.xml
        └── java/com/example/kugouliteenhance/
            ├── ModuleMain.java          # Xposed 生命周期入口
            ├── config/
            │   ├── HookConfig.java      # 配置解析
            │   └── HookRule.java        # 规则数据模型
            ├── hook/
            │   ├── BaseHook.java        # hook 抽象基类
            │   ├── HookManager.java     # 调度器（对应 reference MainHook 分发）
            │   ├── DexKitHelper.java    # 运行时特征发现（对应 hookByDexKit）
            │   ├── ReturnConstantHook.java   # 返回 false/null/void
            │   ├── MethodReplaceHook.java    # 完全替换方法逻辑（H5 拦截）
            │   ├── MethodAfterHook.java      # 方法执行后处理（启动页广告）
            │   ├── ActivityLaunchHook.java   # 拦截 Activity 启动（跳过引导页）
            │   ├── FieldFlagHook.java        # 签名校验字段/方法标志
            │   └── NativeHook.java           # SO 层 hook 扩展槽
            ├── util/XposedLog.java
            └── ui/MainActivity.java
```

## 已实现的 hook 类型（与 `HOOK_TYPES.md` 对应）

| 类型 | 类 | 参考样本对应 |
|---|---|---|
| `ReturnConstantHook` | ReturnConstantHook.java | `J`/`x`/`P` 的 hookReturnFalse/Null/Void |
| `MethodReplaceHook` | MethodReplaceHook.java | H5 界面拦截重写 |
| `MethodAfterHook` | MethodAfterHook.java | `SplashHook` native afterHookedMethod |
| `ActivityLaunchHook` | ActivityLaunchHook.java | `C1044o`/`C1045p` hookActivityLaunch |
| `FieldFlagHook` | FieldFlagHook.java | `SignatureHook` forceLegalFlag |
| `NativeHook` | NativeHook.java | `libkgsignaturehook.so` nativeInstall（扩展槽，默认关闭） |
| `DexKitDiscovery` | DexKitHelper.java | `hookByDexKit`/`hookByCache` |

## 特性

- **配置驱动**：目标方法按名称/名称片段/返回类型匹配，混淆类名变化只需改 `hook_config.json`，无需改代码。
- **清晰分层**：入口 → 配置 → 调度 → 各 hook 类型，去掉参考样本的控制流平坦化与字符串加密，可读可维护。
- **合规**：不捆绑参考样本的专有 SO；SO 层签名绕过仅保留接口扩展槽，默认关闭。

## 构建

需要 Android SDK（compileSdk 34）与 JDK 17：

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 安装与激活

1. 安装 APK；2. 在 LSPosed 中启用模块并勾选作用域 `com.kugou.android.lite`；3. 重启酷狗概念版；4. 通过 logcat 过滤 `KugouLiteEnhance` 查看 hook 应用情况。

## 重要说明

- 酷狗概念版包名 `com.kugou.android.lite` 需在实机核对；若不同，改 `hook_config.json` 与 `ModuleMain.isTarget()`。
- 由于参考样本的目标符号运行时才通过 DexKit 解析，本模块首次需在 `hook_config.json` 填入正确的方法名片段才能命中；默认配置是依据更新日志与可读符号的合理推断。
- 本项目仅用于学习与逆向分析研究，请遵守相关法律法规与软件许可。
