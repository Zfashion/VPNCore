package com.module.vpncore;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Color;
import android.os.Build;

import com.base.vpn.VPNConfig;
import com.data.DataStore;
import com.module.vpncore.ui.MainActivity;
import com.module.vpncore.util.DefaultNotificationManager;
import com.module.vpncore.vpn.VPNInstance;
import com.module.vpncore.vpn.VPNService;

import org.ikev2.android.logic.StrongSwanApplication;

public class App extends StrongSwanApplication {
    @Override
    public void onCreate() {
        super.onCreate();

        VPNConfig.ikev2VPNServiceClass = VPNService.class;
        VPNConfig.openVPNServiceClass = VPNService.class;

        VPNConfig.ikev2VPNActivityPendingClass = MainActivity.class;
        VPNConfig.openVPNActivityPendingClass = MainActivity.class;

        VPNConfig.dataStore = new DataStore.Builder(this)
//                .dataStore()//此处自定义自己的数据存储
//                .encryptor()//此处自定义加密
//                .encryptorKey()//加密需要用到的key
                .build();

        createNotificationChannels();

        VPNInstance.get().init(this);
    }

    @SuppressWarnings("ConstantConditions")
    @TargetApi(Build.VERSION_CODES.O)
    private void createNotificationChannels() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = DefaultNotificationManager.VPN_CHANNEL_ID;//自定义配置
        String channelName = DefaultNotificationManager.VPN_CHANNEL_NAME;//自定义配置
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        channel.enableLights(true);
        channel.setShowBadge(false);
        channel.setLightColor(Color.BLUE);
        channel.setDescription(getString(R.string.permanent_notification_description));
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        notificationManager.createNotificationChannel(channel);
    }
}
