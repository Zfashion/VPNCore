package com.base.vpn;

import android.util.Log;

public class VPNLog {

    public static final boolean log = BuildConfig.TEST_MODE;
    private static final String TAG = "VPNLog";

    public static void d(String msg) {
        d(msg, null);
    }

    public static void d(String msg, Throwable throwable) {
        if (log) {
            Log.d(TAG, msg, throwable);
        }
    }

    public static void e(String msg) {
        e(msg, null);
    }

    public static void e(String msg, Throwable throwable) {
        if (log) {
            Log.e(TAG, msg, throwable);
        }
    }
}
