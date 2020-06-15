package com.base.vpn;

import android.app.Service;
import android.content.Context;
import android.net.VpnService;
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

    /**
     * 监听流量
     */
    interface ByteCountListener {
        void updateByteCount(long in, long out, long diffIn, long diffOut);
    }

    //--------------------------------------

    interface AppFilter {
        void applyFilter(Context context, VpnService.Builder builder);
    }

    //--------------------------------------

    /**
     *  自定义通知样式 根据设计业务需求
     *  必须调用{@link #addNotificationManager(INotificationManager)} 否则将会抛出异常并终止程序
     */
    interface INotificationManager {

        void connecting(Service service, String title);

        void connected(Service service, String title);

        void byteCountChange(Service service, String title, long in, long out, long diffIn, long diffOut);

        void disconnect(Service service);

        void stateChange(Service service, IVPN.VPNState state);
    }

    void connect(Bundle data);

    void disconnect();

    void reconnect();

    void addCallback(VPNCallback callback);

    void removeCallback(VPNCallback callback);

    /**
     * app 过滤 (指定app代理 或 指定app不代理)
     * @param appFilter
     */
    void addAppFilter(AppFilter appFilter);

    /**
     * 自定义通知样式 根据设计业务需求
     * @param notificationManager
     */
    void addNotificationManager(INotificationManager notificationManager);

    /**
     * 检测VPN Service 是否已经destroy
     * @param clazz 目标vpn service
     * @param <T>
     * @return
     */
    default <T extends IVPNService> boolean hasDestroy(Class<T> clazz) {
        return false;
    }
}
