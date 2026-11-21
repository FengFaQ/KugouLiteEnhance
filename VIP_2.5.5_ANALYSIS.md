# 酷狗概念版 (Kugou Concept) 2.5.5 — 自动领取 VIP 逆向分析报告

> 目标：定位“自动领取VIP / 免费领VIP”的真实实现，给出可用于 Xposed + DexKit 的精确类名、方法签名、Hook 点与稳定锚点，并评估服务端校验限制。
> 结论先行：**2.5.5 的“领VIP”是「看激励视频广告 → 客户端上报广告完成 → 服务端发放 VIP 时长」的服务端校验机制。客户端 Hook 只能伪造 UI/回调/解析结果，无法让服务器真正发放 VIP。** 详见第 6 节。

---

## 0. 结论摘要（先读这里）

| 项目 | 结论 |
|---|---|
| 领取机制类型 | (c) + (b) 混合：UI 入口触发 `AdPlayReportProtocol.report()` 网络请求；服务端根据上报的 `play_start/play_end` 决定是否发放 VIP 时长 |
| 真实包 | `com.kugou.android.lite.getvip`（Kotlin 编写，编译后**运行时类名被混淆**为 `com.kugou.android.lite.a.*`） |
| 关键类（源码名 → 运行时名） | `GdtSeeVideoManager → com.kugou.android.lite.a.e`；`AdPlayReportProtocol → com.kugou.android.lite.a.a`；`AdPlayStatusProtocol → com.kugou.android.lite.a.c`；`GetVipConfigProtocol → com.kugou.android.lite.a.f`；`IGetVipListener → com.kugou.android.lite.a.i`；`ISeeVideoResultCallback → com.kugou.android.lite.a.j`；`AdPlayResult → com.kugou.android.lite.a.b`；`IGetService → com.kugou.android.lite.a.g`；`ShowCondition → com.kugou.android.lite.a.k`；`VIPActivityModeManager → com.kugou.android.lite.a.l`；策略类 `GetVipFreePolicy → com.kugou.android.lite.a.a.b`、`GetVipByVideoPolicy → com.kugou.android.lite.a.a.a` |
| 发奖请求 | `POST https://gateway.kugou.com/youth/v1/ad/play_report`，body `{"ad_id":..., "play_start":..., "play_end":...}` |
| 状态请求 | `GET https://gateway.kugou.com/youth/v1/ad/play_status` → `data.{total,done,remain,get_vip_hour}`；配置 `GET https://gateway.kugou.com/youth/api/user/v1/init` |
| 触发链 | GDT 激励视频 `onReward()` → `IGetVipListener.onGetReward(adId, start, end)` → `AdPlayReportProtocol.report(...)` → 服务端返回 `award_vip_hour` → UI 刷新 |
| 最佳 Hook 点 | ① `com.kugou.android.lite.a.a.a(String,long,long)`（report 发奖请求）之前/替换；② `com.kugou.android.lite.a.i`（IGetVipListener）的 `onGetReward` 实现；③ `GdtSeeVideoManager` 内部类 `c.onReward()/onADClose()` 强制奖励标志；④ `AdPlayReportProtocol.parseBody(ab)` 伪造成功响应 |
| 是否服务端校验 | **是**。`award_vip_hour / remain_vip_hour` 完全来自 gateway 响应，客户端无本地写 VIP 的逻辑；hook 只能骗 UI 或触发请求，**骗不过服务器**。 |
| 混淆情况 | Kotlin `@Metadata d2` 保留了原始类名/方法名/参数名，jadx 显示源码名但**运行时 DEX 类名是 `a.*`**；字符串常量（URL、日志 tag、GDT 广告位 ID）是明文，可作 DexKit 锚点 |

---

## 1. 调查范围与方法

