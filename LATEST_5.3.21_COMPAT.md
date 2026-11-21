# 酷狗概念版 5.3.21 —— Xposed Hook 兼容性报告

> 分析对象：`5.3.21.apk`（工作目录 `C:\Users\Administrator\Documents\dsh\kugou\5.3.21.apk`，115,790,578 字节）
> 解包目录：`_apk521/`（15 个 dex、AndroidManifest.xml、assets/、lib/）
> 分析时间：2026-11-21；环境 WSL StepOS-Ubuntu (root)，jadx v1.5.0，aapt (build-tools 34.0.0)
> 方法：`strings -a` 扫描全部 15 个 dex 建立字符串索引（23 MB），配合 `aapt dump badging`、manifest UTF-16 字符串、jadx `--single-class` 定点反编译。
> **诚实声明**：本 app 为重度混淆 + 控制流平坦化 + 字符串加密（见 ANALYSIS.md），绝大部分精确的方法体逻辑无法静态还原；下列结论基于"字符串/类名锚点是否存在"的证据。锚点存在 ≠ hook 一定成功，但足以支持 DexKit 运行时发现。jadx 对本 APK 单类反编译多次超时（仅 DiscoveryCardLabProtocol 成功），故多数锚点来自 DEX 字符串证据而非完整反编译。

---

## 1. 包名与版本确认

使用 `aapt dump badging 5.3.21.apk`：

```
package: name='com.kugou.android.lite'  versionCode='11620'  versionName='5.3.2'
platformBuildVersionName='14'  platformBuildVersionCode='34'
compileSdkVersion='34'
sdkVersion='22'  targetSdkVersion='30'
```

**结论：包名仍为 `com.kugou.android.lite`** —— `targetPackages` 配置无需修改。

- 注意：文件名叫 `5.3.21`，但 manifest 内 versionName = `5.3.2`、versionCode = `11620`（推断 5.3.21 为渠道/构建号标记，或 5.3.2 的补丁版本）。
- 对照：同目录 `2.5.5.apk` 的 versionCode=10597 / versionName=2.5.5，说明本系列 versionCode 持续增长。
- Manifest 中有大量非混淆的稳定组件名，如 `com.kugou.android.app.splash.SplashActivity`、`com.kugou.android.app.startguide.GuideActivity`、`com.kugou.android.app.dialog.confirmdialog.UpdateAppDialog`、`com.tencentmusic.ad.com.kugou.android.lite.TMEAdsContentProvider` —— 这些是极稳定的 DexKit 锚点。

---

## 2. 逐功能结论（12 项）

判定图例：**STILL PRESENT**（找到明确锚点）/ **LIKELY CHANGED**（有邻近候选，需运行时核对）/ **NOT FOUND**（未找到证据）。

### 2.1 去除首页 Banner 广告 —— STILL PRESENT

锚点（DEX 字符串）：
- `Lcom/kugou/android/netmusic/discovery/flow/ui/subview/FlowBannerView;`（发现页信息流 Banner 视图，含 `$a..$g`、`$BannerTouchListener`、`$onDataObserveListener` 内部类）
- `Lcom/kugou/android/netmusic/discovery/flow/model/FlowSubContact$IFlowBannerView;`
- `Lcom/kugou/android/app/home/yueku/YuekuBannerAdapter;`、`Lcom/kugou/android/app/home/yueku/YuekuBannerViewHolder;`（悦库首页 Banner）
- `Lcom/kugou/android/netmusic/bills/banner/BannerAdapter;`、字符串 `show.live.url.home_getbannerpics`
- 方法名字符串：`getBanner`、`renderBannerView start imgWidth`
- 广告 Bean：`Lcom/kugou/android/netmusic/discovery/flow/model/data/entity/AdBannerBean;`

说明：原配置的 `methodName: [getBanner, loadBanner, showBanner]` 中 `getBanner` 仍存在；但返回类型写死 `android.view.View` 可能不准（存在 `AdBannerBean` 数据对象路径）。建议改用 className 锚点 + 返回类型放宽。

### 2.2 去除 TMEAB 广告 —— STILL PRESENT（强）

