package com.base.vpn;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;

public interface IVPNService extends IVPN {

    String VPN_TYPE = "VPN_TYPE";

    String VPN_TYPE_OPEN = "VPN_TYPE_OPEN";

    String VPN_TYPE_IKEV2 = "VPN_TYPE_IKEV2";

    Context getContext();

    //--------------------------------------

    void onDestroy();

    void onRevoke();

    void onCreate();

    int onStartCommand(Intent intent, int flags, int startId);

    IBinder onBind(Intent intent);

    //--------------------------------------

    interface VpnServiceBuilderCreator {
        VpnService.Builder create();
    }

    void addBuilderCreator(VpnServiceBuilderCreator creator);
}