- DEX：`_apk255/classes.dex` … `classes8.dex`（9 个）。
- 反编译：jadx v1.5.0（`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 .../_tools/bin/jadx -d _vip_dec/_c3 --no-res -j 4 ../_apk255/classes3.dex`），输出在 `_vip_dec/_c3/sources/`。
- DEX 字符串扫描：`strings -a` 对中文无效；用自写 MUTF-8 解析器 `/tmp/dexscan.py` 和常量引用映射器 `/tmp/dexrefs.py`（均基于 DEX header 偏移 0x38/0x3c 的 string_ids）。
- 二进制 AndroidManifest 字符串池解析：无独立 Claim/SignIn Activity；只有 `com.kugou.android.useraccount.VIPInfoFragment`、`vippage.VIPInfoChildActivity`、`framework.musicfees.ui.VipInfoIntroActivity` 等会员展示界面。说明领取逻辑在库内。
- 参考模块 `啄木鸟_1.0.5.apk`（`_decompiled/sources/`、`ANALYSIS.md`）是**另一个 Xposed 模块**，其 `H`（signinManager）和 XOR 混淆字符串与本任务目标 App 无关，不能作为 API 来源。

---

## 2. 确认的领取流程（逐环节，附精确符号）

### 2.1 配置拉取（决定入口是否展示）
- 类：`GetVipConfigProtocol`（运行时 `com.kugou.android.lite.a.f`），单例 `public static final GetVipConfigProtocol a`。
- 方法 `public final rx.e<com.kugou.common.entity.e<java.lang.Integer>> a()`（源码名 `getConfig()`）：构造 Retrofit 风格请求，`GET https://gateway.kugou.com/youth/api/user/v1/init`，通过 `IGetService.get2(map)`（运行时 `a.g.a(Map)`）发起。
- 私有解析 `a(d.ab responseBody)`：解析 `status/error_code/error_msg` 与 `data.ad_play_model.{user,song}`；成功后写入 `VIPActivityModeManager`（运行时 `a.l`）与 `ShowCondition`（`a.k`），再序列化为 JSON 存入 `com.kugou.framework.setting.a.o.a().c(json)`（YoungSettingPrefs）。
- 日志 tag：`"lzq-young"`（多处出现，稳定锚点）。

### 2.2 广告播放状态
- 类：`AdPlayStatusProtocol`（运行时 `a.c`），单例。
- 方法 `public final rx.e<com.kugou.common.entity.e<AdPlayResult>> a()`（源码名 `get`）：`GET https://gateway.kugou.com/youth/v1/ad/play_status`。
- 私有解析 `a(d.ab)`：`data.{total, done, remain, get_vip_hour}` → `AdPlayResult`（运行时 `a.b`，5 个 int 字段，setter `a(int)…e(int)`，getter `getA()…getE()`；Kotlin 元数据名 `total/done/remain/getVipHour/remainVipHour`）。

### 2.3 激励视频播放（GDT / 腾讯广告）
- 类：`GdtSeeVideoManager`（运行时 `com.kugou.android.lite.a.e`），单例通过 `Companion.a().a()` 获取（字段 `a = new a(null)`；`a.a()` 返回实例）。
- 常量：`APP_ID="1112108869"`，`POS_ID="6033900901778954"`，`GAIN_REWARD_TIME=15`（秒，调用 `setGainRewardTime(15)`），`TAG="GdtSeeVideoManager"`。
- 方法：
  - `public final void a(@NotNull DelegateFragment, @Nullable ISeeVideoResultCallback)`（源码 init）：保存 `fragment/callback`，构造 `LoadAdParams`（`h()`）并加载 `TangramRewardAD`。
  - `public final void a(@Nullable IGetVipListener)`（源码 openAd，运行时重载签名 `(La/i;)V`）：设置 `adListener` 并 `loadAD()`。
  - `public final void a(String)`：设置广告 id（`id`）；`b(String)`、`a(long)`、`a(boolean)`、`b(boolean)` 等为内部状态 setter。
