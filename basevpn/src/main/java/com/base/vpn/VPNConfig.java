package com.base.vpn;

import com.data.IDataStore;

public class VPNConfig {
    //VPNService 承载类
    public static Class ikev2VPNServiceClass;
    public static Class openVPNServiceClass;
    //VpnService Activity pendingIntent
    public static Class ikev2VPNActivityPendingClass;
    public static Class openVPNActivityPendingClass;
    //数据存储 主要是open vpn在用
    public static IDataStore dataStore;
}
