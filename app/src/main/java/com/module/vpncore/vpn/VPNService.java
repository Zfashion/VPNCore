/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.module.vpncore.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;

import com.base.vpn.IVPN;
import com.base.vpn.IVPNService;
import com.base.vpn.utils.VPNLog;
import com.blinkt.openvpn.OpenVPNImpl;

import org.ikev2.android.logic.Ikev2VPNImpl;

import java.util.Set;

public class VPNService extends VpnService implements IVPNService.VpnServiceBuilderCreator, IVPN {

    private IVPNService mVPNImpl;
    private boolean hasDestroy;

    @Override
    public void onCreate() {
        super.onCreate();
        VPNLog.d("VPNService:onCreate " + this);
        hasDestroy = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        VPNLog.d("VPNService:onStartCommand " + this);
        IVPNService vpnService = getIVPNService(intent);
        if (vpnService != null) {
            return vpnService.onStartCommand(intent, flags, startId);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this);
        stringBuilder.append("\n");
        if (intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            Set<String> set = bundle.keySet();
            for (String key : set) {
                stringBuilder.append(key);
                stringBuilder.append(":");
                stringBuilder.append(bundle.get(key));
                stringBuilder.append("; \n");
            }
            stringBuilder.append("action:");
            stringBuilder.append(intent.getAction());
        }
        VPNLog.d("VPNService:onBind " + stringBuilder.toString());
        IVPNService vpnService = getIVPNService(intent);
        if (vpnService != null) {
            IBinder binder = vpnService.onBind(intent);
            if (binder == null) {
                return super.onBind(intent);
            } else {
                return binder;
            }
        }
        return super.onBind(intent);
    }

    @Override
    public void onRevoke() {
        super.onRevoke();
        if (mVPNImpl != null) {
            mVPNImpl.onRevoke();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hasDestroy = true;
        VPNLog.d("VPNService:onDestroy " + this);
        if (mVPNImpl != null) {
            mVPNImpl.onDestroy();
            mVPNImpl = null;
        }
    }

    @Override
    public Builder create() {
        return new Builder();
    }

    @Override
    public void addCallback(VPNCallback callback) {
        IVPNService vpnService = mVPNImpl;
        if (vpnService != null) {
            vpnService.addCallback(callback);
        }
    }

    @Override
    public void removeCallback(VPNCallback callback) {
        IVPNService vpnService = mVPNImpl;
        if (vpnService != null) {
            vpnService.removeCallback(callback);
        }
    }

    @Override
    public void addAppFilter(AppFilter appFilter) {
        IVPNService vpnService = mVPNImpl;
        if (vpnService != null) {
            vpnService.addAppFilter(appFilter);
        }
    }

    @Override
    public void addNotificationManager(INotificationManager notificationManager) {
        IVPNService vpnService = mVPNImpl;
        if (vpnService != null) {
            vpnService.addNotificationManager(notificationManager);
        }
    }

    @Override
    public <T extends IVPNService> boolean hasDestroy(Class<T> clazz) {
        if (clazz != null && mVPNImpl != null) {
            return !clazz.isInstance(mVPNImpl) || hasDestroy;
        }
        return hasDestroy;
    }

    @Override
    public void connect(Bundle data) {
        IVPNService vpnService = mVPNImpl;
        if (vpnService != null) {
            vpnService.connect(data);
        }
    }

    @Override
    public void disconnect() {
        IVPNService vpnService = mVPNImpl;
        if (vpnService != null) {
            vpnService.disconnect();
        }
    }

    @Override
    public void reconnect() {
        IVPNService vpnService = mVPNImpl;
        if (vpnService != null) {
            vpnService.reconnect();
        }
    }

    private IVPNService getIVPNService(Intent intent) {
        IVPNService vpnService = mVPNImpl;
            if (intent.hasExtra(IVPNService.VPN_TYPE)) {
                String type = intent.getStringExtra(IVPNService.VPN_TYPE);
                switch (type) {
                    case IVPNService.VPN_TYPE_OPEN:
                        if (vpnService != null && !(vpnService instanceof OpenVPNImpl)) {
                            vpnService.onDestroy();
                            vpnService = null;
                        }
                        if (vpnService == null) {
                            vpnService = new OpenVPNImpl(this);
                            vpnService.addBuilderCreator(this);
                            vpnService.onCreate();
                            mVPNImpl = vpnService;
                        }
                        break;
                    case IVPNService.VPN_TYPE_IKEV2:
                        if (vpnService != null && !(vpnService instanceof Ikev2VPNImpl)) {
                            vpnService.onDestroy();
                            vpnService = null;
                        }
                        if (vpnService == null) {
                            vpnService = new Ikev2VPNImpl(this);
                            vpnService.addBuilderCreator(this);
                            vpnService.onCreate();//初始化
                            mVPNImpl = vpnService;
                        }
                        break;
                }
            }
        return vpnService;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this);
        stringBuilder.append("\n");
        if (intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            Set<String> set = bundle.keySet();
            for (String key : set) {
                stringBuilder.append(key);
                stringBuilder.append(":");
                stringBuilder.append(bundle.get(key));
                stringBuilder.append("; \n");
            }
            stringBuilder.append("action:");
            stringBuilder.append(intent.getAction());
        }
        VPNLog.d("VPNService:onUnbind " + stringBuilder.toString());
        return super.onUnbind(intent);
    }
}