- 监听内部类 `com.kugou.android.lite.a.e$c`（源码 `GdtSeeVideoManager$c implements TangramRewardADListener`）：
  - `onADLoad()` → `adLoaded=true`
  - `onADPlay(data)` → 取 `getAdId()` 记入 `id`，上报埋点 20669
  - `onADCached()` → `adCached=true` 并 `showAD(id)`
  - `onADShow()` → 记录 `playStart = System.currentTimeMillis()`
  - **`onReward()` → 设置奖励标志 `this.b.a = true`，并 `Toast.makeText(ctx, "激励领取成功~", Toast.LENGTH_SHORT).show()`**
  - `onADClose()` → 若奖励标志为 true，调用 `IGetVipListener.onGetReward(id, playStart, System.currentTimeMillis())`（即 `m2.a(getO(), getG(), now)`）；否则调用 `m.a()`（onAdClose）。随后上报埋点 20646。
  - 稳定字符串锚点：`"激励领取成功~"`、`"GdtSeeVideoManager"`、`"1112108869"`、`"6033900901778954"`、埋点 id `20646/20669/20681`。

### 2.4 发奖请求（核心）
- 类：`AdPlayReportProtocol`（运行时 `com.kugou.android.lite.a.a`），单例 `a`。
- 方法：`public final rx.e<com.kugou.common.entity.e<AdPlayResult>> a(@NotNull String adName, long playStart, long playEnd)`（Kotlin 元数据源码名 `report(adName, playStart, playEnd)`）。
  - 构造 `IGetService`（运行时 `a.g`），`POST https://gateway.kugou.com/youth/v1/ad/play_report`。
  - body：`JSONObject{"ad_id":adName, "play_start":playStart, "play_end":playEnd}`，`RequestBody.create(MediaType "application/json", body)`，经 `IGetService.post2(map, body)`（运行时 `a.g.a(Map, d.z)`）发出，再 `.map(parseBody)`。
- 私有解析 `a(d.ab responseBody)`（Kotlin 元数据名 `parseBody`）：`JSONObject` 读 `status/error_code/error_msg`；成功时读 `data.{total, done, remain, award_vip_hour, remain_vip_hour}` 填入 `AdPlayResult`。
  - **VIP 时长完全来自服务端返回的 `award_vip_hour/remain_vip_hour`，客户端没有任何“本地加 VIP”的方法。**
- 日志 tag：`"lzq-young"`。

### 2.5 回调接口（运行时混淆名 + 元数据原名）
- `IGetVipListener`（运行时 `a.i`）：
  - `void a()` → `onAdClose`
  - `void a(@NotNull String adId, long playStart, long playEnd)` → `onGetReward`
- `ISeeVideoResultCallback`（运行时 `a.j`）9 个方法（元数据 d2 原名）：
  - `void a(String errorCode)`=`onAPMGDTFailed`，`void a(Throwable)`=`onAPMNetFailed`，`void a()`=`onAPMStart`/`onFail`/`onNetFinish`/`onShowRedirectDialog`/`onSucceed` 等多个无参重载（按返回类型与签名区分），`void a(AdPlayResult)=onGetReward`，`boolean b()=onAPMSucceed`，`void c()/d()/e()/f()`。
  - 接口方法在 DEX 里都被混淆为 `a/b/c/d/e/f`，**签名重载 + Kotlin 元数据 d2 是区分依据**。

### 2.6 策略类（入口包装， clazz3）
- `GetVipFreePolicy`（运行时 `a.a.b`）、`GetVipByVideoPolicy`（运行时 `a.a.a`），均实现 `IGetVipPolicy`（运行时 `a.a.c`）。
- 两者逻辑一致：把 `init(DelegateFragment, ISeeVideoResultCallback)` 与 `openAd(IGetVipListener)` 委托给 `GdtSeeVideoManager.Companion.a().a(fragment, callback)` / `.a(listener)`。

### 2.7 UI 视图层（classes.dex / classes2.dex，尚未完全反编译）
- 发现相关字符串/方法名（来自 DEX 字符串池，非完整反编译）：
  - `getIsReceiveVIP`, `getReceiveVIPMsg`, `setIsReceiveVIP`, `setReceiveVIPMsg`, `refreshGetVipLayout`, `getVipStatus`, `tvGetVip`
  - 相关类（源码名线索）：`GetVipSuccessDialog`, `LoginGetVipDialog`, `ClickGetVipView`, `PPlayerGetVipView`, `SPlayerGetVipView`, `FreeVipModeDelegate`, `GetVipBySeeVideoDelegate`, `IGetVipViewDelegate`, `GetVipByVideoDialog`
  - URL：`https://activity.kugou.com/getvips/v-e9592572/index.html`
