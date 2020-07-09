package com.base.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

public class NetWorkChangeReceiver extends BroadcastReceiver {

    private NetWorkChangeCallback mNetworkChangeCallback;

    public NetWorkChangeReceiver(@NonNull NetWorkChangeCallback mOpenVPN) {
        this.mNetworkChangeCallback = mOpenVPN;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mNetworkChangeCallback != null) {
            mNetworkChangeCallback.networkChange(checkNetConnected(context));
        }
    }

    public static NetWorkChangeReceiver register(@NonNull NetWorkChangeCallback context) {
        try {
            NetWorkChangeReceiver receiver = new NetWorkChangeReceiver(context);
            context.getContext().registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            return receiver;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void unRegister(@NonNull NetWorkChangeCallback context, @NonNull NetWorkChangeReceiver receiver) {
        try {
            context.getContext().unregisterReceiver(receiver);
        } catch (Throwable re) {
        }
    }

    boolean checkNetConnected(Context context) {
        try {
            ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connManager == null) {
                return false;
            }
            NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                    return true;
                }
                networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}