package com.dexkeybridge.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppState {
    public static final String WINDOWS_APP_PACKAGE = "com.microsoft.rdc.androidx";
    public static final int MODE_OBSERVE = 0;
    public static final int MODE_BLOCK = 1;
    public static final int MODE_FORWARD = 2;

    private static final String PREFS = "dex_key_bridge";
    private static final String KEY_LOG = "event_log";
    private static final String KEY_FOREGROUND = "foreground_package";
    private static final String KEY_TARGET = "target_package";
    private static final String KEY_MODE = "bridge_mode";
    private static final String KEY_META_DETECTED = "meta_detected";
    private static final String KEY_ALT_TAB_DETECTED = "alt_tab_detected";
    private static final String KEY_LAST_INJECTION = "last_injection";
    private static final String KEY_WINDOWS_SESSION_ACTIVE = "windows_session_active";
    private static final int MAX_LOG_CHARS = 36000;

    private AppState() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void appendLog(Context c, String line) {
        SharedPreferences p = prefs(c);
        String current = p.getString(KEY_LOG, "");
        String next = current + line + "\n";
        if (next.length() > MAX_LOG_CHARS) {
            next = next.substring(next.length() - MAX_LOG_CHARS);
        }
        p.edit().putString(KEY_LOG, next).apply();
    }

    public static String getLog(Context c) { return prefs(c).getString(KEY_LOG, ""); }
    public static void clearLog(Context c) { prefs(c).edit().putString(KEY_LOG, "").apply(); }

    public static void setForeground(Context c, String pkg) {
        prefs(c).edit().putString(KEY_FOREGROUND, pkg == null ? "" : pkg).apply();
    }
    public static String getForeground(Context c) {
        return prefs(c).getString(KEY_FOREGROUND, "");
    }

    public static void setTarget(Context c, String pkg) {
        prefs(c).edit().putString(KEY_TARGET, pkg == null ? "" : pkg).apply();
    }
    public static String getTarget(Context c) {
        return prefs(c).getString(KEY_TARGET, WINDOWS_APP_PACKAGE);
    }

    public static void setMode(Context c, int mode) {
        prefs(c).edit().putInt(KEY_MODE, mode).apply();
    }
    public static int getMode(Context c) {
        return prefs(c).getInt(KEY_MODE, MODE_OBSERVE);
    }

    public static void setMetaDetected(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_META_DETECTED, v).apply();
    }
    public static boolean isMetaDetected(Context c) {
        return prefs(c).getBoolean(KEY_META_DETECTED, false);
    }

    public static void setAltTabDetected(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_ALT_TAB_DETECTED, v).apply();
    }
    public static boolean isAltTabDetected(Context c) {
        return prefs(c).getBoolean(KEY_ALT_TAB_DETECTED, false);
    }

    public static void setLastInjection(Context c, String v) {
        prefs(c).edit().putString(KEY_LAST_INJECTION, v).apply();
    }
    public static String getLastInjection(Context c) {
        return prefs(c).getString(KEY_LAST_INJECTION, "NOT TESTED");
    }

    public static void setWindowsSessionActive(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_WINDOWS_SESSION_ACTIVE, v).apply();
    }
    public static boolean isWindowsSessionActive(Context c) {
        return prefs(c).getBoolean(KEY_WINDOWS_SESSION_ACTIVE, false);
    }
}
