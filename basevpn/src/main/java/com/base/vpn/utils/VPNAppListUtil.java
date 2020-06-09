package com.base.vpn.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import com.data.IDataStore;

import java.util.HashSet;
import java.util.Set;

public class VPNAppListUtil {

    private static final String APP_LIST = "app_list";
    private IDataStore mDataStore;

    public VPNAppListUtil(IDataStore mDataStore) {
        this.mDataStore = mDataStore;
    }

    public void setAllowedApps(@NonNull Context context, HashSet<String> pkgs) {
        try {
            mDataStore.putStringSet(APP_LIST, pkgs);
        } catch (Throwable e) {
            VPNLog.e("setAllowedApps : ", e);
        }
    }

    public void clearAllowedApps(@NonNull Context context) {
        try {
            mDataStore.remove(APP_LIST);
        } catch (Throwable e) {
            VPNLog.e("clearAllowedApps : ", e);
        }
    }

    public Set<String> getAllowedApps(Context context) {
        try {
            mDataStore.getStringSet(APP_LIST, new ArraySet<>());
        } catch (Throwable e) {
            VPNLog.e("getAllowedApps : ", e);
        }
        return null;
    }
}
