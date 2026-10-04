package com.adkiller;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * 配置存储：
 *  - UI 侧用普通 SharedPreferences 写入
 *  - Hook 侧（在目标App进程）用 XSharedPreferences 跨进程读取
 */
public class Prefs {
    public static final String NAME = "adkiller_cfg";

    public static final String KEY_ENABLE = "enable";
    public static final String KEY_MULTI = "multiplier";
    public static final String KEY_FIXED = "fixed_ecpm";
    public static final String KEY_SKIP = "skip_10s";
    public static final String KEY_MIN = "min_ecpm";

    // ===== UI 侧（本模块进程）=====
    public static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** 让 sp 文件可被其他进程读取（模块保存时调用）*/
    public static void makeReadable(Context ctx) {
        try {
            File f = new File(ctx.getApplicationInfo().dataDir + "/shared_prefs/" + NAME + ".xml");
            if (f.exists()) {
                f.setReadable(true, false);
                File dir = f.getParentFile();
                if (dir != null) dir.setExecutable(true, false);
            }
        } catch (Throwable ignored) {}
    }

    // ===== Hook 侧（目标App进程）读取=====
    // 为避免依赖 XSharedPreferences 类，这里用静态共享对象；
    // 实际取值由 AdKillerHook 通过 XSharedPreferences 注入。
    public static boolean enable = true;
    public static float multiplier = 20f;
    public static int fixedEcpm = 0;
    public static boolean skip10s = false;
    public static int minEcpm = 0;
}
