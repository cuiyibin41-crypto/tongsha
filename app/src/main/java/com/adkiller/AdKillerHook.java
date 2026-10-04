/**
 * ============================================================
 *  AdKiller —— 四大联盟「权重」放大器 XP 模块
 *  主目标：修仙来财(com.whyd.xxlc) / 吉人天相(com.voiix.jrtx)
 *        均为 com.zygote + com.coohua 框架
 *
 *  ★核心原理（已从修仙来财APK确认）：
 *    CoefTool.getCoefEcpm(adType, ecpm) = ecpm × CoefTool.getCoef(adType)
 *    “权重”就是 CoefInfo.bidDiscountValue / waterFallDiscountValue
 *    各大联盟上报服务(TTRspService/GDTRspService/KSRspService/...)
 *    都调 getCoefEcpm → 一个Hook点通杀所有联盟
 *
 *  策略：
 *    1) Hook CoefTool.getCoef / getCoefV2 → 权重系数直接放大（主）
 *    2) Hook CoefTool.getCoefEcpm → 最终eCPM双保险
 *    3) Hook CoefInfo 字段（bidDiscountValue/waterFallDiscountValue）
 *    4) 通用兜底：动态拦截四大联盟 getECPM
 * ============================================================
 */
package com.adkiller;

import java.lang.reflect.Field;
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

    private static boolean cfgEnable = true;
    private static float cfgMulti = 10f;
    private static int cfgFixed = 0;      // eCPM固定值(>0生效)
    private static int cfgMin = 0;
    private static boolean cfgSkip = false;

    private static final Set<String> hookedClasses = Collections.synchronizedSet(new HashSet<String>());

    private static final String[] SDK_PREFIXES = {
            "com.coohua.",                       // 聚合SDK（修仙来财/吉人天相核心，含权重）
            "com.zygote.",                       // 自研框架
            "com.qq.e.",                         // 优量汇 GDT
            "com.kwad.",                         // 快手 KS
            "com.baidu.mobads.",                 // 百度
            "com.bytedance.sdk.openadsdk.",      // 穿山甲
            "com.byazt.",                        // 穿山甲（混淆）
            "com.bytedance.msdk.",               // GroMore
            "com.sigmob.",                       // sigmob
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!isTarget(lpparam.packageName)) return;
        loadConfig();
        XposedBridge.log(TAG + ": 命中 " + lpparam.packageName
                + " 启用=" + cfgEnable + " 倍率x" + cfgMulti + " 固定=" + cfgFixed);
        if (!cfgEnable) return;

        ClassLoader cl = lpparam.classLoader;

        hookCoefTool(cl);          // ★ 权重核心
        hookCoefInfoFields(cl);    // ★ 权重字段直接改
        hookZygoteRender(cl);      // 渲染层兜底
        hookClassLoader();         // 通用动态拦截兜底

        if (cfgSkip) hookSkip10s(cl);
    }

    private boolean isTarget(String pkg) {
        return pkg.equals("com.whyd.xxlc")      // 修仙来财
            || pkg.equals("com.voiix.jrtx")     // 吉人天相
            || pkg.equals("com.mrln.tykj")      // 墨染流年
            || pkg.equals("com.xingyao.jtgd");  // 慧竞
    }

    private void loadConfig() {
        try {
            XSharedPreferences p = new XSharedPreferences("com.adkiller", Prefs.NAME);
            p.makeWorldReadable();
            cfgEnable = p.getBoolean(Prefs.KEY_ENABLE, true);
            cfgMulti  = p.getFloat(Prefs.KEY_MULTI, 10f);
            cfgFixed  = p.getInt(Prefs.KEY_FIXED, 0);
            cfgMin    = p.getInt(Prefs.KEY_MIN, 0);
            cfgSkip   = p.getBoolean(Prefs.KEY_SKIP, false);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 读配置失败，用默认 " + t);
        }
    }

    // ============================================================
    // ★★★ 核心：权重系数 CoefTool
    // ============================================================
    private void hookCoefTool(ClassLoader cl) {
        try {
            Class<?> ct = XposedHelpers.findClass("com.coohua.base.sdk.ad.coef.CoefTool", cl);

            for (final Method m : ct.getDeclaredMethods()) {
                String mn = m.getName();
                // getCoef(int)D / getCoefV2(int)D —— 取权重系数
                // getCoefEcpm(int,double)D —— 最终eCPM
                boolean isTarget =
                        (mn.equals("getCoef") && m.getReturnType() == double.class
                                && m.getParameterTypes().length == 1)
                     || (mn.equals("getCoefV2") && m.getReturnType() == double.class)
                     || mn.equals("getCoefEcpm");
                if (!isTarget) continue;
                if (Modifier.isAbstract(m.getModifiers()) || Modifier.isNative(m.getModifiers())) continue;

                final boolean isEcpm = mn.equals("getCoefEcpm");
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            double orig = ((Number) param.getResult()).doubleValue();
                            double boosted = isEcpm ? boostEcpmFinal(orig) : boostCoefficient(orig);
                            if (boosted != orig) {
                                param.setResult(boosted);
                                XposedBridge.log(TAG + ": ★权重 " + m.getName()
                                        + " " + orig + " -> " + boosted);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log(TAG + ": +Hook CoefTool." + mn + "()" + m.getParameterTypes().length + "参");
            }
            XposedBridge.log(TAG + ": ★ CoefTool 权重 Hook 完成");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": CoefTool 不存在 " + t.getMessage());
        }
    }

    /** 权重系数放大（系数通常是 0~2 之间的小数） */
    private double boostCoefficient(double coef) {
        try {
            if (cfgFixed > 0) return cfgFixed;
            if (coef <= 0) return coef;
            if (cfgMin > 0 && coef < cfgMin) return coef;
            return coef * (double) cfgMulti;
        } catch (Throwable t) { return coef; }
    }

    /** 最终eCPM放大（不再乘倍率，只做保底监控；系数已放大） */
    private double boostEcpmFinal(double ecpm) {
        return ecpm; // 由系数层负责放大；这里保留钩子仅做日志/扩展
    }

    // ============================================================
    // ★ 权重字段：CoefInfo.bidDiscountValue / waterFallDiscountValue
    // ============================================================
    private void hookCoefInfoFields(ClassLoader cl) {
        try {
            Class<?> ci = XposedHelpers.findClass("com.coohua.base.sdk.ad.coef.CoefInfo", cl);
            for (final Field f : ci.getDeclaredFields()) {
                String fn = f.getName();
                if (!(fn.equals("bidDiscountValue") || fn.equals("waterFallDiscountValue"))) continue;
                if (f.getType() != double.class) continue;
                f.setAccessible(true);
                XposedBridge.log(TAG + ": 找到权重字段 CoefInfo." + fn);
                // 无字段级Hook，改用包装：在对象首次被读时无人拦截；由 getCoef 层已覆盖。
            }
            // 更直接：init 时替换 map 值不可行（无逐对象时机）；getCoef已覆盖，无需额外。
        } catch (Throwable ignored) {}
    }

    // ============================================================
    // 吉人天相渲染层兜底（RenderAdData）
    // ============================================================
    private void hookZygoteRender(ClassLoader cl) {
        try {
            Class<?> rad = XposedHelpers.findClass("com.zygote.b.base.ad.render.RenderAdData", cl);
            XposedBridge.hookAllConstructors(rad, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        String ecpm = (String) XposedHelpers.getObjectField(param.thisObject, "ecpm");
                        if (ecpm != null && !ecpm.isEmpty()) {
                            double v = Double.parseDouble(ecpm);
                            long b = Math.round(v * (double) cfgMulti);
                            XposedHelpers.setObjectField(param.thisObject, "ecpm", String.valueOf(b));
                            XposedBridge.log(TAG + ": RenderAdData eCPM " + ecpm + " -> " + b);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + ": ★ RenderAdData Hook 成功");
        } catch (Throwable ignored) {}
    }

    // ============================================================
    // 通用兜底：动态类加载拦截（四大联盟 getECPM）
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
                        if (!matchPrefix(name) || hookedClasses.contains(name)) return;
                        hookedClasses.add(name);
                        hookEcpmMethods((Class<?>) res);
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + ": ★ ClassLoader 拦截已启用");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ClassLoader Hook 失败 " + t);
        }
    }

    private boolean matchPrefix(String name) {
        for (String p : SDK_PREFIXES) if (name.startsWith(p)) return true;
        return false;
    }

    private void hookEcpmMethods(Class<?> clazz) {
        try {
            if (clazz.isInterface()) return;
            if (clazz.getName().contains("coef")) return; // coef 已专门处理
            for (Method m : clazz.getDeclaredMethods()) {
                try {
                    String mn = m.getName().toLowerCase();
                    if (!(mn.startsWith("get") && mn.contains("ecpm"))) continue;
                    if (Modifier.isAbstract(m.getModifiers()) || Modifier.isNative(m.getModifiers())) continue;
                    if (m.getParameterTypes().length != 0) continue;
                    Class<?> rt = m.getReturnType();
                    if (!(rt == int.class || rt == Integer.class || rt == long.class || rt == Long.class
                            || rt == float.class || rt == Float.class || rt == double.class
                            || rt == Double.class || rt == String.class)) continue;
                    final Class<?> frt = rt;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object orig = param.getResult();
                                Object boosted = boostAny(frt, orig);
                                if (boosted != null && !boosted.equals(orig)) {
                                    param.setResult(boosted);
                                    XposedBridge.log(TAG + ": eCPM " + orig + " -> " + boosted
                                            + " @" + param.method.getDeclaringClass().getSimpleName());
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private Object boostAny(Class<?> rt, Object orig) {
        try {
            if (orig == null) return null;
            if (rt == int.class || rt == Integer.class) {
                int v = ((Number) orig).intValue();
                long b = boostLong(v);
                if (b < 0) b = 0; if (b > Integer.MAX_VALUE) b = Integer.MAX_VALUE;
                return (int) b;
            }
            if (rt == long.class || rt == Long.class) {
                long b = boostLong(((Number) orig).longValue());
                return b < 0 ? 0L : b;
            }
            if (rt == float.class || rt == Float.class) return (float) boostDouble(((Number) orig).doubleValue());
            if (rt == double.class || rt == Double.class) return boostDouble(((Number) orig).doubleValue());
            if (rt == String.class) {
                String s = ((String) orig).trim();
                if (s.isEmpty()) return orig;
                double d;
                try { d = Double.parseDouble(s); } catch (Throwable e) { return orig; }
                double b = boostDouble(d);
                return s.indexOf('.') < 0 ? String.valueOf(Math.round(b)) : String.valueOf(Math.round(b * 100.0) / 100.0);
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
    // 秒过10秒门槛（可选）
    // ============================================================
    private void hookSkip10s(ClassLoader cl) {
        try {
            Class<?> tm = XposedHelpers.findClass("com.zygote.b.base.ad.render.TaskManager", cl);
            XposedHelpers.findAndHookMethod(tm, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try { XposedHelpers.setLongField(param.thisObject, "mRewardViewAccumulatedTimeMs", 10000L); }
                    catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + ": ★ TaskManager 秒过已启用");
        } catch (Throwable ignored) {}
    }
}
