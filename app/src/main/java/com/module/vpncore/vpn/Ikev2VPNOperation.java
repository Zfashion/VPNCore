package com.module.vpncore.vpn;

import android.app.Activity;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.base.vpn.IVPN;
import com.base.vpn.utils.VPNLog;
import com.module.vpncore.R;
import com.module.vpncore.util.DefaultNotificationManager;
import com.module.vpncore.util.VPNAppFilterHelper;

import org.ikev2.android.data.VpnProfile;
import org.ikev2.android.data.VpnType;
import org.ikev2.android.logic.Ikev2VPNImpl;
import org.ikev2.android.logic.TrustedCertificateManager;
import org.ikev2.android.security.Ikev2Config;

import java.lang.ref.WeakReference;

import static android.app.Activity.RESULT_OK;

public class Ikev2VPNOperation implements IVPNOperation, IVPN.VPNCallback, IVPN.AppFilter {

    private static final String PROFILE_REQUIRES_PASSWORD = "REQUIRES_PASSWORD";
    private static final String PROFILE_NAME = "PROFILE_NAME";
    private static final int PREPARE_VPN_SERVICE = 0;

    private IVPN mVPN;
    private Context mContext;
    private boolean mNeedBindAfterHandle;
    private IVPN.VPNState mCurrentState = IVPN.VPNState.DISCONNECTED;
    private IVPN.VPNCallback mCallback;
    private String mPort;
    private Bundle mProfileInfo;
    private WeakReference<Activity> mActivity;
    private boolean mWaitingForResult;
    private String mIp;
    private String mServerNodeName;

    public Ikev2VPNOperation(@NonNull Context mContext) {
        this.mContext = mContext.getApplicationContext();
        initCert();
    }

    @Override
    public void stateChange(IVPN.VPNState state) {
        VPNLog.d("stateChange : " + state.name());
        if (state == IVPN.VPNState.CONNECT_FAIL) {
            disconnect();
            return;
        } else if (state == IVPN.VPNState.DISCONNECTED) {
            if (mVPN != null) {
                mVPN.removeCallback(this);
            }
            unbindService();
            mVPN = null;
        }
        mCurrentState = state;
        if (mCallback != null) {
            mCallback.stateChange(state);
        }
    }

    @Override
    public void message(String msg) {
        if (mCallback != null) {
            mCallback.message(msg);
        }
    }