锚点：
- `Lcom/kugou/framework/tmeab/TMEABManager;`（`()Lcom/kugou/framework/tmeab/TMEABManager;` — 单例 getter，稳定锚点）
- `Lcom/kugou/framework/tmeab/TMEABManager$Companion;`
- `Lcom/kugou/android/tmeab/TMEABProtocol;`、`TMEABProtocol$a/$b`、`TMEABProtocol$ExecuteInterface`
- `Lcom/kugou/android/tmeab/FeeABConfig;`、`Lcom/kugou/android/tmeab/SignABConfigHandler;`、`VipPrivBlockABConfigHandler`、`AdFreeProcessConfigHandler` 等 AB 配置处理器
- 字符串常量：`TMEAB0ad0type`、`TMEAB0sppk0type`、`TMEAB0mianmo0type`、`TMEAB0FreemiumModelTouchpoint0type`
- Manifest Provider：`com.tencentmusic.ad.com.kugou.android.lite.TMEAdsContentProvider`
- 通用广告 SDK：`Lcom/tencentmusic/ad/TMEAdConst;`

说明：TMEAB 体系完整保留，且类名几乎未混淆。`methodNameContains: [TMEAd, TMEAB]` 方向正确，但 boolean 返回类型过滤太窄；建议加 className 锚点。

### 2.3 去除启动页广告 —— STILL PRESENT（强）

锚点：
- Activity：`com.kugou.android.app.splash.SplashActivity`、`com.kugou.android.app.splash.foresplash.ForeSplashActivity`、`com.kugou.android.splash.AdShellActivity`、`com.kugou.android.app.splash.adcontainer.AdContainerActivity`
- 协议：`Lcom/kugou/android/splash/protocol/SplashLiteGetAdConfigProtocol;`（含 `$Entity`、`$ConfigEntity`、`$DataEntity`、`$IService`、`$a/$b/$c`）
- 配置：`Lcom/kugou/framework/setting/operator/SplashAdPrefs;`、`SplashAdPrefs$Keys`
- AB 处理器：`Lcom/kugou/android/app/splash/foresplash/ab/SplashManagerAbConfigHandler;`
- 混淆子包：`Lcom/kugou/android/app/splash/a..q`（启动页逻辑）、`Lcom/kugou/android/splash/img/a..i`（闪屏图）、`Lcom/kugou/android/app/boot/SplashCard;`、`SplashCard$OnADCallback`
- X5：`com.kugou.android.app.x5.X5SplashActivity`

说明：原配置 `methodNameContains: [SplashAd, LaunchAd]` + 返回 void 不一定命中（实际跳过动作可能在 Activity/native 层）。建议改为 classNameContains 锚点 `["Splash", "ForeSplash", "AdContainer"]`，并 hook 相应生命周期方法。

### 2.4 关闭自动更新检测 —— STILL PRESENT

锚点：
- `Lcom/kugou/android/update/KGUpdateInterface;`（更新接口，classes3/9/10/12）
- `Lcom/kugou/android/app/dialog/confirmdialog/UpdateAppDialog;`（Manifest 中亦有）
- 方法/日志字符串：`checkUpdate`（独立方法名字符串存在）、`checkUpdate, activity is illegal: `、`Enter checkUpdate`、`requestUpdate`、`getUpdateInfo`、`CheckAppUpdateHttpRequest:checkupdate from server`
- 华为更新 SDK：`Lcom/huawei/updatesdk/service/otaupdate/CheckUpdateCallBack;`、日志 `SelfUpdateSDKErrorCode_CheckUpdate_FAILED`、`STATUS_CHECKUPDATE_RESPONSE_IS_NULL`

说明：`checkUpdate` 方法名仍存在，原配置核心命中概率高；建议补 className 锚点 `KGUpdateInterface` / `UpdateAppDialog`。

### 2.5 去除播放页左下角小广告 —— STILL PRESENT（锚点明确）

