package de.robv.android.xposed;

import java.io.File;

public class XSharedPreferences {
    public XSharedPreferences(String packageName, String prefFileName) {}
    public XSharedPreferences(String packageName) {}
    public XSharedPreferences(File prefFile) {}
    public boolean getBoolean(String key, boolean defValue) { return defValue; }
    public float getFloat(String key, float defValue) { return defValue; }
    public int getInt(String key, int defValue) { return defValue; }
    public long getLong(String key, long defValue) { return defValue; }
    public String getString(String key, String defValue) { return defValue; }
    public void makeWorldReadable() {}
    public void reload() {}
    public boolean hasFileChanged() { return false; }
}