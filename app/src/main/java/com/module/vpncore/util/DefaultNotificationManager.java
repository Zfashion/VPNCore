package com.module.vpncore.util;

import android.app.Service;
import android.content.res.Resources;

import com.base.vpn.IVPN;
import com.base.vpn.NetTraffics;
import com.module.vpncore.R;

public class DefaultNotificationManager implements IVPN.INotificationManager {

    public static final String VPN_CHANNEL_ID = "vpn_channel_id";
    public static final String VPN_CHANNEL_NAME = "vpn_channel_name";
    private IVPN.VPNState mState;

    @Override
    public void connecting(Service service, String title) {
        VPNNotificationHelper.connecting(service, title, service.getString(R.string.vpn_connecting), VPN_CHANNEL_ID, service.hashCode());
    }

    @Override
    public void connected(Service service, String title) {
        VPNNotificationHelper.connected(service, title, service.getString(R.string.vpn_connected), VPN_CHANNEL_ID, service.hashCode());
    }

    @Override
    public void byteCountChange(Service service, String title, long in, long out, long diffIn, long diffOut) {
        if (mState == IVPN.VPNState.CONNECTED) {
            Resources resources = service.getResources();
            String netstat = String.format(service.getString(org.strongswan.android.R.string.statusline_bytecount),
                    NetTraffics.humanReadableByteCount(in, false, resources),
                    NetTraffics.humanReadableByteCount(diffIn / 2, true, resources),
                    NetTraffics.humanReadableByteCount(out, false, resources),
                    NetTraffics.humanReadableByteCount(diffOut / 2, true, resources));
            VPNNotificationHelper.connected(service, title, netstat, VPN_CHANNEL_ID, service.hashCode());
        }
    }

    @Override
    public void disconnect(Service service) {
        VPNNotificationHelper.remove(service);
    }

    @Override
    public void stateChange(Service service, IVPN.VPNState state) {
        mState = state;
    }
}
