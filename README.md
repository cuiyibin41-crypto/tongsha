# 广告联盟通杀 XP 模块 —— 使用与开发说明

> 目标：让这类「看广告叠红包」的 uni-app 套壳游戏，广告秒完、自动叠包
> 原理：Hook 广告 SDK 回调 + uni-app JS 桥，让 App **自己**走真实上报链路

---

## 一、为什么能通杀（核心认知）

这类 App（碎银屋/墨染流年/吉人天相/浩瀛万象…）结构高度雷同：

```
H5/uni-app 前端(assets/web/*.js)  ←→  原生桥(window.android)  ←→  广告SDK
```

**看广告的完整链路：**
```
1. JS 调 window.android.showad() → 原生拉起广告SDK
2. 广告SDK播放 → 用户看完 → SDK回调 App
3. App 收到回调 → closeRewardAd() → callJs("close_ad", json)
4. JS 收到 close_ad → uni.pro.get_reward1() → HTTP 上报(带签名)
5. 服务端加币
```

**关键**：第 4 步的签名是 **App 自己算的**（用 App 内置的密钥/cpkey）。
所以只要我们 Hook 第 2~3 步让「广告完成」自动发生，**上报就是真实合法的**，不需要逆向签名！

---

## 二、三条 Hook 策略

### 策略 A：加速播放（最安全，推荐先用）
Hook 广告播放器，把 30 秒倒计时改成 3 秒。曝光链路完整，最像真人。

- 穿山甲：`TTFullScreenVideoAd` 内部播放器 / `onProgressUpdate`
- 优量汇：`RewardVideoAD` 内部 `VideoPlayer`
- 通用：Hook `CountDownTimer` / `Handler.postDelayed`，把延时改小

### 策略 B：Hook 完成回调（见效快）
直接 Hook 各 SDK 的 `onVideoComplete` / `onRewardVerify`，让它提前触发。

各家回调方法名（已从墨染流年 APK 挖出）：

| 联盟 | 接口类 | 关键方法 |
|---|---|---|
| 穿山甲 pangle | `TTRewardVideoAd$RewardAdInteractionListener` | `onVideoComplete` `onRewardVerify(ZILjava/lang/String;ILjava/lang/String;)` `onRewardArrived` |
| 优量汇 GDT | `com.qq.e.ads.rewardvideo.RewardVideoADListener` | `onVideoComplete` `onReward(Ljava/util/Map;)` |
| 快手 KS | `KsRewardVideoAd$RewardAdInteractionListener` | `onVideoPlayEnd` `onRewardVerify` |
| 百度 | `RewardVideoAd$RewardVideoAdListener` | `playCompletion` `onRewardVerify(ZLjava/util/Map;)` |
| sigmob | `WindRewardAdListener` | `onRewardVerify` |

### 策略 C：Hook uni-app JS 桥（最通杀）
Hook `callJs(String, Object)`，拦截/伪造 `close_ad` 事件。

墨染流年的桥：
```
com.dtyx.qckj.utils.AdLoadUtils.callJs(activity, jsFunc, data)
  → com.dtyx.qckj.BaseWebActivity.callJs(String, Object)
```
通用层：`com.common.BaseAppActivity.callJs(String[, Object])`

**终极通杀点**：`WebView.evaluateJavascript(String, ValueCallback)`
所有 uni-app 的 JS 注入都过这里，拦截它能看到 `close_ad`/`onReward` 等事件。

---

## 三、编译步骤

1. 用 Android Studio 新建项目，包名 `com.adkiller`
2. 把 `AdKillerHook.java` 放到 `app/src/main/java/com/adkiller/`
3. `AndroidManifest.xml` 加入 xposed 三个 meta-data
4. `app/src/main/assets/xposed_init` 内容：`com.adkiller.AdKillerHook`
5. build.gradle 加依赖：
```gradle
compileOnly 'de.robv.android.xposed:api:82'
```
6. 打包 APK → LSPosed 里启用 → 勾选目标App → 重启目标App
7. 看 LSPosed 日志（tag: `AdKiller`）确认 Hook 命中

---

## 四、开发路线（分阶段）

- [x] 阶段1：Hook 各 SDK 回调，打日志确认命中（当前代码）
- [ ] 阶段2：在命中点**主动调用** onVideoComplete（提前完成）
- [ ] 阶段3：Hook callJs，拦截 close_ad 并自动注入下一轮
- [ ] 阶段4：配合无障碍/Tasker 自动点击「看广告」按钮，形成闭环
- [ ] 阶段5：多账号/多设备调度

---

## 五、风险与注意

1. **联盟反作弊**：广告SDK会上报真实播放时长/曝光。纯跳过易被联盟扣量/封号。
   建议用「加速」而非「跳过」（曝光完整，只缩时长）。
2. **服务端风控**：eCPM 异常、频率过快 → 封号。保持 eCPM 合理（几十~几百）。
3. **仅测试**：请勿用于真实牟利，账号封禁风险自担。
