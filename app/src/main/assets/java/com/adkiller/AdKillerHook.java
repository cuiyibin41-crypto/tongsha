/**
 * ============================================================
 *  广告奖励放大器 XP 模块 (AdRewardBoost)
 *  适用: LSPosed / EdXposed，兼容各App
 *
 *  目标: 提高“看广告”的奖励金额（不是秒过，是改eCPM拿更多）
 *  原理: 在 eCPM 被签名上报之前篡改它 →
 *        App自己拿篡改后的值做签名(RSA) → 服务端验签通过 → 发大额奖励
 * ============================================================
 *
 *  吉人天相(com.voiix.jrtx)关键链路（已逆向）：
 *    RenderAdData.ecpm  ──>  SignUtils.getCommonSign("ecpm=...")
 *      → toSign = userId_时间戳_appId_eCPM
 *      → RSA/ECB/PKCS1Padding 加密 → Base64 → 上报
 *    所以在 RenderAdData.setEcpm / 构造时改值即可。
 */
package com.adkiller;

import android.webkit.ValueCallback;
import android.webkit.WebView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AdKillerHook implements IXposedHookLoadPackage {

    private static final String TAG = "AdKiller";

    // 配置（从模块UI的 SharedPreferences 跨进程读取）
    private static boolean cfgEnable = true;
    private static float cfgMulti = 20f;
    private static int cfgFixed = 0;
    private static int cfgMin = 0;
    private static boolean cfgSkip = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!isTarget(lpparam.packageName)) return;
        loadConfig();
        XposedBridge.log(TAG + ": 命中 " + lpparam.packageName + " 倍率x" + cfgMulti + " 固定=" + cfgFixed + " 启用=" + cfgEnable);

        if (!cfgEnable) return;

        hookZygoteEcpm(lpparam.classLoader);   // ★ 吉人天相/zygote 系
        hookZygoteTaskManager(lpparam.classLoader); // 顺带秒过10秒门槛
        hookRewardSDKs(lpparam);               // 其它聚合SDK备用
        hookUniBridge(lpparam);                // uni-app / WebView
    }

    private boolean isTarget(String pkg) {
        return pkg.equals("com.voiix.jrtx")     // 吉人天相
            || pkg.equals("com.mrln.tykj")      // 墨染流年
            || pkg.equals("com.xingyao.jtgd");  // 慧竞
        // 想全App生效: return true;
    }

    /** 在目标App进程读取模块UI保存的配置 */
    private void loadConfig() {
        try {
            XSharedPreferences prefs = new XSharedPreferences("com.adkiller", Prefs.NAME);
            prefs.makeWorldReadable();
            cfgEnable = prefs.getBoolean(Prefs.KEY_ENABLE, true);
            cfgMulti  = prefs.getFloat(Prefs.KEY_MULTI, 20f);
            cfgFixed  = prefs.getInt(Prefs.KEY_FIXED, 0);
            cfgMin    = prefs.getInt(Prefs.KEY_MIN, 0);
            cfgSkip   = prefs.getBoolean(Prefs.KEY_SKIP, false);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 读配置失败，用默认 " + t);
        }
    }

    // ============================================================
    // ★★ 核心：篡改 eCPM（RenderAdData） → 提高奖励
    // ============================================================
    private void hookZygoteEcpm(ClassLoader cl) {
        try {
            Class<?> rad = XposedHelpers.findClass("com.zygote.b.base.ad.render.RenderAdData", cl);
            // Hook 构造与 setEcpm，把 eCPM 值改成放大后的
            XposedBridge.hookAllConstructors(rad, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    boostEcpmField(param.thisObject);
                }
            });
            XposedHelpers.findAndHookMethod(rad, "setEcpm", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String old = (String) param.args[0];
                    param.args[0] = boostEcpmValue(old);
                    XposedBridge.log(TAG + ": RenderAdData.setEcpm " + old + " -> " + param.args[0]);
                }
            });
            XposedBridge.log(TAG + ": ★ RenderAdData(ecpm) Hook 成功");
        } catch (Throwable t) {
            // 非 zygote 系，忽略
        }
    }

    private void boostEcpmField(Object obj) {
        try {
            String ecpm = (String) XposedHelpers.getObjectField(obj, "ecpm");
            String boosted = boostEcpmValue(ecpm);
            XposedHelpers.setObjectField(obj, "ecpm", boosted);
            XposedBridge.log(TAG + ": 构造期 eCPM " + ecpm + " -> " + boosted);
        } catch (Throwable ignored) {}
    }

    private String boostEcpmValue(String ecpm) {
        try {
            if (cfgFixed > 0) return String.valueOf(cfgFixed);
            if (ecpm == null || ecpm.isEmpty()) return ecpm;
            double v = Double.parseDouble(ecpm);
            if (v <= 0) return ecpm;
            if (v < cfgMin) return ecpm;  // 低于阈值不动
            long boosted = Math.round(v * cfgMulti);
            return String.valueOf(boosted);
        } catch (Throwable t) {
            return ecpm;
        }
    }

    // ============================================================
    // 顺带：秒过“累计观看10秒”门槛
    // ============================================================
    private void hookZygoteTaskManager(ClassLoader cl) {
        if (!cfgSkip) return;  // 未开启则不秒过
        try {
            Class<?> tm = XposedHelpers.findClass("com.zygote.b.base.ad.render.TaskManager", cl);
            XposedHelpers.findAndHookMethod(tm, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedHelpers.setLongField(param.thisObject, "mRewardViewAccumulatedTimeMs", 10000L);
                }
            });
            XposedBridge.log(TAG + ": ★ TaskManager Hook 成功");
        } catch (Throwable ignored) {}
    }

    // ============================================================
    // 广告 SDK 回调监控（备用/日志）
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
    }

    private void hookIfExists(String className, ClassLoader cl, final String[] methods) {
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
        } catch (Throwable t) { /* 类不存在忽略 */ }
    }

    // ============================================================
    // JS 桥 / 终极通杀点
    // ============================================================
    private void hookUniBridge(XC_LoadPackage.LoadPackageParam lp) {
        ClassLoader cl = lp.classLoader;
        try {
            Class<?> gh = XposedHelpers.findClass("com.zygote.b.base.js.base.GameHandler", cl);
            XposedBridge.hookAllMethods(gh, "nativeCallJsFunction", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    StringBuilder sb = new StringBuilder(TAG + ": nativeCallJsFunction ");
                    for (Object a : param.args) sb.append(String.valueOf(a)).append(" | ");
                    XposedBridge.log(sb.toString());
                }
            });
        } catch (Throwable ignored) {}

        String[] bridgeClasses = {
                "com.dtyx.qckj.BaseWebActivity",
                "com.common.BaseAppActivity",
                "com.dtyx.qckj.utils.AdLoadUtils",
        };
        for (String cn : bridgeClasses) hookCallJs(cn, cl);

        try {
            XposedHelpers.findAndHookMethod(WebView.class, "evaluateJavascript",
                    String.class, ValueCallback.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String js = (String) param.args[0];
                    if (js == null) return;
                    if (js.contains("ecpm") || js.contains("close_ad") || js.contains("onReward")) {
                        XposedBridge.log(TAG + ": WebView JS = " + js.substring(0, Math.min(140, js.length())));
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookCallJs(String className, ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            XposedBridge.hookAllMethods(clazz, "callJs", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    StringBuilder sb = new StringBuilder(TAG + ": callJs[" + className + "] ");
                    for (Object a : param.args) sb.append(String.valueOf(a)).append(" | ");
                    XposedBridge.log(sb.toString());
                }
            });
        } catch (Throwable ignored) {}
    }
}
