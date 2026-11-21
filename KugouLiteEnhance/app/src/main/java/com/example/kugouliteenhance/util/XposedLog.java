package com.example.kugouliteenhance.util;

import de.robv.android.xposed.XposedBridge;

/** Logging utility that always prefixes messages with the module tag. */
public final class XposedLog {
    public static final String TAG = "KugouLiteEnhance";

    private XposedLog() {}

    public static void d(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    public static void i(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    public static void w(String msg) {
        XposedBridge.log("[" + TAG + "] WARN: " + msg);
    }

    public static void e(String msg) {
        XposedBridge.log("[" + TAG + "] ERROR: " + msg);
    }

    public static void e(String msg, Throwable t) {
        XposedBridge.log("[" + TAG + "] ERROR: " + msg + " | " + (t == null ? "" : t.toString()));
    }
}
