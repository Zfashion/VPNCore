package com.base.vpn;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import com.data.DataStore;
import com.data.IDataStore;

import java.util.HashSet;
import java.util.Set;

public class VPNAppListUtil {

    private static final String APP_LIST = "app_list";

    public static void setAllowedApps(@NonNull Context context, HashSet<String> pkgs) {
        try {
            IDataStore dataStore = new DataStore.Builder(context).build();
            dataStore.putStringSet(APP_LIST, pkgs);
        } catch (Throwable e) {
            VPNLog.e("setAllowedApps : ", e);
        }
    }

    public static void clearAllowedApps(@NonNull Context context) {
        try {
            IDataStore dataStore = new DataStore.Builder(context).build();
            dataStore.remove(APP_LIST);
        } catch (Throwable e) {
            VPNLog.e("clearAllowedApps : ", e);
        }
    }

    public static Set<String> getAllowedApps(Context context) {
        try {
            IDataStore dataStore = new DataStore.Builder(context).build();
            return dataStore.getStringSet(APP_LIST, new ArraySet<>());
        } catch (Throwable e) {
            VPNLog.e("getAllowedApps : ", e);
        }
        return null;
    }
}
