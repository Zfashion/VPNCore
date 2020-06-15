package com.module.vpncore.vpn;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.base.vpn.IVPN;

public interface IVPNOperation {

    String EXTRA_PORT = "port";
    String EXTRA_IP = "ip";
    String EXTRA_OPENVPN_UDP = "udp";
    String EXTRA_SERVER_NODE_NAME = "node_name";


    int TYPE_IKEV2_VPN = 0;
    int TYPE_OPEN_VPN = 1;

    void connect(Bundle bundle);

    void disconnect();

    void onActivityResult(int requestCode, int resultCode, @Nullable Intent data);

    void addIVPNCallBack(IVPN.VPNCallback callback);

    void detach();

    void attach(Activity activity);

    boolean isConnecting();

    boolean isConnected();

    IVPN.VPNState getState();
}