锚点：
- `Lcom/kugou/android/app/player/entity/PlayerWidgetAd;`（播放器挂件广告实体，classes8；含构造 `(PlayerWidgetAd, KGMusicWrapper)` 重载）
- `Lcom/kugou/android/app/player/entity/PlayerUseAd;`（播放器使用广告实体）
- 字符串：`tme_ad_mid_card_ad_vip_view_stub`、`tme_ad_min_card_ad_vip`、`tme_ad_mid_card_vip_ad_reward`（播放页小卡片广告相关）

说明：原配置 `methodNameContains: [PlayPageAd, MiniAd]` 未直接命中；实际实体名为 `PlayerWidgetAd`/`PlayerUseAd`。**建议更新 className 锚点**。

### 2.6 H5 界面拦截（网络繁忙请稍后重试）—— LIKELY CHANGED

锚点：
- `Lcom/kugou/common/web/redfail/H5LoadInfo;`、`H5LoadInfo$Companion`、`Lcom/kugou/common/web/redfail/RedFailDelegate;`、`RedFailReporter.kt`（"红失败"上报体系，疑似"网络繁忙"提示的关联模块）
- `Lcom/kugou/fanxing/allinone/browser/h5/IH5Processor;`、`FAH5Code;`、`FAH5Constant;`、`wrapper/a;`、`wrapper/sys/a;`、`wrapper/tbs/a;`
- `Lcom/kugou/android/app/flexowebview/KGFlexoWebNoLaunchModeActivity;`（Manifest）
- `Lcom/kugou/android/kuqun/IKuqunAdH5Dele;`、`Lcom/kugou/android/lite/config/ShowH5DirectAdEvent;`
- 日志：`WebViewClient onReceivedError failingUrl =`、`WebViewClient X5 onReceivedError failingUrl =`

**未找到**字面串"网络繁忙"或"请稍后重试"（dex 与 resources.arsc 均未命中）；仅有通用 "try again later"。说明文案可能已改为图片/服务端下发，或 H5 拦截逻辑已重构为 redfail 体系。

说明：原配置 `methodNameContains: [H5, WebViewLoad]` + boolean 返回过泛。**建议改用 classNameContains `["redfail", "H5LoadInfo", "IH5Processor"]`**，运行时再确认具体拦截点。

### 2.7 跳过引导页 —— STILL PRESENT（强）

锚点：
- Activity：`com.kugou.android.app.startguide.GuideActivity`（Manifest 与 dex 均有，`Lcom/kugou/android/app/startguide/GuideActivity;`）
- 内部类：`GuideActivity$a..$f`、`Lcom/kugou/android/app/startguide/fragments/{First,Second,Third,Fourth,Fifth}GuideFragment;`、`GuideAbsFragment`、`SwipeCloseViewPager`、`CirclePageIndicator`
- 日志字符串：`skip show guide, activity is null`、`skip show guide, activity is finishing`、`skip show guide, not in main thread`、`skip show guide, current music is null`、`mGuideActivityStarted = `、`GuideActivity: onPageScrolled`
- 辅助：`Lcom/kugou/android/app/businessactivity/NewUserGuideDialog;`、`Lcom/kugou/android/app/home/mine/NewUserGuideDelegate;`

说明：引导页体系完整保留，且日志含 "skip show guide" —— 与原功能完全对应。原配置 `classNameContains: [Guide, SplashGuide, Welcome]` 可命中 `GuideActivity`，建议补 `startguide` 缩小范围。

### 2.8 绕过签名校验 —— 部分变更（LIKELY CHANGED）

Java 层锚点（仍存在）：
- 方法名字符串：`checkSignature`、`checkSignature fail!`、`checkSignature cost:`、`checkSignature$default`、`failed checkSignature :`、`checkSignature parameter is null`（classes.dex 与 classes3.dex）
- `getSignature`、`getSignatureMd5`、`verifySignature`、`signatureVerify`、`getSignatureSHA exception`
- 日志：`ApkSignatureVerify get hostSignature error : `、`ApkSignatureVerify verify plugin signature error : `、`AppUtil.getSignatureFromApk`
- Manifest Provider：`com.kugou.android.lite.updateSdk.fileProvider`（更新 SDK 签名相关）

