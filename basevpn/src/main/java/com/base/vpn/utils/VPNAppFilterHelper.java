package com.base.vpn;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.base.vpn.utils.VPNAppListUtil;
import com.data.DataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VPNAppFilterHelper {
    private static final String TAG = "VPNAppFilterHelper";
    private String VPN_SERVICE;

    private VPNAppFilterHelper() {
    }

    public VPNAppFilterHelper(String name) {
        this.VPN_SERVICE = name;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void applyFilter(Context context, VpnService.Builder builder) {
        VPNLog.d("applyFilter :" + VPN_SERVICE);
        Set<String> allowedApps = new VPNAppListUtil(new DataStore.Builder(context).build()).getAllowedApps(context);
        if (allowedApps != null && allowedApps.size() > 0) {
            List<String> finalList = new ArrayList<>();
            finalList.add(context.getPackageName());//包含自己
            for (String pkg : allowedApps) {
                //不包含在黑名单的包名则保存在list
                if (!AppBlackList.containerAppList(pkg)) {
                    //需要代理的app
                    finalList.add(pkg);
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, VPN_SERVICE + " -> exclude :" + pkg);
                    }
                }
            }
            for (String pkg : finalList) {
                try {
                    VPNLog.d("addAllowed: " + VPN_SERVICE + "  : " + pkg);
                    builder.addAllowedApplication(pkg);
                } catch (PackageManager.NameNotFoundException e) {
                    VPNLog.e("addAllowed :", e);
                }
            }
        } else {
            //全选状态时 需排除黑名单的包名即可
            List<String> blackList = AppBlackList.getAppList();
            for (String pkg : blackList) {
                try {
                    VPNLog.d("addDisallowed: " + VPN_SERVICE + " : " + pkg);
                    builder.addDisallowedApplication(pkg);
                } catch (PackageManager.NameNotFoundException e) {
                    VPNLog.e("addDisallowed :", e);
                }
            }
        }
    }
}