    @Override
    public void applyFilter(Context context, VpnService.Builder builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            new VPNAppFilterHelper("ikev2_vpn").applyFilter(context,builder);
        }
    }

    private static class LoadCertificatesTask extends AsyncTask<Void, Void, TrustedCertificateManager> {
        @Override
        protected TrustedCertificateManager doInBackground(Void... params) {
            return TrustedCertificateManager.getInstance().load();
        }
    }

    private void initCert() {
        String key = "-----BEGIN CERTIFICATE-----\n" +
                "MIIDIjCCAgqgAwIBAgIIYIfwhsbd0XUwDQYJKoZIhvcNAQELBQAwLzEMMAoGA1UE\n" +
                "BhMDY29tMQ4wDAYDVQQKEwVteXZwbjEPMA0GA1UEAxMGVlBOIENBMB4XDTE5MDMx\n" +
                "MjA0NDczOFoXDTIyMDMxMTA0NDczOFowLzEMMAoGA1UEBhMDY29tMQ4wDAYDVQQK\n" +
                "EwVteXZwbjEPMA0GA1UEAxMGVlBOIENBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n" +
                "MIIBCgKCAQEAnX6Bn58VDv3I0D5lCVoy1MBr1YtU3GmP6FTtOeGzJBOXwV7ROg7g\n" +
                "QqIs0lFtSqHEpuebB1ap2NqaoD+qYXDZJ3jYAIvkhxQPT2WPTpg7wTYadOBERUMV\n" +
                "WQhnj6pAfzUq0PN8+7p/VrJu6glN5BTvADvwie4mxwUbhXyFq2bdoMIqhKFzHCGc\n" +
                "RA+HJ5rQ09R58c6bKnr6KPJiGmXLUsd5dUIpbvd6jfYAd6QePY4WIeOW9+IkTgnc\n" +
                "4VdwUWODnc5GZ1b+mAR4EMW1KeuBf2RYmGDjtaUETVLG197WxMR16F4kBzb+TS1n\n" +
                "8FP7nhXCMoJByjhuye75IVoTnCf+9dqVGwIDAQABo0IwQDAPBgNVHRMBAf8EBTAD\n" +
                "AQH/MA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUBovkspbIvf1zP79/Q1ajAij3\n" +
                "1UkwDQYJKoZIhvcNAQELBQADggEBAHVSuDzsfHctqoVJsDWlVm3ADWJYhF90mSej\n" +
                "RlgLOjgsbMgibHJQ2gUS5Gn1i+YAX/qpI0V9MHczQcQW/NJisk2evKohj5y19o7k\n" +
                "VEq/p7KxC4eq1B/MXnmtDLVCOMU5jsNwd9lnIfOPoiD0b/CmBInXd7UJ/1NySB8r\n" +
                "v8FwmKZeZdi4SFsY/9bMdoakFkfyHdydpQ4Vfs52t5aMXMUgzUI361bTdmSspISN\n" +
                "y95bo70Wm9SJqnG1uYTwV60XgPF+Rao5509WzutYQasKhj5YSZW9w85k5FdnlRtK\n" +
                "GlyJwfjY88I2rPLxbPBEB8LnRLegXfsBPMhupSFc6CHcxI82374=\n" +
                "-----END CERTIFICATE-----";
        String userName = "myvpn";
        String password = "treeup";
        String remoteId = "104.248.180.176";
        Ikev2Config.CA ca = new Ikev2Config.CA(key, userName, password, remoteId);
        Ikev2Config.storeRemoteCfg(ca, 11);
        new LoadCertificatesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void connect(Bundle bundle) {
        mPort = bundle.getString(IVPNOperation.EXTRA_PORT);
        mIp = bundle.getString(IVPNOperation.EXTRA_IP);
        mServerNodeName = bundle.getString(IVPNOperation.EXTRA_SERVER_NODE_NAME);

        if (mVPN != null && mVPN.hasDestroy(Ikev2VPNImpl.class)) {
            mVPN.removeCallback(this);
            mVPN = null;
        }
        if (mVPN == null) {
            bindVPNService("connect", true);
        } else {
            startVpnProfile();
        }
    }

    @Override
    public void disconnect() {
        if (mVPN != null) {
            unbindService();
            mVPN.disconnect();
            mVPN = null;
        } else if (mCurrentState == IVPN.VPNState.CONNECTING || mCurrentState == IVPN.VPNState.CONNECTED) {
            bindVPNService("disconnect", true);
        }
    }

    @Override
    public void addIVPNCallBack(IVPN.VPNCallback callback) {
        mCallback = callback;
    }

    @Override
    public void detach() {
        if (mActivity != null) {
            mActivity.clear();
            mActivity = null;
        }
        mCallback = null;
    }

    @Override
    public void attach(Activity activity) {
        if (mActivity == null || mActivity.get() == null) {
            mActivity = new WeakReference<>(activity);
        }
    }

    @Override
    public boolean isConnecting() {
        return mCurrentState == IVPN.VPNState.CONNECTING || mCurrentState != IVPN.VPNState.DISCONNECTED;
    }

    private void bindVPNService(String origin, boolean needBindAfterHandle) {
        Intent intent = Ikev2VPNImpl.getIntent(mContext);
        if (!TextUtils.isEmpty(origin)) {
            intent.putExtra("origin", origin);//仅用于log分析
        }
        intent.setAction(IVPN.BIND_VPN_HANDLE);
        mNeedBindAfterHandle = needBindAfterHandle;
        VPNLog.d("try bind service : " + origin);
        mContext.bindService(intent, mVpnHandleConnection, Service.BIND_AUTO_CREATE);
    }

    @Override
    public boolean isConnected() {
        return mCurrentState == IVPN.VPNState.CONNECTED;
    }

    @Override
    public IVPN.VPNState getState() {
        return mCurrentState;
    }

    private ServiceConnection mVpnHandleConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            VPNLog.d("service bind success!");
            mVPN = ((Ikev2VPNImpl.Ikev2VPNHandleBinder) service).getVPNService();
            mVPN.addCallback(Ikev2VPNOperation.this);
            /**
             *  根据实际业务设置通知管理器
             *
             *  警告 : 必须设置通知管理器 否则将会抛出异常 {@link RuntimeException}
             */
            mVPN.addNotificationManager(new DefaultNotificationManager());
            if (mNeedBindAfterHandle) {
                mNeedBindAfterHandle = false;
                if (isConnected() || isConnecting()) {
                    disconnect();
                } else {
                    startVpnProfile();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mVPN != null) {
                mVPN.removeCallback(Ikev2VPNOperation.this);
            }
            mVPN = null;
        }
    };


    private void startVpnProfile(VpnProfile profile) {
        VPNLog.d("startVpnProfile");
        Bundle profileInfo = new Bundle();
        profile.setName(mServerNodeName);
        profileInfo.putString(Ikev2VPNImpl.KEY_UUID, profile.getUUID().toString());
        profileInfo.putString(Ikev2VPNImpl.KEY_USERNAME, profile.getUsername());
        profileInfo.putString(Ikev2VPNImpl.KEY_PASSWORD, profile.getPassword());
        profileInfo.putBoolean(PROFILE_REQUIRES_PASSWORD, profile.getVpnType().has(VpnType.VpnTypeFeature.USER_PASS));
        profileInfo.putString(PROFILE_NAME, profile.getName());
        try {
            //config ip and ports
            //profile.setLocalId(mSelectedServer.getIp());
            //profile.setRemoteId(mSelectedServer.getIp());
            profile.setGateway(mIp);
            profile.setPort(Integer.parseInt(mPort));
        } catch (Exception e) {
            e.printStackTrace();
        }
        startVpnProfile(profileInfo);
    }

    private void startVpnProfile() {
        VpnProfile profile = Ikev2Config.getVpnProfile();
        if (profile != null) {
            startVpnProfile(profile);
        } else {
            Toast.makeText(mContext, R.string.error, Toast.LENGTH_LONG).show();
        }
    }

    private void startVpnProfile(Bundle profileInfo) {
        VPNLog.d("startVpnProfile");
        prepareVpnService(profileInfo);
    }

    private void prepareVpnService(Bundle profileInfo) {
        Intent intent;
        VPNLog.d("prepareVpnService mWaitingForResult:" + mWaitingForResult);
        if (mWaitingForResult) {
            mProfileInfo = profileInfo;
            return;
        }

        try {
            intent = VpnService.prepare(mContext);
        } catch (IllegalStateException ex) {
            /* this happens if the always-on VPN feature (Android 4.2+) is activated */
            //VpnProfileControlActivity.VpnNotSupportedError.showWithMessage(this, R.string.vpn_not_supported_during_lockdown);
            Toast.makeText(mContext, R.string.vpn_not_supported_during_lockdown, Toast.LENGTH_LONG).show();
            VPNLog.d("prepareVpnService error :", ex);
            return;
        } catch (NullPointerException ex) {
            /* not sure when this happens exactly, but apparently it does */
            //VpnProfileControlActivity.VpnNotSupportedError.showWithMessage(this, R.string.vpn_not_supported);
            Toast.makeText(mContext, R.string.vpn_not_supported, Toast.LENGTH_LONG).show();
            VPNLog.d("prepareVpnService error :", ex);
            return;
        }
        /* store profile info until the user grants us permission */
        mProfileInfo = profileInfo;
        if (intent != null) {
            try {
                mWaitingForResult = true;
                mActivity.get().startActivityForResult(intent, PREPARE_VPN_SERVICE);
                mActivity.clear();
                mActivity = null;
            } catch (ActivityNotFoundException ex) {
                /* it seems some devices, even though they come with Android 4,
                 * don't have the VPN components built into the system image.
                 * com.android.vpndialogs/com.android.vpndialogs.ConfirmDialog
                 * will not be found then */
                Toast.makeText(mContext, R.string.vpn_not_supported, Toast.LENGTH_LONG).show();
                //VpnProfileControlActivity.VpnNotSupportedError.showWithMessage(this, R.string.vpn_not_supported);
                mWaitingForResult = false;
                VPNLog.d("prepareVpnService error :", ex);
            }
        } else {    /* user already granted permission to use VpnService */
            onActivityResult(PREPARE_VPN_SERVICE, RESULT_OK, null);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        VPNLog.d("processActivityResult");
        mWaitingForResult = false;
        switch (requestCode) {
            case PREPARE_VPN_SERVICE:
                if (resultCode == RESULT_OK && mProfileInfo != null) {
                    VPNLog.d("prepare connect");
                    if (mVPN != null) {
                        VPNLog.d("start connect");
                        mVPN.connect(mProfileInfo);
                    }
                }
                break;
        }
    }

    private void unbindService() {
        try {
            if (mVPN != null && !mVPN.hasDestroy(Ikev2VPNImpl.class)) {
                mContext.unbindService(mVpnHandleConnection);
            }
        } catch (Throwable e) {
            VPNLog.e("unbindService : ", e);
        }
    }
}