**重要变化**：参考样本中的 `libkgsignaturehook.so` 在本版本中**不存在**！现有 lib 列表含：`libkgkey.so`(22KB)、`libjnihook.so`、`libshadowhook.so`(67KB)、`libencrypt2.so`、`libFireEye-rqd.so`、`libfireeye-memory.so`、`libtobEmbedEncrypt.so`、`libdexloader.so` 等。

结论：
- Java 层签名校验：`methodNameContains: [checkSignature, verifySign, getSignature]` 仍可命中，STILL PRESENT。
- SO 层签名校验：`{"nativeLibrary": "kgsignaturehook"}` 已失效（库不存在），**必须更新**为 `kgkey` / `jnihook` / `shadowhook` 之一，或直接禁用该项。原配置中 `bypass_signature_so` 已 `enabled: false`，实际不影响，但建议更新文档说明。

### 2.9 关闭青年弹窗 —— STILL PRESENT（强）

锚点：
- 弹窗类：`Lcom/kugou/common/dialog/YoungCommDialog;`（含 `$a`）、`Lcom/kugou/common/dialog8/YoungMainSelectDialog;`、`YoungTitleSelectDialog;`、`Lcom/kugou/android/app/YoungModeTipDialog;`
- 配置/协议：`Lcom/kugou/common/youngmode/YoungModeProtocol;`、`GetYoungModeResponse;`、`Lcom/kugou/common/youngmode/f$a..$e;`
- 日志：`get UI_MSG_SHOW_YOUNGMODE_DIALOG  isToday:`、`getYoungModeInfo throw exception:`、`getYoungModeInfo:start check cloud young mode`
- 字符串：`YoungMode`、`youngmode`、`KugouYouth`、`kugouyouth://youth.kugou.com?action=`
- 导航：`Lcom/kugou/android/app/YoungNavigationUtils;`

说明：原配置 `methodNameContains: [YouthDialog, TeenDialog]` **不会命中**（实际用 Young 而非 Youth/Teen）。**必须更新**为 `YoungCommDialog` / `YoungModeTipDialog` 或 classNameContains `["Young", "youngmode"]`。

### 2.10 拦截 VIP 到期卡片 —— STILL PRESENT

锚点：
- 方法名字符串：`getPaidVipExpireTime`、`paidVipExpireTime`、`paid_vip_expire_time`、`getVipCardInfo`、`setVipCardInfo`、`getVipCardLevel`、`VipCardInfo`
- 字符串常量：`kg_vip_expire_dialog`、`kg_vip_expired_dialog`、`vip_expired_goto_buy`、`vip_expired_tips`、`EXPIRED_VIP`、`VIP_EARNING_USER_CONFIG_EXPIRED_TIME`、`svip_expire_day`
- 实体：`Lcom/kugou/fanxing/allinone/watch/partyroom/entity/PrUserDataEntity$VipCardInfo;`
- 广告 VIP 卡片：`tme_ad_mid_card_ad_vip_view_stub`（播放页）
- VIP 页面：`com.kugou.android.useraccount.vippage.VIPInfoChildActivity`、`Lcom/kugou/android/lite/getvip/*`（大量混淆类 a..z, a0..p0）

说明：原配置 `methodNameContains: [VipExpire, VipCard]` 与实际 `getPaidVipExpireTime`/`getVipCardInfo` 部分吻合（VipCard 命中），可保留并补充 `PaidVipExpire`、`vip_expire`。

### 2.11 移除发现页概念实验室 —— STILL PRESENT（已 jadx 确认）

**最强证据**（jadx `--single-class` 成功反编译 `DiscoveryCardLabProtocol.java`，loaded from classes9.dex）：

```java
public class DiscoveryCardLabProtocol {
    public interface CallInterface {
        @GET
        Call<ResponseBody> getCall(@QueryMap Map<String, String> map);
    }
    public static DiscoveryCardRequest a() { ... 
        // 关键：
        new w.b().n("DiscoveryCard")...o(com.kugou.common.network.y.m(
            CommonConfigKeys.HO, "https://gateway.kugou.com/youth/v1/song/lab_feature_list"))
        ...
        hashMap.put("card_id", String.valueOf(3105));
        ...
    }
    public static CommonResponse<DiscoveryCardEntity> b(String str) ...
}
```

