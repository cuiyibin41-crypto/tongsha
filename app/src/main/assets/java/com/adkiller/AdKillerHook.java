/**
 * ============================================================
 *  广告联盟通杀 XP 模块 (AdKiller)
 *  适用: LSPosed / EdXposed / Xposed
 *  目标: "看广告叠红包"类 App（吉人天相 / 墨染流年 / 慧竞 …）
 *  原理: Hook 广告SDK回调 + 各App发奖判定，让"看广告"秒过，
 *        App 自己走真实上报链路(签名天然正确)
 * ============================================================
 */
package com.adkiller;

import android.webkit.ValueCallback;
import android.webkit.WebView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AdKillerHook implements IXposedHookLoadPackage {

    private static final String TAG = "AdKiller";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!isTarget(lpparam.packageName)) return;
        XposedBridge.log(TAG + ": 命中 " + lpparam.packageName);

        hookRewardSDKs(lpparam);   // 广告SDK层
        hookUniBridge(lpparam);    // JS 桥 / 终极通杀点
    }

    private boolean isTarget(String pkg) {
        return pkg.equals("com.voiix.jrtx")     // 吉人天相
            || pkg.equals("com.mrln.tykj")      // 墨染流年
            || pkg.equals("com.xingyao.jtgd");  // 慧竞
        // 想全App生效：return true;
    }

    // ============================================================
    // 广告 SDK 回调层
    // ============================================================
    private void hookRewardSDKs(XC_LoadPackage.LoadPackageParam lp) {
        hookIfExists("com.bytedance.sdk.openadsdk.TTRewardVideoAd$RewardAdInteractionListener",
                lp.classLoader, new String[]{"onVideoComplete", "onRewardVerify", "onRewardArrived"});
        hookIfExists("com.qq.e.ads.rewardvideo.RewardVideoADListener",
                lp.classLoader, new String[]{"onVideoComplete", "onReward"});
        hookIfExists("com.kwad.sdk.api.KsRewardVideoAd$RewardAdInteractionListener",
                lp.classLoader, new String[]{"onVideoPlayEnd", "onRewardVerify"});
        hookIfExists("com.baidu.mobads.sdk.api.RewardVideoAd$RewardVideoAdListener",
                lp.classLoader, new String[]{"playCompletion", "onRewardVerify"});
        hookIfExists("com.sigmob.windad.rewardVideo.WindRewardVideoAdListener",
                lp.classLoader, new String[]{"onRewardVerify"});

        // ★ 吉人天相自研框架 com.zygote.b
        hookZygoteTaskManager(lp.classLoader);

        XposedBridge.log(TAG + ": 广告SDK回调 Hook 完成");
    }

    // ============================================================
    // ★★ 吉人天相核心：com.zygote.b.base.ad.render.TaskManager
    //   onResume() 中: 累计观看 >= 10000ms 才 onViewCompleted()
    //   Hook: 进入前把 mRewardViewAccumulatedTimeMs 拨到 10000
    // ============================================================
    private void hookZygoteTaskManager(ClassLoader cl) {
        try {
            Class<?> tm = XposedHelpers.findClass("com.zygote.b.base.ad.render.TaskManager", cl);
            XposedHelpers.findAndHookMethod(tm, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedHelpers.setLongField(param.thisObject, "mRewardViewAccumulatedTimeMs", 10000L);
                    XposedBridge.log(TAG + ": [TaskManager] 累计时长拉满 -> 10000ms");
                }
            });
            XposedBridge.log(TAG + ": ★ TaskManager Hook 成功（吉人天相）");
        } catch (Throwable t) {
            // 无该框架，忽略
        }
    }

    private void hookIfExists(String className, ClassLoader cl, String[] methods) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            for (final String m : methods) {
                for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                    if (!method.getName().equals(m)) continue;
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + ": 广告回调 " + className + "." + m);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            // 该类不存在，忽略
        }
    }

    // ============================================================
    // JS 桥 / 终极通杀点
    // ============================================================
    private void hookUniBridge(XC_LoadPackage.LoadPackageParam lp) {
        ClassLoader cl = lp.classLoader;

        // 吉人天相/Cocos：GameHandler.nativeCallJsFunction
        try {
            Class<?> gh = XposedHelpers.findClass("com.zygote.b.base.js.base.GameHandler", cl);
            XposedBridge.hookAllMethods(gh, "nativeCallJsFunction", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
      
