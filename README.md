# KugouLiteEnhance

对参考样本 `啄木鸟_1.0.5.apk` 进行静态逆向分析，提取酷狗概念版（酷狗概念版 / Kugou Lite）相关 **hook 类型**后，独立实现的一个 LSPosed/Xposed 增强模块。

> 本项目为逆向分析与学习研究产物，**不复制、不捆绑**参考样本的代码或专有 native 库，仅实现同类的功能抽象。

## 仓库结构

```
.
├── ANALYSIS.md                 # 逆向分析报告：样本、工具链、APK 结构、hook 框架、类映射与机制
├── HOOK_TYPES.md               # hook 类型清单：19 项功能 → 参考类 + 技术抽象
├── VIP_2.5.5_ANALYSIS.md       # 2.5.5 "看广告领VIP" 机制逆向 + 服务端校验结论
├── LATEST_5.3.21_COMPAT.md     # 最新版 5.3.21 对 13 项 hook 的逐项兼容性核验
└── KugouLiteEnhance/           # 独立、可构建的 Android LSPosed 模块
    ├── README.md      # 模块说明与构建指南
    ├── build.gradle / settings.gradle
    └── app/src/main/
        ├── AndroidManifest.xml
        ├── assets/{xposed_init,hook_config.json}
        └── java/com/example/kugouliteenhance/...
```

## 提取的 hook 类型

去首页 Banner 广告 · 去 TMEAB 广告 · 去启动页广告 · 关闭自动更新检测 · 去播放页小广告 · H5 界面拦截 · 跳过引导页 · 绕过签名校验 · 关闭青年弹窗 · 拦截 VIP 到期卡片 · 移除概念实验室 · 去搜索页 Banner 广告 · **看广告领VIP自动化（2.5.5 移植）** 等。

抽象为 8 类可复用技术：返回常量替换、方法体替换、方法后置处理、Activity 启动拦截、字段/方法标志修改（签名校验）、native 扩展槽、DexKit 运行时特征发现、自动领 VIP（奖励标志强制 + 上报时长归一化）。详见 [HOOK_TYPES.md](HOOK_TYPES.md) 与 [ANALYSIS.md](ANALYSIS.md)。

## 最新版 5.3.21 兼容性 & 2.5.5 自动领 VIP 移植

- 对最新版 `5.3.21.apk` 的逐项核验见 [LATEST_5.3.21_COMPAT.md](LATEST_5.3.21_COMPAT.md)：包名仍为 `com.kugou.android.lite`，13 项 hook 中 11 项功能仍存在，仅 H5 拦截可能变化；配置已按新版锚点更新。
- 2.5.5 "自动获取 VIP" 的完整机制见 [VIP_2.5.5_ANALYSIS.md](VIP_2.5.5_ANALYSIS.md)，并已作为新 hook `AutoClaimVipHook` 移植进模块。**必须说明**：该功能本质是"看激励广告领 VIP"，VIP 由服务端 `play_report` 返回的 `award_vip_hour` 发放；客户端 hook 只能自动化流程，无法绕过服务端校验直接发放 VIP。

## 构建

见 [KugouLiteEnhance/README.md](KugouLiteEnhance/README.md)。需要 Android SDK（compileSdk 34）与 JDK 17：

```bash
cd KugouLiteEnhance && ./gradlew assembleDebug
```

## 免责声明

本项目仅用于逆向工程学习与研究，请遵守相关法律法规及目标软件的许可协议。作者不对任何滥用行为负责。