- 这一层持有“是否已领取 / 领取文案”状态，是 UI 状态 hook 的候选，但真正的发奖网络调用在 2.4。

---

## 3. 机制定性（对应用户问题 2）

是 **(c) UI 入口调用领取 API + (b) 网络响应解析伪造** 的组合：
1. 展示入口（`GetVipConfigProtocol` + `VIPActivityModeManager` 决定展示条件）；
2. 用户点击 → 策略类 → GDT 激励广告播放；
3. 广告 `onReward` → `IGetVipListener.onGetReward(adId, start, end)`；
4. `AdPlayReportProtocol.report(...)` 向 gateway 上报；
5. 服务端校验上报（广告真实性、时长、`gain_reward_time=15s`、频次等）后返回 `award_vip_hour`，客户端再刷新 VIP UI。
- **不是**简单“检查本地 flag 强制返回已领取”——虽然 UI 有 `getIsReceiveVIP/setIsReceiveVIP` 状态，但那只是展示层缓存，VIP 权益由服务端账号数据决定。
- **不是**每日签到（classes5 的 `com.kugou.fanxing.signin.view.NewSignInFragment` 是粉丝星签到，与本 VIP 无关）。

---

## 4. 推荐 Xposed Hook 点（按“可实现目标”排序）

> 说明：以下类名均为**运行时 DEX 名**（已通过 `strings -a classes3.dex | grep` 验证每个类存在且唯一）。jadx 源码里的 `com.kugou.android.lite.getvip.*` 是 Kotlin 元数据还原名，运行时不存在，请勿直接用它 hook。

### Hook A（最直接触发发奖请求）：`com.kugou.android.lite.a.a` 的 report
- 方法签名（DEX）：`public final rx.e a(java.lang.String, long, long)`（返回 `rx.e<com.kugou.common.entity.e<com.kugou.android.lite.a.b>>`）
- 目标：在 `before` 中将 `playStart` 改为 `System.currentTimeMillis() - 16000`、`playEnd` 改为 `System.currentTimeMillis()`，使时长满足 `GAIN_REWARD_TIME=15s` 校验；或在 `after` 拦截返回值替换成伪造的 `Observable.just(CommonResponse(status=1, data=AdPlayResult(award_vip_hour=N)))`。
- 局限：服务端仍可校验 `ad_id` 真实性、重复上报、账号当日上限；伪造响应只能骗 UI。
- DexKit 锚点：`className = "com.kugou.android.lite.a.a"` + `methodName = "a"` + 参数 `String,long,long` + `const-string` 含 `"https://gateway.kugou.com/youth/v1/ad/play_report"`。

### Hook B（模拟广告完成回调）：`com.kugou.android.lite.a.e$c.onReward` / `onADClose`
- 类：`com.kugou.android.lite.a.e$c`（GDT 监听器），实现 `com.qq.e.tg.rewardAD.TangramRewardADListener`。
- `onReward()`：强制通过反射/字段写把奖励标志 `b.a`（boolean，jadx 中 `this.b.a`）设为 `true`；无需真实看完广告。
- `onADClose()`：当标志为真时，正常会调用 `IGetVipListener.a(adId, start, end)`（Hook 点 A 的触发者）。可在 `onADClose` 里 hook `IGetVipListener`（运行时 `a.i`）实现类的 `a(String,long,long)` 来注入任意 adId。
- DexKit 锚点：`classNameContains "com.kugou.android.lite.a.e$"` + `const-string` `"激励领取成功~"`（Toast）；或 `methodName "onADClose"` + `classNameContains "TangramRewardADListener"`（若按接口查）。

