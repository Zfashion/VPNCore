package com.module.vpncore;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Color;
import android.os.Build;

import com.base.vpn.VPNConfig;
import com.blinkt.openvpn.OpenVPNImpl;
import com.data.DataStore;

import org.ikev2.android.logic.StrongSwanApplication;

public class App extends StrongSwanApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        VPNConfig.dataStore = new DataStore.Builder(this)
//                .dataStore()//此处自定义自己的数据存储
//                .encryptor()//此处自定义加密
//                .encryptorKey()//加密需要用到的key
                .build();

        VPNConfig.notification_connected_color_res = R.color.colorPrimary;
        VPNConfig.notification_connecting_color_res = R.color.colorAccent;
        VPNConfig.notification_small_icon_res = R.mipmap.ic_launcher;

        VPNConfig.pendingIntentClass = MainActivity.class;

        createNotificationChannels();

        VPNInstance.get().init(this);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createNotificationChannels() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String name = "openvpn_channel";
        NotificationChannel channel = new NotificationChannel(OpenVPNImpl.NOTIFICATION_CHANNEL_NEWSTATUS_ID, name, NotificationManager.IMPORTANCE_LOW);
        channel.enableLights(true);
        channel.setLightColor(Color.BLUE);
        mNotificationManager.createNotificationChannel(channel);
    }
}
