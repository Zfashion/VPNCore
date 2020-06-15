package com.module.vpncore.vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.base.vpn.IVPN;

public class VPNInstance implements IVPNOperation {

    private IVPNOperation mCurrentVPNOperation;
    private IVPNOperation[] mVpnOperations = new IVPNOperation[2];

    private static final class Holder {
        private final static VPNInstance INSTANCE = new VPNInstance();
    }

    public static VPNInstance get() {
        return Holder.INSTANCE;
    }

    public void setVPNType(int type) {
        if (mCurrentVPNOperation != null) {
            mCurrentVPNOperation.detach();
        }
        switch (type) {
            case IVPNOperation.TYPE_OPEN_VPN:
                mCurrentVPNOperation = mVpnOperations[0];
                break;
            case IVPNOperation.TYPE_IKEV2_VPN:
                mCurrentVPNOperation = mVpnOperations[1];
                break;
        }
    }

    public void init(Context context) {
        mVpnOperations[0] = new OpenVPNOperation(context);
        mVpnOperations[1] = new Ikev2VPNOperation(context);
    }

    @Override
    public void connect(Bundle bundle) {
        if (mCurrentVPNOperation != null) {
            mCurrentVPNOperation.connect(bundle);
        }
    }

    @Override
    public void disconnect() {
        if (mCurrentVPNOperation != null) {
            mCurrentVPNOperation.disconnect();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (mCurrentVPNOperation != null) {
            mCurrentVPNOperation.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void addIVPNCallBack(IVPN.VPNCallback callback) {
        if (mCurrentVPNOperation != null) {
            mCurrentVPNOperation.addIVPNCallBack(callback);
        }
    }

    @Override
    public void detach() {
        if (mCurrentVPNOperation != null) {
            mCurrentVPNOperation.detach();
        }
    }

    @Override
    public void attach(Activity activity) {
        if (mCurrentVPNOperation != null) {
            mCurrentVPNOperation.attach(activity);
        }
    }

    @Override
    public boolean isConnecting() {
        if (mCurrentVPNOperation != null) {
            return mCurrentVPNOperation.isConnecting();
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        if (mCurrentVPNOperation != null) {
            return mCurrentVPNOperation.isConnected();
        }
        return false;
    }

    @Override
    public IVPN.VPNState getState() {
        if (mCurrentVPNOperation != null) {
            return mCurrentVPNOperation.getState();
        }
        return IVPN.VPNState.DISCONNECTED;
    }
}
