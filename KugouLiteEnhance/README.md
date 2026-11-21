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
            │   ├── AutoClaimVipHook.java     # 2.5.5 移植：看广告领VIP自动化
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
| `AutoClaimVipHook` | AutoClaimVipHook.java | 2.5.5 `getvip` 包看广告领VIP自动化（新移植） |

## 特性

- **配置驱动**：目标方法按名称/名称片段/类名片段/方法体内字符串常量/返回类型匹配，混淆类名变化只需改 `hook_config.json`，无需改代码。
- **跨版本锚点**：新增 `classNameContains` 与 `stringConstants` 两类 DexKit 锚点，实测 2.5.5→5.3.21 运行时类名完全重写后仍能通过字符串常量命中目标方法（详见根目录 `LATEST_5.3.21_COMPAT.md`）。
- **清晰分层**：入口 → 配置 → 调度 → 各 hook 类型，去掉参考样本的控制流平坦化与字符串加密，可读可维护。
- **合规**：不捆绑参考样本的专有 SO；SO 层签名绕过仅保留接口扩展槽，默认关闭。

## 最新版（5.3.21）兼容性

依据 `LATEST_5.3.21_COMPAT.md` 对 5.3.21 的实测：包名仍为 `com.kugou.android.lite`（versionName 5.3.2），13 项原 hook 中 11 项功能仍存在、仅 H5 拦截可能变化；默认配置已改用新版类名/字符串锚点。`bypass_signature_so` 因 `libkgsignaturehook.so` 在新版中已移除而默认关闭。看广告领VIP机制的关键字符串锚点（`play_report`/`award_vip_hour`/`激励领取成功~`/`6033900901778954`）在 5.3.21 中仍存在，故 `auto_claim_vip` 已按新锚点配置。

**关于「自动获取 VIP」的重要说明**：2.5.5 的自动领 VIP 本质是「看激励广告领 VIP」。客户端 `GdtSeeVideoManager` 播放 GDT 激励视频后，`AdPlayReportProtocol` 向 `gateway.kugou.com/youth/v1/ad/play_report` 上报，**服务端**依据返回的 `award_vip_hour` 真正发放 VIP。因此 `AutoClaimVipHook` 只能自动化客户端可做的部分（强制奖励标志、把上报的播放时长归一到 ≥15s），**无法绕过服务端校验直接发放 VIP**。完整机制见根目录 `VIP_2.5.5_ANALYSIS.md`。

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