锚点：
- `Lcom/kugou/android/app/home/discovery/protocol/DiscoveryCardLabProtocol;` + `$CallInterface`（classes9.dex）
- API URL：`https://gateway.kugou.com/youth/v1/song/lab_feature_list`、card_id `3105`
- 配置键：`young.discovery.rec_card_lab`、`kg_discovery_rec_lab_item`
- 邻近：`DiscoveryCardProtocol;`、`DiscoveryCardDetailProtocol;`、`DiscoveryCardTagRecProtocol;`、`DiscoveryCardLongAudioRecProtocol;`

说明：概念实验室在 5.3.21 中明确存在（服务端 card_id 3105 + URL lab_feature_list）。原配置 `methodNameContains: [LabCard, ConceptLab]` **未命中**。**强烈建议改用 classNameContains `["DiscoveryCardLabProtocol"]`** 或字符串锚点 `"lab_feature_list"` / `"3105"`。

### 2.12 去除搜索页 Banner 广告 —— STILL PRESENT（强）

锚点：
- 控制器：`Lcom/kugou/android/netmusic/search/rec/controller/SearchBannerController;`（含 `$a..$e`、`$Companion`、`$2`，classes12.dex）
- 适配器：`Lcom/kugou/android/netmusic/search/rec/adapter/SearchBannerAdapter;`
- 视图：`Lcom/kugou/android/netmusic/search/bannerad/BannerAdView;`、`RowBannerItemView`、`RowMVBannerItemView`、`RowAlbumBannerItemView`、`RowSingerBannerItemView`、`RowDefaultBannerItemView`、`BannerItemView`
- 数据/类型：`Lcom/kugou/android/netmusic/search/bean/SearchBannerType;`、`Lcom/kugou/android/netmusic/search/rec/entity/AdBannerEntity;`、`Lcom/kugou/android/netmusic/search/ISearchBannerCount;`
- 文件名字符串：`SearchBannerController.kt`
- 方法：`getBanner`（搜索路径亦有）

说明：原配置 `methodNameContains: [getSearchBanner, showSearchAd]` **未命中**。**建议改用 classNameContains `["SearchBannerController", "SearchBannerAdapter", "BannerAdView"]`**。

---

## 3. 汇总表

| # | 功能 | 判定 | 关键锚点 |
|---|---|---|---|
| 1 | 首页 Banner | STILL PRESENT | FlowBannerView / YuekuBannerAdapter / getBanner |
| 2 | TMEAB 广告 | STILL PRESENT | TMEABManager / TMEABProtocol / TMEAB0ad0type |
| 3 | 启动页广告 | STILL PRESENT | SplashActivity / ForeSplashActivity / SplashLiteGetAdConfigProtocol |
| 4 | 自动更新检测 | STILL PRESENT | KGUpdateInterface / checkUpdate / UpdateAppDialog |
| 5 | 播放页小广告 | STILL PRESENT | PlayerWidgetAd / PlayerUseAd |
| 6 | H5 拦截 | LIKELY CHANGED | H5LoadInfo / RedFailDelegate / IH5Processor（"网络繁忙"文案未找到） |
| 7 | 跳过引导页 | STILL PRESENT | startguide.GuideActivity / "skip show guide" |
| 8 | JAVA 签名校验 | STILL PRESENT | checkSignature / verifySignature（classes.dex,3） |
| 8b | SO 签名校验 | **CHANGED** | libkgsignaturehook.so 已不存在 → kgkey / jnihook / shadowhook |
| 9 | 青年弹窗 | STILL PRESENT | YoungCommDialog / YoungModeTipDialog（原 Youth/Teen 关键词失效） |
| 10 | VIP 到期卡片 | STILL PRESENT | getPaidVipExpireTime / kg_vip_expire_dialog / VipCardInfo |
| 11 | 概念实验室 | STILL PRESENT | DiscoveryCardLabProtocol / lab_feature_list / card_id 3105 |
| 12 | 搜索页 Banner | STILL PRESENT | SearchBannerController / SearchBannerAdapter / BannerAdView |

