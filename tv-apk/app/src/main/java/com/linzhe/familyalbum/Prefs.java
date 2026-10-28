package com.linzhe.familyalbum;

import android.content.Context;
import android.content.SharedPreferences;

/** 设置持久化 */
public class Prefs {
    private static final String FILE = "settings";

    public static String server(Context c) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        String s = p.getString("server", "").trim();
        if (s.isEmpty()) s = "http://192.168.1.100:8787";
        if (!s.endsWith("/")) s += "/";
        return s;
    }

    public static void setServer(Context c, String v) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("server", v.trim()).apply();
    }

    /** 图片停留秒数 */
    public static int interval(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("interval", 8);
    }

    public static void setInterval(Context c, int sec) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("interval", Math.max(2, sec)).apply();
    }

    /** true=静音(默认) */
    public static boolean muted(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("muted", true);
    }

    public static void setMuted(Context c, boolean b) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("muted", b).apply();
    }
}
