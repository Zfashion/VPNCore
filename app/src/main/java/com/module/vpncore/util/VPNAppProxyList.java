package com.module.vpncore.util;

import androidx.collection.ArraySet;

import com.base.vpn.utils.VPNLog;
import com.data.IDataStore;

import java.util.Set;

public class VPNAppProxyList {

    private static final String APP_LIST = "app_list";
    private IDataStore mDataStore;

    public VPNAppProxyList(IDataStore mDataStore) {
        this.mDataStore = mDataStore;
    }

    public void setAllowedApps(Set<String> pkgs) {
        try {
            mDataStore.putStringSet(APP_LIST, pkgs);
        } catch (Throwable e) {
            VPNLog.e("setAllowedApps : ", e);
        }
    }

    public void clearAllowedApps() {
        try {
            mDataStore.remove(APP_LIST);
        } catch (Throwable e) {
            VPNLog.e("clearAllowedApps : ", e);
        }
    }

    public Set<String> getAllowedApps() {
        try {
            return mDataStore.getStringSet(APP_LIST, new ArraySet<>());
        } catch (Throwable e) {
            VPNLog.e("getAllowedApps : ", e);
        }
        return new ArraySet<>();
    }
}