总评：12 项功能中 **11 项仍明确存在**，1 项（H5 拦截）LIKELY CHANGED；额外发现 1 项 SO 签名库变更。原配置的多个 `methodNameContains` 关键词（Youth/Teen、PlayPageAd/MiniAd、LabCard/ConceptLab、getSearchBanner/showSearchAd、SplashAd/LaunchAd）与实际 5.3.21 命名不匹配，**建议按下方 config 更新**。

---

## 4. 推荐的 UPDATED hook_config.json 片段

保留 `targetPackages: ["com.kugou.android.lite"]` 不变。核心改动：把易失效的 `methodNameContains` 换成稳定的 `classNameContains` / `className` 锚点（dex 中确认存在），并补充字符串锚点供 DexKit 使用。值仍为 `null`/`false`/`void` 依据原功能语义。

```json
{
  "_comment": "酷狗概念版增强模块 hook 特征配置 - 针对 5.3.21 (versionCode 11620 / versionName 5.3.2) 更新；包名未变 com.kugou.android.lite。锚点来自 5.3.21.apk 的 DEX 字符串与 jadx 反编译证据。",
  "targetPackages": ["com.kugou.android.lite"],
  "hooks": [
    {
      "id": "remove_home_banner",
      "type": "ReturnConstantHook",
      "description": "去除首页 Banner 广告",
      "enabled": true,
      "target": {
        "classNameContains": ["FlowBannerView", "YuekuBannerAdapter", "AdBannerBean"],
        "methodName": ["getBanner", "renderBannerView"],
        "stringConstants": ["home_getbannerpics"]
      },
      "value": null
    },
    {
      "id": "remove_search_banner",
      "type": "ReturnConstantHook",
      "description": "去除搜索页 Banner 广告",
      "enabled": true,
      "target": {
        "classNameContains": ["SearchBannerController", "SearchBannerAdapter", "BannerAdView"],
        "stringConstants": ["SearchBannerController.kt"]
      },
      "value": null
    },
    {
      "id": "remove_tmeab_ad",
      "type": "ReturnConstantHook",
      "description": "去除 TMEAB 广告",
      "enabled": true,
      "target": {
        "classNameContains": ["TMEABManager", "TMEABProtocol", "FeeABConfig"],
        "stringConstants": ["TMEAB0ad0type", "TMEAB0sppk0type"],
        "methodNameContains": ["TMEAd", "TMEAB"]
      },
      "value": false
    },
    {
      "id": "remove_playpage_small_ad",
      "type": "ReturnConstantHook",
      "description": "去除播放页左下角小广告",
      "enabled": true,
      "target": {
        "classNameContains": ["PlayerWidgetAd", "PlayerUseAd"],
        "stringConstants": ["tme_ad_min_card_ad_vip", "tme_ad_mid_card_ad_vip_view_stub"]
      },
      "value": null
    },
    {
      "id": "disable_auto_update",
      "type": "ReturnConstantHook",
      "description": "关闭自动更新检测",
      "enabled": true,
      "target": {
        "classNameContains": ["KGUpdateInterface", "UpdateAppDialog"],
        "methodName": ["checkUpdate", "getUpdateInfo", "requestUpdate"]
      },
      "value": null
    },
    {
      "id": "close_youth_dialog",
      "type": "ReturnConstantHook",
      "description": "关闭青年弹窗（5.3.21 使用 Young 命名，非 Youth/Teen）",
      "enabled": true,
      "target": {
        "classNameContains": ["YoungCommDialog", "YoungModeTipDialog", "YoungMainSelectDialog", "YoungModeProtocol"],
        "stringConstants": ["YoungMode", "get UI_MSG_SHOW_YOUNGMODE_DIALOG"]
      },
      "value": null
    },
    {
      "id": "block_vip_expire_card",
      "type": "ReturnConstantHook",
      "description": "拦截 VIP 到期卡片",
      "enabled": true,
      "target": {
        "methodName": ["getPaidVipExpireTime", "getVipCardInfo"],
        "methodNameContains": ["VipExpire", "VipCard"],
        "stringConstants": ["kg_vip_expire_dialog", "EXPIRED_VIP", "vip_expired_tips"]
      },
      "value": null
    },
    {
      "id": "remove_lab_entry",
      "type": "ReturnConstantHook",
      "description": "移除发现页概念实验室（5.3.21: DiscoveryCardLabProtocol, card_id=3105）",
      "enabled": true,
      "target": {
        "classNameContains": ["DiscoveryCardLabProtocol"],
        "stringConstants": ["lab_feature_list", "young.discovery.rec_card_lab", "3105"]
      },
      "value": null
    },
    {
      "id": "skip_guide_activity",
      "type": "ActivityLaunchHook",
      "description": "重写跳过引导页逻辑",
      "enabled": true,
      "target": {
        "classNameContains": ["startguide.GuideActivity", "GuideActivity", "NewUserGuideDialog"],
        "stringConstants": ["skip show guide", "mGuideActivityStarted"]
      }
    },
    {
      "id": "skip_splash_ad",
      "type": "MethodAfterHook",
      "description": "重写去除启动页广告逻辑",
      "enabled": true,
      "target": {
        "classNameContains": ["SplashActivity", "ForeSplashActivity", "AdContainerActivity", "SplashLiteGetAdConfigProtocol", "SplashAdPrefs"]
      }
    },
    {
      "id": "h5_intercept",
      "type": "MethodReplaceHook",
      "description": "重写 H5 界面拦截（原'网络繁忙请稍后重试'文案在 5.3.21 未找到，疑似 redfail 体系替代）",
      "enabled": true,
      "target": {
        "classNameContains": ["H5LoadInfo", "RedFailDelegate", "IH5Processor", "RedFailReporter"],
        "stringConstants": ["WebViewClient onReceivedError", "redfail"]
      }
    },
    {
      "id": "bypass_signature_java",
      "type": "FieldFlagHook",
      "description": "绕过 JAVA 层签名校验",
      "enabled": true,
      "target": {
        "methodName": ["checkSignature", "getSignature", "verifySignature", "getSignatureMd5"],
        "methodNameContains": ["checkSignature", "verifySign"]
      }
    },
    {
      "id": "bypass_signature_so",
      "type": "NativeHook",
      "description": "SO 层签名校验：5.3.21 已无 libkgsignaturehook.so；候选替换为 kgkey/jnihook/shadowhook（需运行时确认；默认关闭）",
      "enabled": false,
      "target": { "nativeLibrary": "kgkey" }
    }
  ]
}
```