### Hook C（伪造“已领取”UI 状态）：视图层
- 目标方法（存在 classes.dex/classes2.dex，需补充反编译确认运行时类名）：`setIsReceiveVIP(boolean)`、`setReceiveVIPMsg(String)`、`refreshGetVipLayout()`、`getIsReceiveVIP()`。
- 效果：让界面显示“已领取/领取成功”。**纯视觉，无实际权益**。
- DexKit 锚点：`methodName "setIsReceiveVIP"` 或 `methodName "refreshGetVipLayout"` + `const-string "https://activity.kugou.com/getvips/v-e9592572/index.html"` / `"领取"`（中文 MUTF-8）。

### Hook D（伪造网络解析结果）：`AdPlayReportProtocol.parseBody`
- 私有方法（运行时 `a.a` 内）：`private final rx.e a(d.ab)`（Kotlin 名 `parseBody`）。
- Hook `after`，构造 `CommonResponse` 并 `a(true)`（status）、`a(AdPlayResult)`（data），把 `award_vip_hour` 设为期望值。
- 这能让上层以为发奖成功并刷新 UI，但**不产生服务端权益**。

### Hook E（绕过展示条件）：`GetVipConfigProtocol` / `VIPActivityModeManager`
- `com.kugou.android.lite.a.f.a()` 的解析里会根据 `data.ad_play_model.{user,song}` 调用 `VIPActivityModeManager.a(int)/b(int)/c(int)`；hook 这些可让入口在任何账号条件下显示，配合 B/C 实现“自动弹领取”。DexKit 锚点：`const-string "https://gateway.kugou.com/youth/api/user/v1/init"` 或 `"lzq-young"` + `"ad_play_model"`。

---

## 5. DexKit 稳定锚点汇总（可直接用于运行时查找）

| 目标 | 推荐锚点（AND 组合） |
|---|---|
| 发奖协议类 | `className == "com.kugou.android.lite.a.a"`；`methodName == "a"`；`paramTypes == ["java.lang.String","long","long"]`；`const-string` 包含 `"play_report"` 或 `"https://gateway.kugou.com/youth/v1/ad/play_report"` |
| 广告管理单例 | `className == "com.kugou.android.lite.a.e"`；`const-string` 含 `"GdtSeeVideoManager"` / `"1112108869"` / `"6033900901778954"` / `"激励领取成功~"` |
| GDT 监听内部类 | `className startsWith "com.kugou.android.lite.a.e$"`；`methodName == "onADClose"`；`const-string "激励领取成功~"` |
| 接口 IGetVipListener | `className == "com.kugou.android.lite.a.i"`；方法 `a()` 与 `a(String,long,long)` |
| 状态/配置 | `className == "com.kugou.android.lite.a.c"` + `const-string "play_status"`；`className == "com.kugou.android.lite.a.f"` + `const-string "/youth/api/user/v1/init"` |
| 通用日志 tag | `const-string "lzq-young"`（在 a.a / a.c / a.f 的解析方法中都出现，用于区分同包混淆类） |
| GDT 广告位 | `const-string "6033900901778954"` / `"1112108869"`（唯一广告位/APPID，强锚点） |
| 中文领取文案 | `const-string "领取"` / `"等待领取"` / `"激励领取成功~"`；注意 DEX 里中文是 MUTF-8，DexKit 用普通字符串 API 即可 |

> 注意：Kotlin 元数据 `@Metadata(d2=...)` 保留了源码类名/方法名/参数名，但**运行时反射/DexKit 按 DEX 名查**。上面所有 `com.kugou.android.lite.a.*` 名称已在 classes3.dex 中逐个验证存在。

---

## 6. 服务端校验与可行性评估（诚实结论）

1. **VIP 权益由服务端账号系统决定**：`award_vip_hour / remain_vip_hour` 只能来自 `gateway.kugou.com/youth/v1/ad/play_report` 的响应。客户端没有任何把 VIP 时长写入账号的方法。Hook 该响应解析只能让 App UI 显示“领取成功”，无法改变服务器上的会员状态。
2. **上报参数可被服务端校验**：`ad_id`（GDT 广告 id）、`play_start/play_end`（需 ≥ 15s 的 `GAIN_REWARD_TIME`）、`status/error_code`、账号每日领取上限、广告真实性（GDT 服务端回调）都可能被校验。伪造 `play_start/play_end` 或 `ad_id` 可能触发风控。
3. **能做到的**：
   - 让领取入口无条件展示（Hook E）；
   - 自动触发广告完成回调、自动调用 report（Hook B/A），省去人工看广告；
   - 伪造客户端 UI“已领取”与响应解析（Hook C/D），用于测试。
