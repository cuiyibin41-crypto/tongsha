/**
 * ============================================================
 *  AdKiller —— 广告奖励放大器 XP 模块
 *  目标：通杀四大广告联盟（穿山甲/优量汇/快手/百度）及聚合层，
 *        放大 eCPM → 提高看广告奖励
 *
 *  核心策略：动态类加载拦截
 *    Hook ClassLoader.loadClass，当广告 SDK 的类加载时，
 *    自动扫描其 getECPM/getEcpm 方法并 Hook 返回值。
 *    一套代码通杀四大联盟所有版本。
 *
 *  吉人天相(com.voiix.jrtx)专用链路：
 *    RenderAdData.ecpm → SignUtils.getCommonSign → RSA签名上报
 *    （eCPM在签名前被篡改，App自己签名，服务端验签通过）
 * ============================================================
 */
package com.adkiller;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AdKillerHook implements IXposedHookLoadPackage {

    private static final String TAG = "AdKiller";

    // ===== 配置（从模块UI跨进程读取） =====
    private static boolean cfgEnable = true;
    private static float cfgMulti = 20f;
    private static int cfgFixed = 0;
    private static int cfgMin = 0;
    private static boolean cfgSkip = false;

    // 已 Hook 的类，避免重复
    private static final Set<String> hookedClasses = Collections.synchronizedSet(new HashSet<String>());

    // 四大联盟 + 聚合层 类名前缀（命中即扫描 eCPM 方法）
    private static final String[] SDK_PREFIXES = {
            "com.qq.e.",                        // 优量汇 GDT
            "com.kwad.",                        // 快手 KS
            "com.baidu.mobads.",                // 百度
            "com.bytedance.sdk.openadsdk.",     // 穿山甲 Pangle
            "com.byazt.",                       // 穿山甲（混淆包名）
            "com.bytedance.msdk.",              // GroMore 聚合
            "com.sigmob.",                      // sigmob
            "com.zygote.",                      // 吉人天相自研框架
            "com.coohua.",                      // coohua 聚合
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!isTarget(lpparam.packageName)) return;

        loadConfig();
        XposedBridge.log(TAG + ": 命中 " + lpparam.packageName
                + " 启用=" + cfgEnable + " 倍率x" + cfgMulti + " 固定=" + cfgFixed + " 底限=" + cfgMin);

        if (!cfgEnable) return;

        // 1) 动态类加载拦截（通杀核心）
        hookClassLoader();

        // 2) 吉人天相专用直接 Hook（双保险）
        hookZygoteDirect(lpparam.classLoader);

        // 3) 可选：秒过 10 秒观看门槛
        if (cfgSkip) hookSkip10s(lpparam.classLoader);
    }

    private boolean isTarget(String pkg) {
        return pkg.equals("com.voiix.jrtx")     // 吉人天相
            || pkg.equals("com.mrln.tykj")      // 墨染流年
            || pkg.equals("com.xingyao.jtgd");  // 慧竞
    }

    private void loadConfig() {
        try {
            XSharedPreferences p = new XSharedPreferences("com.adkiller", Prefs.NAME);
            p.makeWorldReadable();
            cfgEnable = p.getBoolean(Prefs.KEY_ENABLE, true);
            cfgMulti  = p.getFloat(Prefs.KEY_MULTI, 20f);
            cfgFixed  = p.getInt(Prefs.KEY_FIXED, 0);
            cfgMin    = p.getInt(Prefs.KEY_MIN, 0);
            cfgSkip   = p.getBoolean(Prefs.KEY_SKIP, false);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 读配置失败，用默认 " + t);
        }
    }

    // ============================================================
    // 动态类加载拦截：SDK 类一加载就扫描 eCPM 方法
    // ============================================================
    private void hookClassLoader() {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass",
                    String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object res = param.getResult();
                        if (!(res instanceof Class)) return;
                        String name = (String) param.args[0];
                        if (!matchPrefix(name)) return;
                        if (hookedClasses.contains(name)) return;
                        hookedClasses.add(name);
                        hookEcpmMethods((Class<?>) res);
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + ": ★ ClassLoader 动态拦截已启用");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ClassLoader Hook 失败 " + t);
        }
    }

    private boolean matchPrefix(String name) {
        for (String p : SDK_PREFIXES) {
            if (name.startsWith(p)) return true;
        }
        return false;
    }

    /** 扫描类中 getECPM/getEcpm 等方法并 Hook 返回值 */
    private void hookEcpmMethods(Class<?> clazz) {
        try {
            if (clazz.isInterface()) return;
            for (Method m : clazz.getDeclaredMethods()) {
                try {
                    String mn = m.getName().toLowerCase();
                    if (!mn.startsWith("get") || !mn.contains("ecpm")) continue;
                    if (Modifier.isAbstract(m.getModifiers()) || Modifier.isNative(m.getModifiers())) continue;
                    if (m.getParameterTypes().length != 0) continue;

                    Class<?> rt = m.getReturnType();
                    if (!(rt == int.class || rt == Integer.class
                            || rt == long.class || rt == Long.class
                            || rt == float.class || rt == Float.class
                            || rt == double.class || rt == Double.class
                            || rt == String.class)) continue;

                    final Class<?> frt = rt;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object orig = param.getResult();
                            Object boosted = boostValue(frt, orig);
                            if (boosted != null && !boosted.equals(orig)) {
                                param.setResult(boosted);
                                XposedBridge.log(TAG + ": eCPM " + orig + " -> " + boosted
                                        + " @" + param.method.getDeclaringClass().getSimpleName()
                                        + "." + param.method.getName());
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": +Hook " + clazz.getName() + "." + m.getName() + "()" + rt.getSimpleName());
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    /** 数值放大 */
    private Object boostValue(Class<?> rt, Object orig) {
        try {
            if (orig == null) return null;
            if (rt == int.class || rt == Integer.class) {
                int v = ((Number) orig).intValue();
                long b = boostLong(v);
                if (b < 0) b = 0;
                if (b > Integer.MAX_VALUE) b = Integer.MAX_VALUE;
                return (int) b;
            }
            if (rt == long.class || rt == Long.class) {
                long b = boostLong(((Number) orig).longValue());
                return b < 0 ? 0L : b;
            }
            if (rt == float.class || rt == Float.class) {
                return (float) boostDouble(((Number) orig).doubleValue());
            }
            if (rt == double.class || rt == Double.class) {
                return boostDouble(((Number) orig).doubleValue());
            }
            if (rt == String.class) {
                String s = ((String) orig).trim();
                if (s.isEmpty()) return orig;
                double d;
                try { d = Double.parseDouble(s); } catch (Throwable e) { return orig; }
                double b = boostDouble(d);
                // 原来是整数则输出整数，否则保留两位
                if (s.indexOf('.') < 0) return String.valueOf(Math.round(b));
                return String.valueOf(Math.round(b * 100.0) / 100.0);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private long boostLong(long v) {
        if (cfgFixed > 0) return cfgFixed;
        if (v <= 0) return v;
        if (cfgMin > 0 && v < cfgMin) return v;
        return Math.round(v * (double) cfgMulti);
    }

    private double boostDouble(double v) {
        if (cfgFixed > 0) return cfgFixed;
        if (v <= 0) return v;
        if (cfgMin > 0 && v < cfgMin) return v;
        return v * (double) cfgMulti;
    }

    // ============================================================
    // 吉人天相专用：RenderAdData + 聚合 eCPM 读取点
    // ============================================================
    private void hookZygoteDirect(ClassLoader cl) {
        // RenderAdData: 字段/构造/setEcpm 三保险
        try {
            Class<?> rad = XposedHelpers.findClass("com.zygote.b.base.ad.render.RenderAdData", cl);
            XposedBridge.hookAllConstructors(rad, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        String ecpm = (String) XposedHelpers.getObjectField(param.thisObject, "ecpm");
                        String b = (String) boostValue(String.class, ecpm);
                        if (b != null && !b.equals(ecpm)) {
                            XposedHelpers.setObjectField(param.thisObject, "ecpm", b);
                            XposedBridge.log(TAG + ": RenderAdData构造 eCPM " + ecpm + " -> " + b);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedHelpers.findAndHookMethod(rad, "setEcpm", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String old = (String) param.args[0];
                    String b = (String) boostValue(String.class, old);
                    if (b != null) {
                        param.args[0] = b;
                        XposedBridge.log(TAG + ": setEcpm " + old + " -> " + b);
                    }
                }
            });
            XposedBridge.log(TAG + ": ★ RenderAdData Hook 成功");
        } catch (Throwable ignored) {}

        // GroMore 聚合最大 eCPM
        try {
            Class<?> g = XposedHelpers.findClass("com.zygote.b.base.ad.gromore.SdkLoaderGromore", cl);
            XposedHelpers.findAndHookMethod(g, "getMaxECpm", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object o = param.getResult();
                    Object b = boostValue(Double.class, o);
                    if (b != null) param.setResult(b);
                }
            });
            XposedBridge.log(TAG + ": ★ SdkLoaderGromore.getMaxECpm Hook 成功");
        } catch (Throwable ignored) {}
    }

    // ============================================================
    // 可选：秒过“累计观看 10 秒”门槛
    // ============================================================
    private void hookSkip10s(ClassLoader cl) {
        try {
            Class<?> tm = XposedHelpers.findClass("com.zygote.b.base.ad.render.TaskManager", cl);
            XposedHelpers.findAndHookMethod(tm, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedHelpers.setLongField(param.thisObject, "mRewardViewAccumulatedTimeMs", 10000L);
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + ": ★ TaskManager 秒过 10s 已启用");
        } catch (Throwable ignored) {}
    }
}
