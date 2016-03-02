package com.crossbowffs.soundcloudadaway;

import android.util.Log;

public final class Xlog {
    private static final boolean VLOG = true;
    private static final boolean DLOG = true;
    private static final String TAG = "SoundCloudAdAway";

    private Xlog() { }

    private static void log(int priority, String tag, String message, Object... args) {
        message = String.format(message, args);
        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            Throwable throwable = (Throwable)args[args.length - 1];
            String stacktraceStr = Log.getStackTraceString(throwable);
            message = message + '\n' + stacktraceStr;
        }

        Log.println(priority, tag, message);
    }

    public static void v(String message, Object... args) {
        if (VLOG) {
            log(Log.VERBOSE, TAG, message, args);
        }
    }

    public static void d(String message, Object... args) {
        if (DLOG) {
            log(Log.DEBUG, TAG, message, args);
        }
    }

    public static void i(String message, Object... args) {
        log(Log.INFO, TAG, message, args);
    }

    public static void w(String message, Object... args) {
        log(Log.WARN, TAG, message, args);
    }

    public static void e(String message, Object... args) {
        log(Log.ERROR, TAG, message, args);
    }
}
