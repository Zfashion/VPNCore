package com.base.vpn.utils;

import android.util.Log;

import com.base.vpn.BuildConfig;

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
