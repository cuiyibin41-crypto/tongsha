package com.adkiller;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** 配置存储（UI 写入 / Hook 跨进程读取） */
public class Prefs {
    public static final String NAME = "adkiller_cfg";

    public static final String KEY_ENABLE = "enable";
    public static final String KEY_MULTI = "multiplier";
    public static final String KEY_FIXED = "fixed_ecpm";
    public static final String KEY_SKIP = "skip_10s";
    public static final String KEY_MIN = "min_ecpm";

    // ===== UI 侧 =====
    public static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** 保存后让配置文件可被目标进程读取 */
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
}