**字段说明**：`classNameContains`、`stringConstants` 为本报告建议新增的匹配维度（需 HookConfig/DexKitHelper 实现相应匹配逻辑）；若当前代码仅支持既有字段（`methodName`/`methodNameContains`/`returnType`/`classNameContains`），可把上述 `stringConstants` 中的值并入 `classNameContains` 或新增的 `stringContains` 字段，核心是**用 5.3.21 实测存在的类名/字符串替换旧的不匹配关键词**。

---

## 5. 证据附录（可复核的命令）

```bash
# 包名/版本
/root/android-sdk/build-tools/34.0.0/aapt dump badging 5.3.21.apk | head -2
# 建立字符串索引
cd /mnt/c/Users/Administrator/Documents/dsh/kugou/_apk521
for d in classes*.dex; do strings -a -n 4 "$d" > /mnt/c/Users/Administrator/Documents/dsh/kugou/_v521scan/$d.str; done
# 关键查询示例
grep -h 'DiscoveryCardLabProtocol' _v521scan/*.str
grep -h -E 'checkSignature|verifySignature' _v521scan/*.str
grep -h -E 'SplashActivity|SplashLiteGetAdConfigProtocol' _v521scan/*.str
grep -h -E 'YoungCommDialog|YoungModeTipDialog' _v521scan/*.str
# jadx 定点反编译（唯一成功的具体类）
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /mnt/c/Users/Administrator/Documents/dsh/kugou/_tools/bin/jadx \
  -d _v521scan/_decomp521 --no-res -j 2 \
  --single-class 'com.kugou.android.app.home.discovery.protocol.DiscoveryCardLabProtocol' 5.3.21.apk
```
