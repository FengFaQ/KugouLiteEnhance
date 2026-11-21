# KugouLiteEnhance

对参考样本 `啄木鸟_1.0.5.apk` 进行静态逆向分析，提取酷狗概念版（酷狗概念版 / Kugou Lite）相关 **hook 类型**后，独立实现的一个 LSPosed/Xposed 增强模块。

> 本项目为逆向分析与学习研究产物，**不复制、不捆绑**参考样本的代码或专有 native 库，仅实现同类的功能抽象。

## 仓库结构

```
.
├── ANALYSIS.md        # 逆向分析报告：样本、工具链、APK 结构、hook 框架、类映射与机制
├── HOOK_TYPES.md      # hook 类型清单：18 项功能 → 参考类 + 技术抽象
└── KugouLiteEnhance/  # 独立、可构建的 Android LSPosed 模块
    ├── README.md      # 模块说明与构建指南
    ├── build.gradle / settings.gradle
    └── app/src/main/
        ├── AndroidManifest.xml
        ├── assets/{xposed_init,hook_config.json}
        └── java/com/example/kugouliteenhance/...
```

## 提取的 hook 类型

去首页 Banner 广告 · 去 TMEAB 广告 · 去启动页广告 · 关闭自动更新检测 · 去播放页小广告 · H5 界面拦截 · 跳过引导页 · 绕过签名校验 · 关闭青年弹窗 · 拦截 VIP 到期卡片 · 移除概念实验室 · 去搜索页 Banner 广告 等。

抽象为 7 类可复用技术：返回常量替换、方法体替换、方法后置处理、Activity 启动拦截、字段/方法标志修改（签名校验）、native 扩展槽、DexKit 运行时特征发现。详见 [HOOK_TYPES.md](HOOK_TYPES.md) 与 [ANALYSIS.md](ANALYSIS.md)。

## 构建

见 [KugouLiteEnhance/README.md](KugouLiteEnhance/README.md)。需要 Android SDK（compileSdk 34）与 JDK 17：

```bash
cd KugouLiteEnhance && ./gradlew assembleDebug
```

## 免责声明

本项目仅用于逆向工程学习与研究，请遵守相关法律法规及目标软件的许可协议。作者不对任何滥用行为负责。
