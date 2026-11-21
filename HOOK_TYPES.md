# 酷狗概念版 —— Hook 类型清单

> 从 `啄木鸟_1.0.5.apk` 的 `LXposed/Woodpecker/kugoulite/` 包提取并归纳。
> 这是"独立软件"要实现的能力清单；每条都标注了参考样本中的对应类与实现手法。

## Hook 类型总览

| # | Hook 类型 | 参考样本实现 | 手法分类 | 对应更新日志功能 |
|---|---|---|---|---|
| 1 | 方法返回常量替换 | `J.init` → `hookReturnFalse`；`SignatureHook.hookBooleanCheckMethods` → `XC_MethodReplacement.returnConstant` | 返回值篡改 | 拦截广告、签名校验、关闭更新 |
| 2 | 返回 null 替换 | `x.init` → `hookReturnNull` | 返回值篡改 | 去除广告视图/数据对象 |
| 3 | 返回 void 替换 | `P.init` → `hookReturnVoid` | 返回值篡改 | 屏蔽方法副作用 |
| 4 | Banner 广告移除 | `hookReturnNoBanner(Method, Method)`（C1032c） | 视图/数据拦截 | 首页 Banner、搜索页 Banner |
| 5 | 启动页广告跳过 | `SplashHook.enable(PackageReadyParam)` + native `afterHookedMethod` | SO 层 hook | 重写去除启动页广告 |
| 6 | 引导页跳过 | `C1044o.init` → `hookActivityLaunch`；`C1045p.init` → `hookActivityLaunchers` | Activity 启动拦截 | 重写跳过引导页逻辑 |
| 7 | 自动更新检测关闭 | `K.enable` → `hookUpdateMethod` | 方法 hook | 关闭自动更新检测 |
| 8 | H5 界面拦截重写 | `t.init` → `hookGateByCache` + `preloadDexKitCache` | DexKit 动态定位 + hook | H5 界面拦截（网络繁忙重试） |
| 9 | VIP 到期卡片拦截 | `C1042m.init` → `hookFilterLabCard` | 卡片过滤 hook | 拦截 VIP 到期卡片 |
| 10 | 概念实验室移除 | `C1042m.init` → `hookFilterLabCard` / `hookByDexKit` | 卡片/入口过滤 | 移除发现页概念实验室 |
| 11 | 青年弹窗关闭 | `C1032c` / `x.init` 系列（弹窗返回 null/false） | 弹窗拦截 | 关闭青年弹窗 |
| 12 | TMEAB 广告移除 | 广告类 hook（返回 null/false） | 返回值篡改 | 去除 TMEAB 广告 |
| 13 | 播放页小广告移除 | 广告类 hook | 视图拦截 | 去除播放页左下角小广告 |
| 14 | JAVA 层签名校验绕过 | `SignatureHook.forceLegalFlag` + `hookBooleanCheckMethods` + `hookStaticVoidContextMethods` | 字段标志修改 + 方法替换 | 绕过 JAVA 层签名校验 |
| 15 | SO 层签名校验绕过 | `SignatureHook.installNativeHook()` → `libkgsignaturehook.so` `nativeInstall()`；`libshadowhook.so` inline hook | native inline hook | 绕过 SO 层签名校验 |
| 16 | 动态类定位（反混淆基础） | `hookByDexKit` / `hookByCache` / `preloadDexKitCache`（DexKitBridge） | 运行时特征发现 | （支撑以上全部） |
| 17 | 设置入口注入 | `SettingEntry.init`（+$1..$4） | UI 入口注入 | 模块设置入口 |
| 18 | 动态 Activity / 反检测 | `DynamicActivity`（ActivityManagerHandler、PackageManagerHandler、ProxyHandlerCallback）、`WoodpeckerActivity` | 动态代理 / 桩 Activity | （支撑/隐藏模块行为） |

## 手法分类说明（独立模块要实现的"hook 类型"抽象）

1. **ReturnConstantHook** —— 让目标方法恒返回指定常量（`false`/`null`/`void`）。
2. **MethodReplaceHook** —— 用自定义实现替换目标方法（H5 拦截、广告逻辑重写）。
3. **MethodAfterHook** —— 在目标方法执行后修改结果（启动页广告 native 跳过的 Java 对应物）。
4. **ActivityLaunchHook** —— hook `Activity.startActivity` 系列（Intent、Intent+Bundle 等重载），拦截/替换启动（引导页）。
5. **FieldFlagHook** —— 修改字段/方法的访问标志与校验布尔（签名校验 JAVA 层）。
6. **DexKitDiscovery** —— 按方法名/字符串/返回类型/调用引用等特征在运行时定位被混淆的目标类与方法。
7. **NativeHook** —— 通过 shadowhook / JNI 注册在 SO 层拦截（签名校验 SO 层、启动页广告）。

## 配置驱动

独立模块把"目标特征"外置到 `assets/hook_config.json`，每种 hook 类型一条配置：

```json
{
  "hooks": [
    { "id": "remove_home_banner", "type": "ReturnConstantHook", "enabled": true,
      "target": { "methodName": ["getBanner"], "returnType": "android.view.View" },
      "value": null },
    { "id": "skip_guide", "type": "ActivityLaunchHook", "enabled": true,
      "target": { "className": ["GuideActivity"] } },
    { "id": "bypass_signature_java", "type": "FieldFlagHook", "enabled": true,
      "target": { "methodName": ["checkSignature"] } }
  ]
}
```

这样同一套代码可适配酷狗概念版不同版本（混淆类名变化只需改配置）。