4. **做不到的**：在不调用真实 GDT 广告并完成、且不通过服务器校验的情况下，凭空让账号获得 VIP 权益。若目标是“真正领到 VIP”，必须让真实的 `play_report`（带合法 ad_id、合法时长）到达服务器；客户端 hook 最多加速/自动化这一流程，不能替代服务器发奖。
5. 结论：**作为“自动看广告领VIP”的辅助自动化是可行的（Hook B→A 串起流程）；作为“无广告无服务端验证直接发VIP”是不可行的。** 请据此设定模块功能和用户预期。

---

## 7. 待补充（低优先级，非阻塞）
- classes.dex / classes2.dex 的完整反编译，确认 `setIsReceiveVIP/refreshGetVipLayout` 等 UI 类的运行时 DEX 名与签名（Hook C 精确化）。
- 查找实现 `IGetVipListener`（`a.i`）与 `ISeeVideoResultCallback`（`a.j`）的具体业务类（当前接口实现查找脚本因 DEX 解析偏移问题未产出，可用 jadx 的 `--single-class` 对 classes2/classes3 逐个查 implements）。
- classes5 `com.kugou.fanxing.signin.view.NewSignInFragment` 与此 VIP 无关，已排除。

---

## 8. 证据索引
- 反编译源码：`_vip_dec/_c3/sources/com/kugou/android/lite/getvip/`（`GdtSeeVideoManager.java`, `AdPlayReportProtocol.java`, `AdPlayStatusProtocol.java`, `GetVipConfigProtocol.java`, `IGetVipListener.java`, `ISeeVideoResultCallback.java`, `IGetService.java`, `AdPlayResult.java`, `ShowCondition.java`, `policy/*`, `VIPActivityModeManager.java`）
- 关键字符串（classes3.dex 明文）：`"https://gateway.kugou.com/youth/v1/ad/play_report"`, `"https://gateway.kugou.com/youth/v1/ad/play_status"`, `"https://gateway.kugou.com/youth/api/user/v1/init"`, `"lzq-young"`, `"激励领取成功~"`, `"6033900901778954"`, `"1112108869"`, `"award_vip_hour"`, `"remain_vip_hour"`, `"get_vip_hour"`, `"ad_play_model"`。

---

## 9. 5.3.21 差异（追加，2026-11-21）

> 对 `_apk521/classes3.dex` 做了 jadx 反编译（`_vip_dec/_c521/sources/`）并逐项核对 DEX 字符串池。**2.5.5 的 `com.kugou.android.lite.a.a/.e/.i` 短混淆名在 5.3.21 中已不存在**；新版包名仍是 `com.kugou.android.lite.getvip`，但类名/方法签名/接口方法都变了。

### 9.1 5.3.21 实际运行时类名与签名（均已确认存在于 DEX，非推测）
- **发奖协议类**：`com.kugou.android.lite.getvip.k`（2.5.5 的 `a.a` / AdPlayReportProtocol 对应物），`public static final k INSTANCE`。
  - **report 方法（核心，签名已变）**：`public final rx.Observable<com.kugou.common.entity.a<com.kugou.android.lite.getvip.l>> c(@NotNull String adName, long playStart, long playEnd, boolean clicked)` — 由 3 参数 `(String,long,long)` 变为 **4 参数 `(String,long,long,boolean)`**，新增 `clicked`（是否点击广告）；方法名从 `a` 变为 `c`；返回 `Observable` 而非 `rx.e`。
  - 调用点：`com.kugou.android.lite.getvip.VIPActivityModeManager`（同名类，运行时名未变）中 `k.INSTANCE.c(adId, playStart, playEnd, hasClick)...`（约第 780 行）。
  - 私有 parseBody：`k.b(okhttp3.ResponseBody)`，`award_vip_hour` 仍在此解析。
