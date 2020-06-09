package com.base.vpn.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.base.vpn.VPNConfig;

public class VPNNotificationHelper {

    public static void connecting(@NonNull Service service, String title, String content, String chanel, int id) {
        NotificationCompat.Builder builder = getBuilder(service, chanel);
        builder.setContentTitle(title);
        builder.setContentText(content);
        builder.setColor(ContextCompat.getColor(service, VPNConfig.notification_connecting_color_res));
        service.startForeground(id, builder.build());
    }

    public static void connected(@NonNull Service service, String title, String content, String chanel, int id) {
        NotificationManager manager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = getBuilder(service, chanel);
        builder.setContentTitle(title);
        builder.setContentText(content);
        builder.setColor(ContextCompat.getColor(service, VPNConfig.notification_connected_color_res));
        //noinspection ConstantConditions
        manager.notify(id, builder.build());
    }

    public static void remove(@NonNull Service service) {
        service.stopForeground(true);
    }

    private static NotificationCompat.Builder getBuilder(Context context, String chanel) {
        ApplicationInfo info = context.getApplicationInfo();
        Intent intent = new Intent(context, VPNConfig.pendingIntentClass);
        PendingIntent pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(context, chanel)
                .setSmallIcon(VPNConfig.notification_small_icon_res)
                .setContentIntent(pending)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), info.icon))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    }
}
