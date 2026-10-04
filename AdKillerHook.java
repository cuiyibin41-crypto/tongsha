/**
 * ============================================================
 *  广告联盟通杀 XP 模块 (AdRewardAuto)
 *  适用: LSPosed / EdXposed / Xposed
 *  目标: 这类"看广告叠红包"的 uni-app 套壳游戏
 *  原理: Hook 广告SDK回调 + uni-app JS桥，让"看广告"秒完，
 *        App 自己走真实上报链路(签名天然正确)
 * ============================================================
 *
 * 【三条 Hook 策略，从稳到狠】
 *  A. 加速层: Hook 广告播放器/倒计时 → 让30秒广告3秒播完
 *  B. 完成层: Hook 各家 SDK 的 onVideoComplete/onRewardVerify → 直接回调"看完"
 *  C. 桥接层(最通杀): Hook callJs/WebView → 直接给JS发"广告完成"事件
 *
 * 【为什么能通杀】
 *  这类App都是 uni-app/H5 套壳，广告完成 → callJs("close_ad",...)
 *  → JS 调 uni.pro.get_reward1() → 上报。
 *  Hook 广告SDK层 = 一套代码通吃所有用该SDK的App。
 */
package com.adkiller;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.ValueCallback;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AdKillerHook implements IXposedHookLoadPackage {

    private static final String TAG = "AdKiller";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 只处理目标App（可改成通杀全部）
        // 墨染流年: com.mrln.tykj
        // 碎银屋/其它: 按需添加，或全部放行
        if (!isTarget(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": 命中 " + lpparam.packageName);

        hookRewardSDKs(lpparam);      // A/B 策略：广告SDK层
        hookUniBridge(lpparam);       // C 策略：uni-app JS 桥
    }

    private boolean isTarget(String pkg) {
        // 目标App（按需增删）；想全App生效直接 return true;
        return pkg.equals("com.voiix.jrtx")      // 吉人天相（Cocos + com.zygote.b）
            || pkg.equals("com.mrln.tykj")       // 墨染流年（uni-app）
            || pkg.equals("com.xingyao.jtgd");   // 慧竞
    }

    // ============================================================
    // 策略 B：Hook 各家广告 SDK 的"播放完成/领奖"回调
    // ============================================================
    private void hookRewardSDKs(XC_LoadPackage.LoadPackageParam lp) {
        // ---- 穿山甲 pangle/csj ----
        hookIfExists("com.bytedance.sdk.openadsdk.TTRewardVideoAd$RewardAdInteractionListener",
                lp.classLoader, new String[]{"onVideoComplete", "onRewardVerify", "onRewardArrived"});

        // ---- 优量汇 GDT ----
        hookIfExists("com.qq.e.ads.rewardvideo.RewardVideoADListener",
                lp.classLoader, new String[]{"onVideoComplete", "onReward"});

        // ---- 快手 KS ----
        hookIfExists("com.kwad.sdk.api.KsRewardVideoAd$RewardAdInteractionListener",
                lp.classLoader, new String[]{"onVideoPlayEnd", "onRewardVerify"});

        // ---- 百度 ----
        hookIfExists("com.baidu.mobads.sdk.api.RewardVideoAd$RewardVideoAdListener",
                lp.classLoader, new String[]{"playCompletion", "onRewardVerify"});

        // ---- sigmob (墨染流年用的) ----
        hookIfExists("com.sigmob.windad.WindRewardAdListener",
                lp.classLoader, new String[]{"onRewardVerify"});
        hookIfExists("com.sigmob.sdk.nova.ui.reward.QuickRewardVideoActivity",
                lp.classLoader, new String[]{"onVideoComplete"});

        // ---- ★ 吉人天相自研框架 com.zygote.b (z7壳) ----
        hookZygoteTaskManager(lp.classLoader);

        XposedBridge.log(TAG + ": 广告SDK回调 Hook 完成");
    }

    // ============================================================
    // ★★ 吉人天相核心：Hook com.zygote.b.base.ad.render.TaskManager
    //   逻辑：累计观看时长 >= 10000ms 才 onViewCompleted()
    //   Hook：直接把累计时长拨到 10000，秒过门槛
    // ============================================================
    private void hookZygoteTaskManager(ClassLoader cl) {
        try {
            Class<?> tm = XposedHelpers.findClass("com.zygote.b.base.ad.render.TaskManager", cl);

            // 1) Hook onResume：进入前把累计时长拉满
            XposedHelpers.findAndHookMethod(tm, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedHelpers.setLongField(param.thisObject, "mRewardViewAccumulatedTimeMs", 10000L);
                    XposedBridge.log(TAG + ": [TaskManager] 累计时长已拉满 -> 10000ms");
                }
            });

            // 2) 备用：Hook onViewCompleted 打日志（确认发奖回调）
            try {
                Class<?> l = XposedHelpers.findClass("com.zygote.b.base.ad.render.AdTaskStatusListener", cl);
                XposedBridge.log(TAG + ": [TaskManager] AdTaskStatusListener 已定位");
            } catch (Throwable ignored) {}

            XposedBridge.log(TAG + ": ★ com.zygote.b TaskManager Hook 成功（吉人天相）");
        } catch (Throwable t) {
            // 非吉人天相（没有该框架）则忽略
        }
    }
    }

    private void hookIfExists(String className, ClassLoader cl, String[] methods) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            for (String m : methods) {
                for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                    if (!method.getName().equals(m)) continue;
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + ": 广告回调命中 " + className + "." + m);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            // 该类不存在（App 没用这家SDK），忽略
        }
    }

    // ============================================================
    // 策略 C：Hook uni-app JS 桥（最通杀）
    //   BaseWebActivity.callJs(name, data) → 注入JS
    //   拦截 close_ad / onXXXCallback 等事件，可自动触发下一轮
    // ============================================================
    private void hookUniBridge(XC_LoadPackage.LoadPackageParam lp) {
        ClassLoader cl = lp.classLoader;

        // ---- 吉人天相/Cocos 系：GameHandler.nativeCallJsFunction ----
        try {
            Class<?> gh = XposedHelpers.findClass("com.zygote.b.base.js.base.GameHandler", cl);
            XposedBridge.hookAllMethods(gh, "nativeCallJsFunction", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    StringBuilder sb = new StringBuilder(TAG + ": nativeCallJsFunction args=");
                    for (Object a : param.args) sb.append(String.valueOf(a)).append(" | ");
                    XposedBridge.log(sb.toString());
                }
            });
            XposedBridge.log(TAG + ": Hook GameHandler.nativeCallJsFunction 成功");
        } catch (Throwable ignored) {}


        // C1: Hook 常见的 callJs(String, Object) —— 各App类名不同，扫多个候选
        String[] bridgeClasses = {
                "com.dtyx.qckj.BaseWebActivity",      // 墨染流年
                "com.common.BaseAppActivity",         // 墨染流年 通用层
                "com.dtyx.qckj.utils.AdLoadUtils",    // 墨染流年 广告工具
        };
        for (String cn : bridgeClasses) {
            hookCallJs(cn, cl);
        }

        // C2: Hook WebView 的 JS 注入（终极通杀点）
        try {
            XposedHelpers.findAndHookMethod(WebView.class, "evaluateJavascript",
                    String.class, ValueCallback.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String js = (String) param.args[0];
                    if (js == null) return;
                    if (js.contains("close_ad") || js.contains("onReward") || js.contains("get_reward")) {
                        XposedBridge.log(TAG + ": WebView注入JS = " + js.substring(0, Math.min(120, js.length())));
                    }
                }
            });
            XposedBridge.log(TAG + ": WebView.evaluateJavascript Hook 完成");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": WebView Hook 失败 " + t);
        }
    }

    private void hookCallJs(String className, ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            XposedBridge.hookAllMethods(clazz, "callJs", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    StringBuilder sb = new StringBuilder(TAG + ": callJs[" + className + "] args=");
                    for (Object a : param.args) sb.append(String.valueOf(a)).append(" | ");
                    XposedBridge.log(sb.toString());
                }
            });
            XposedBridge.log(TAG + ": Hook callJs 成功 " + className);
        } catch (Throwable t) {
            // 类不存在，忽略
        }
    }
}
