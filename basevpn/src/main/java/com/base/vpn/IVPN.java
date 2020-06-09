package com.base.vpn;

import android.os.Bundle;

public interface IVPN {

    String BIND_VPN_HANDLE = "BIND_VPN_HANDLE";

    enum VPNState {
        CONNECTING,//正在连接 但未完全成功
        CONNECTED,//已连接成功
        DISCONNECTED,//已断开连接
        RECONNECT,//重试重连
        CONNECT_FAIL//连接失败 有可能触发换端口重连
    }

    interface VPNCallback {

        void stateChange(VPNState state);

        void message(String msg);
    }

    //--------------------------------------

    interface ByteCountListener {
        void updateByteCount(long in, long out, long diffIn, long diffOut);
    }

    //--------------------------------------

    void connect(Bundle data);

    void disconnect();

    void reconnect();

    void addCallback(VPNCallback callback);

    void removeCallback(VPNCallback callback);

    default <T extends IVPNService> boolean hasDestroy(Class<T> clazz) {
        return false;
    }
}