- **URL 形式（关键差异）**：`https://gateway.kugou.com/youth/v2/ad/play_report`（**v1 → v2**）。代码写法：`com.kugou.common.network.y.m(KGConfigKeys.di, "https://gateway.kugou.com/youth/v2/ad/play_report")`，其中 `KGConfigKeys.di = new ConfigKey("young.ad.dialog_play_report_v2")`。即**整串 URL 作为常量存在于 DEX 中**，但会先经过配置中心合并（远端可覆盖默认值）；不是运行时手工拼接。请求 query 增加 `is_gradient=1`，body 增加 `has_download`（clicked?1:0），其余 `ad_id/play_start/play_end` 不变。
- **IGetVipListener**：运行时名 **未混淆**，就是 `com.kugou.android.lite.getvip.IGetVipListener`（DEX 中出现 4 次）。接口方法（Kotlin 元数据原名 → 运行时名）：
  - `onGetReward(String adId, long playStart, long playEnd, boolean hasClick, boolean openFreeMode, String biReportParam)` → **`void a(String,long,long,boolean,boolean,String)`**
  - `onGetRewardWithCallback(String,long,long,boolean,boolean,boolean,String)` → `void b(...)`
  - `onAdClose()` → **`void onAdClose()`**（未混淆）
- **广告管理器**：2.5.5 的 `GdtSeeVideoManager`（运行时 `a.e`）在新版分裂/迁移：
  - `com.kugou.android.app.ads.GdtAdsVideoManager`（未混淆，含 `APP_ID = "1112108869"`、`Toast "激励领取成功~"`）；
  - `com.kugou.android.lite.getvip.t`（单字母方法管理器，持有 callback / TYPE / bind 等状态，可能是原 `a.e` 的直接替代；精确对应关系建议实机 DexKit 核对）。
- **结果实体类**：`com.kugou.android.lite.getvip.l`（2.5.5 的 `a.b` / AdPlayResult 对应物）。

### 9.2 5.3.21 仍存在的稳定字符串锚点（已核对 classes3.dex）
`6033900901778954`、`1112108869`、`GdtSeeVideoManager`、`lzq-young`、`ad_play_model`、`award_vip_hour`、`https://gateway.kugou.com/youth/v1/ad/free_mode_info`、`https://gateway.kugou.com/youth/v1/ad/set_free_mode`、`https://gateway.kugou.com/youth/v2/ad/play_report`、`young.ad.dialog_play_report_v2`、`激励领取成功~`（在 GdtAdsVideoManager 中，jadx 源码与 DEX 字节均确认存在）。
- **DexKit 锚点（5.3.21）**：`className == "com.kugou.android.lite.getvip.k"` + `methodName == "c"` + 参数 `(String,long,long,boolean)`；或用 `const-string` `"youth/v2/ad/play_report"` / `"young.ad.dialog_play_report_v2"` 定位。接口用 `className == "com.kugou.android.lite.getvip.IGetVipListener"` + `methodName == "a"` + 6 参数签名。
- 注意：新版方法体开头有 `SwordSwitches/SwordProxy.proxy4Arg(...)` 的厂商加固代理分支（开关关闭时走原始逻辑），hook 时应放在 `before` 且在代理分支之前/之后按实机验证。

### 9.3 服务端校验结论（5.3.21 不变）
`award_vip_hour / remain_vip_hour` 依旧来自 `play_report` 的服务端响应；新增 `clicked/has_download/is_gradient` 与 v2 接口、远端配置键，反而说明风控更严。客户端 hook 依旧只能自动化上报/伪造 UI，**无法让服务器凭空发放 VIP**。

> 精确类名已通过 jadx 反编译 + DEX 字符串验证（k、IGetVipListener、GdtAdsVideoManager 均确认存在）。`com.kugou.android.lite.getvip.t` 与旧 `a.e` 的精确对应关系未做运行时验证，如需可实机 DexKit 核对。
