package com.module.vpncore;

import android.app.Activity;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.base.vpn.IVPN;
import com.base.vpn.utils.VPNLog;
import com.blinkt.openvpn.OpenVPNImpl;
import com.blinkt.openvpn.VpnProfile;
import com.blinkt.openvpn.core.ConfigParser;
import com.blinkt.openvpn.core.Connection;
import com.blinkt.openvpn.core.ProfileManager;
import com.blinkt.openvpn.core.VpnStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class OpenVPNOperation implements IVPNOperation, IVPN.VPNCallback, IVPN.AppFilter {

    private static final String profileString = "client\n" +
            "<connection>\n" +
            "remote 159.65.137.205  443 tcp\n" +
            "connect-timeout  5\n" +
            "connect-retry 0\n" +
            "</connection>\n" +
            "\n" +
            "<connection>\n" +
            "remote 159.65.137.205 102 tcp\n" +
            "connect-timeout  5\n" +
            "connect-retry 0\n" +
            "</connection>\n" +
            "\n" +
            "connect-retry 1\n" +
            "connect-retry-max 1\n" +
            "resolv-retry 60\n" +
            "\n" +
            "dev tun\n" +
            "nobind\n" +
            "persist-tun\n" +
            "remote-cert-tls server\n" +
            "auth SHA256\n" +
            "cipher AES-128-CBC\n" +
            "tls-client\n" +
            "tls-version-min 1.2\n" +
            "tls-cipher TLS-DHE-RSA-WITH-AES-128-GCM-SHA256\n" +
            "setenv opt block-outside-dns\n" +
            "verb 4\n" +
            "<ca>\n" +
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDKzCCAhOgAwIBAgIJAPk/Guo5AM6AMA0GCSqGSIb3DQEBCwUAMBMxETAPBgNV\n" +
            "BAMTCENoYW5nZU1lMB4XDTE3MDMzMDA3MTQzN1oXDTI3MDMyODA3MTQzN1owEzER\n" +
            "MA8GA1UEAxMIQ2hhbmdlTWUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB\n" +
            "AQDIgXnKHt04goAbUK8E++FOyaEfkyktRQVSrxKzwQ3xmPGei3x4E5aSCa47MAym\n" +
            "QcHSnDlJQGmR4OfHiGqhuwmTxi5U42W0GViSRGsEJIpkdtAHykh8FC5T4fOUzML3\n" +
            "hs8YkvraeA2W7sSUYuS/m21LIS9n4FyamewONKRgtc+H/GhDD65nlgR5n20lrdIz\n" +
            "2L9H5IZB6EjrjLuM9TGIsdalrvhWGgUBUpgf2Oh5kH4PdA2+8WY+kd3HVhmLo60e\n" +
            "FyTxKdIS/72BHAh52zH4mSV7AmgwwIsNRadyTrEFQWg7Jl0trbFIOd3pHkD8jEYe\n" +
            "gW8TUaeRdzulhVHStoV39qobAgMBAAGjgYEwfzAdBgNVHQ4EFgQUMFU6R/ZNlHvf\n" +
            "jbRg0dYO4eQ46kQwQwYDVR0jBDwwOoAUMFU6R/ZNlHvfjbRg0dYO4eQ46kShF6QV\n" +
            "MBMxETAPBgNVBAMTCENoYW5nZU1lggkA+T8a6jkAzoAwDAYDVR0TBAUwAwEB/zAL\n" +
            "BgNVHQ8EBAMCAQYwDQYJKoZIhvcNAQELBQADggEBADEkL11AUEX8WpiB0Zw5m1id\n" +
            "+QIKxnHCFml/9dDvjQyN+umXYt1alo36bqtKX+29gFSWhLdP5An//Fj1rxGEG90v\n" +
            "TFSTzZa4dj87Kw6PIZKF8Uad1aW5gr6QLK1nruoRksaJOaY/Z0YMLWtIdwTDcHo9\n" +
            "AkXK6ffCbWyb26piApixaMIdE4c7WvlTIm+b7WbcILS1YzuNxH/QVbFBJP4y0sJL\n" +
            "fBQmbtn6dp1aUmvAIig88t6wcMvkMmz9AiOdJ4xvEvRxvMzhTrjH2zO2qP6CRTwA\n" +
            "H/YIA4pisVgc8tGbdBLQ9qcEmB9knQVB3cWPjkqxWenDjRTD0vVKqiyZAlv4lqw=\n" +
            "-----END CERTIFICATE-----\n" +
            "</ca>\n" +
            "<cert>\n" +
            "Certificate:\n" +
            "    Data:\n" +
            "        Version: 3 (0x2)\n" +
            "        Serial Number: 2 (0x2)\n" +
            "    Signature Algorithm: sha256WithRSAEncryption\n" +
            "        Issuer: CN=ChangeMe\n" +
            "        Validity\n" +
            "            Not Before: Mar 30 07:15:56 2017 GMT\n" +
            "            Not After : Mar 28 07:15:56 2027 GMT\n" +
            "        Subject: CN=treeup\n" +
            "        Subject Public Key Info:\n" +
            "            Public Key Algorithm: rsaEncryption\n" +
            "                Public-Key: (2048 bit)\n" +
            "                Modulus:\n" +
            "                    00:cd:67:5f:8c:be:a1:24:8c:b7:01:5f:04:e9:d8:\n" +
            "                    af:49:eb:9d:c7:b5:be:e0:64:fa:f5:56:3c:9e:52:\n" +
            "                    cd:87:ad:6f:fb:6d:10:27:5c:96:d2:ff:d7:2f:d1:\n" +
            "                    a5:07:00:ca:d5:23:2a:84:61:3d:07:e4:fe:0c:90:\n" +
            "                    1c:b6:64:cc:ca:15:a7:c1:c2:05:fd:75:e3:16:46:\n" +
            "                    f0:b4:7b:ca:94:36:fd:14:b4:65:21:12:23:55:24:\n" +
            "                    0d:30:51:06:e8:f5:ab:6d:33:ad:0a:d5:70:2f:89:\n" +
            "                    29:45:b0:53:12:e3:53:76:88:41:50:4c:e2:8a:2a:\n" +
            "                    d0:46:1c:7e:3b:19:ff:84:4c:e4:63:fc:36:fb:28:\n" +
            "                    58:88:85:51:b0:30:51:37:b3:40:87:d0:65:6c:5b:\n" +
            "                    e5:da:cd:30:f5:37:1b:a5:a2:ee:f5:b0:e1:81:a7:\n" +
            "                    cb:f5:d2:e2:7f:40:e7:c0:65:1a:be:a5:1b:51:99:\n" +
            "                    d9:55:82:0e:4c:c7:24:a4:b4:76:25:97:6b:58:ee:\n" +
            "                    e4:88:55:89:c4:f1:df:a1:be:65:09:60:ea:cf:73:\n" +
            "                    cd:e2:d0:7e:74:89:30:65:bd:f3:aa:54:03:c6:1f:\n" +
            "                    7e:d7:a1:a2:75:45:aa:ec:1e:0e:91:b5:3f:0d:96:\n" +
            "                    4d:45:66:ad:21:89:e3:ce:a8:d7:bb:7a:b1:ff:9a:\n" +
            "                    10:13\n" +
            "                Exponent: 65537 (0x10001)\n" +
            "        X509v3 extensions:\n" +
            "            X509v3 Basic Constraints: \n" +
            "                CA:FALSE\n" +
            "            X509v3 Subject Key Identifier: \n" +
            "                D1:77:B7:A3:22:1C:A8:95:88:65:CD:E9:75:64:A2:67:B4:0D:15:EA\n" +
            "            X509v3 Authority Key Identifier: \n" +
            "                keyid:30:55:3A:47:F6:4D:94:7B:DF:8D:B4:60:D1:D6:0E:E1:E4:38:EA:44\n" +
            "                DirName:/CN=ChangeMe\n" +
            "                serial:F9:3F:1A:EA:39:00:CE:80\n" +
            "\n" +
            "            X509v3 Extended Key Usage: \n" +
            "                TLS Web Client Authentication\n" +
            "            X509v3 Key Usage: \n" +
            "                Digital Signature\n" +
            "    Signature Algorithm: sha256WithRSAEncryption\n" +
            "         1d:6c:f0:d7:d4:20:f6:d3:51:55:b4:48:d3:14:69:33:73:63:\n" +
            "         ad:09:7f:6c:26:6d:67:04:66:63:4b:0b:9a:4c:1c:a7:9e:8e:\n" +
            "         e0:14:5e:62:26:b3:05:f9:8c:a0:f9:aa:e5:b1:c1:a0:b2:5d:\n" +
            "         07:9a:01:86:50:e5:cb:87:38:07:f9:49:2b:7f:98:e0:53:9e:\n" +
            "         7a:70:59:4e:32:ce:87:e0:a5:de:9d:2e:44:35:14:d2:4c:3e:\n" +
            "         44:4e:cd:3a:c8:be:6d:74:ae:a4:60:4c:71:9b:b0:e9:5f:e9:\n" +
            "         d8:9c:5a:9e:10:0a:e7:6d:24:ef:1e:a9:52:f0:c0:6b:12:35:\n" +
            "         04:a3:24:e2:a5:1f:59:93:d7:ff:15:40:22:72:95:de:35:c9:\n" +
            "         af:50:af:45:93:ce:0a:6f:db:84:3e:3b:af:0c:4c:d4:ff:4b:\n" +
            "         df:39:d2:15:16:8e:5e:ac:e3:c0:51:32:b5:63:f1:6f:83:de:\n" +
            "         97:f6:ec:09:9a:12:bd:1a:56:c4:51:2c:6b:c5:f6:9b:5f:ca:\n" +
            "         a6:d0:36:cd:5f:78:5e:92:72:51:bd:bc:bc:c2:ce:0d:a8:ac:\n" +
            "         13:3f:f8:c8:56:2a:f9:57:dc:4a:18:cd:87:08:1c:53:13:15:\n" +
            "         e6:4c:d2:24:97:00:b8:57:ca:ee:ca:60:23:51:5d:99:87:de:\n" +
            "         c6:45:d9:60\n" +
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDNDCCAhygAwIBAgIBAjANBgkqhkiG9w0BAQsFADATMREwDwYDVQQDEwhDaGFu\n" +
            "Z2VNZTAeFw0xNzAzMzAwNzE1NTZaFw0yNzAzMjgwNzE1NTZaMBExDzANBgNVBAMT\n" +
            "BnRyZWV1cDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAM1nX4y+oSSM\n" +
            "twFfBOnYr0nrnce1vuBk+vVWPJ5SzYetb/ttECdcltL/1y/RpQcAytUjKoRhPQfk\n" +
            "/gyQHLZkzMoVp8HCBf114xZG8LR7ypQ2/RS0ZSESI1UkDTBRBuj1q20zrQrVcC+J\n" +
            "KUWwUxLjU3aIQVBM4ooq0EYcfjsZ/4RM5GP8NvsoWIiFUbAwUTezQIfQZWxb5drN\n" +
            "MPU3G6Wi7vWw4YGny/XS4n9A58BlGr6lG1GZ2VWCDkzHJKS0diWXa1ju5IhVicTx\n" +
            "36G+ZQlg6s9zzeLQfnSJMGW986pUA8YfftehonVFquweDpG1Pw2WTUVmrSGJ486o\n" +
            "17t6sf+aEBMCAwEAAaOBlDCBkTAJBgNVHRMEAjAAMB0GA1UdDgQWBBTRd7ejIhyo\n" +
            "lYhlzel1ZKJntA0V6jBDBgNVHSMEPDA6gBQwVTpH9k2Ue9+NtGDR1g7h5DjqRKEX\n" +
            "pBUwEzERMA8GA1UEAxMIQ2hhbmdlTWWCCQD5PxrqOQDOgDATBgNVHSUEDDAKBggr\n" +
            "BgEFBQcDAjALBgNVHQ8EBAMCB4AwDQYJKoZIhvcNAQELBQADggEBAB1s8NfUIPbT\n" +
            "UVW0SNMUaTNzY60Jf2wmbWcEZmNLC5pMHKeejuAUXmImswX5jKD5quWxwaCyXQea\n" +
            "AYZQ5cuHOAf5SSt/mOBTnnpwWU4yzofgpd6dLkQ1FNJMPkROzTrIvm10rqRgTHGb\n" +
            "sOlf6dicWp4QCudtJO8eqVLwwGsSNQSjJOKlH1mT1/8VQCJyld41ya9Qr0WTzgpv\n" +
            "24Q+O68MTNT/S9850hUWjl6s48BRMrVj8W+D3pf27AmaEr0aVsRRLGvF9ptfyqbQ\n" +
            "Ns1feF6SclG9vLzCzg2orBM/+MhWKvlX3EoYzYcIHFMTFeZM0iSXALhXyu7KYCNR\n" +
            "XZmH3sZF2WA=\n" +
            "-----END CERTIFICATE-----\n" +
            "</cert>\n" +
            "<key>\n" +
            "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDNZ1+MvqEkjLcB\n" +
            "XwTp2K9J653Htb7gZPr1VjyeUs2HrW/7bRAnXJbS/9cv0aUHAMrVIyqEYT0H5P4M\n" +
            "kBy2ZMzKFafBwgX9deMWRvC0e8qUNv0UtGUhEiNVJA0wUQbo9attM60K1XAviSlF\n" +
            "sFMS41N2iEFQTOKKKtBGHH47Gf+ETORj/Db7KFiIhVGwMFE3s0CH0GVsW+XazTD1\n" +
            "Nxulou71sOGBp8v10uJ/QOfAZRq+pRtRmdlVgg5MxySktHYll2tY7uSIVYnE8d+h\n" +
            "vmUJYOrPc83i0H50iTBlvfOqVAPGH37XoaJ1RarsHg6RtT8Nlk1FZq0hiePOqNe7\n" +
            "erH/mhATAgMBAAECggEAdNXeFdLBGmcoGZGQ2+szGdr57oVEw6Ls1Oxuoqf63Lgc\n" +
            "wGkzDRCvgemg30RimG8s8LuGDbK54mmw0DiQ/Hatvi0/NQlGGvwZZayIckEP4+q7\n" +
            "XjGWbI9CpcVR8y/DvHMxRXZlcYoivAdLAKbhOhcwfHXYoPJ60Zi0y4ydiMSrAPcu\n" +
            "+y/b448kep+F/Z6hpLH+5ga/DiHbsR3xhr+JYjre1cM0pl1nPHIUKpqO7izdpZcy\n" +
            "lOu6V3ZuO9I4uwz4xb4W/pFC4geNRL+BzHCKx3h58zD2r43MWel95aW/vh08RSEx\n" +
            "5WkSaecKXsLr63/4FkOKF3YfmstSGigLZlriFsHZcQKBgQDlqYXsm/xAfqcKOlDp\n" +
            "2DIkN9Il+gB60QWImSG3I0Ns2jS9+jZ/L8koAfnWlSQbqQ1e+4LCRlXGmH4CSM+y\n" +
            "c6BCJLk1m/lJHP6H8hSaDKIjfsN4KHKwXy6UzoJH/KoovNTa29N/xX2K9huXerId\n" +
            "9UV5XiCH8LrFUkmZv2fqe1nezwKBgQDk9aqVq0hVAhuyPUufxapDKul8xA7nVHwx\n" +
            "8tdoQquJMKnYR6iVppAaoI6So/EMXNQZJ3kCaG3Z8vCjXBaDNMSuW1aDSw7Hv3kB\n" +
            "GnYkbVxVJ7ZL6vvWIkeBEzfXxHvXiA1gg9APPtTv+Tb0TkR3wkxZCkg5wkQsXIU4\n" +
            "sa9BTdyrfQKBgQCekCym8At5e/hYV2sGCP6VgvTUw4cRRL9NUGy2xOIIhZ9kixyV\n" +
            "M6jutm6IePA1KMLSkVP2ThlqxF47tYmw66P6BuDY4pd6o0oZEkqnEZHgb+UFUOfe\n" +
            "XdkLZIkOqqPQ/I75jEy6KuBC0Si7rTrM9ErDQPm04cAR/H5UaJKWkhO6gQKBgQDQ\n" +
            "YeLqq2R+sheBBpaQiLeowCKXgl1KH7OVRj7UznEOwLKkfLur0FexVFXOktUtekMz\n" +
            "zaAuF9t7FMf89jArJFipk8nOXv7Jv7Oi1HGYP8xcWHNq7yhbwQExMcuOXm6UQGhk\n" +
            "YjN33Kiy7DAe9CkOklEobNpFb1Dayy4Y5mbqWbIwhQKBgBL9CdH+Za01PzYCIcjS\n" +
            "zSeCioeLJRjxOSDHJzBPsoyEwFu7GKHgq70YZTygbOyNT4zMj1foPmvXHUaelClL\n" +
            "Mh74IzHP4t++pnObX1Kq/jYyCg4qFWIEWzzQLCrpQjPyCMnGT5I1TKl5EjJGiFam\n" +
            "iQK5dTnrBOL8H83f1Lot4z52\n" +
            "-----END PRIVATE KEY-----\n" +
            "</key>\n" +
            "key-direction 1\n" +
            "<tls-auth>\n" +
            "#\n" +
            "# 2048 bit OpenVPN static key\n" +
            "#\n" +
            "-----BEGIN OpenVPN Static key V1-----\n" +
            "e94c539a0ea5f1f3c4edbc8c998fe76a\n" +
            "637ca7aa8ecf3401ec6d5609e22331bf\n" +
            "73b04de83598c510f631132db25fd6d6\n" +
            "3648665729c818e226ab77e01e7d0140\n" +
            "8252abfbd66ee2e8bc08e60f258f08bd\n" +
            "7f367e3e3b08059d3f8924538e7ee396\n" +
            "e69017697e86c7e7dacd7cd57133acb6\n" +
            "c3533b94075b69a52ebe0120319ed34a\n" +
            "e19073d4c690a4678bebfc3a3cec527c\n" +
            "2f7ca9acdfd43952856226b6dfca004f\n" +
            "80ac279a88131fc5c5e070f14dfda8b8\n" +
            "6fe490c68d70b22cc6c6821226959123\n" +
            "8f8394768bd3c039e826f55df08c893c\n" +
            "6038be66e0599e61456684602bb11f8a\n" +
            "9f8827bf4409421d9fd44ae80064fa03\n" +
            "592381d6333a49662ccf7518e4bcdbb8\n" +
            "-----END OpenVPN Static key V1-----\n" +
            "</tls-auth>\n";

    private int START_VPN_PROFILE = 123;
    private final Context mContext;
    private VpnProfile mSelectedProfile;
    private IVPN mVPN;
    private boolean mNeedBindAfterHandle;
    private IVPN.VPNState mCurrentState = IVPN.VPNState.DISCONNECTED;
    private IVPN.VPNCallback mCallback;
    private String mPort;
    private String mServerNodeName;
    private WeakReference<Activity> mActivity;

    public OpenVPNOperation(@NonNull Context context) {
        mContext = context.getApplicationContext();
        //设定VPNService承载类
        OpenVPNImpl.vpnServiceClass = VPNService.class;
        initProfile();
    }

    @Override
    public void connect(Bundle bundle) {
        String port = bundle.getString(IVPNOperation.EXTRA_PORT);
        String ip = bundle.getString(IVPNOperation.EXTRA_IP);
        mServerNodeName = bundle.getString(IVPNOperation.EXTRA_SERVER_NODE_NAME);
        boolean udp = bundle.getBoolean(IVPNOperation.EXTRA_OPENVPN_UDP);
        int len = mSelectedProfile.mConnections.length;
        Connection connection = mSelectedProfile.mConnections[0];
        if (len > 1) {
            mSelectedProfile.mConnections = new Connection[]{connection};
        }
        //设置服务器IP,端口,TCP/UDP
        connection.mServerName = ip;
        connection.mServerPort = mPort = port;
        connection.mUseUdp = udp;
        startConnect();
    }

    @Override
    public void disconnect() {
        if (mVPN != null) {
            unbindService();
            mVPN.disconnect();
            mVPN = null;
        } else {
            bindVPNService("disconnect", true);
        }
    }

    private void initProfile() {
        mSelectedProfile = doImport(new ByteArrayInputStream(profileString.getBytes()));
    }


    private static VpnProfile doImport(InputStream is) {
        ConfigParser cp = new ConfigParser();
        VpnProfile vpnProfile;
        try {
            InputStreamReader isr = new InputStreamReader(is);
            cp.parseConfig(isr);
            vpnProfile = cp.convertProfile();
            isr.close();
            is.close();
            return vpnProfile;

        } catch (IOException | ConfigParser.ConfigParseError e) {
            e.printStackTrace();
        }
        return null;

    }

    private void bindVPNService(String origin, boolean needBindAfterHandle) {
        Intent intent = OpenVPNImpl.getIntent(mContext);
        if (!TextUtils.isEmpty(origin)) {
            intent.putExtra("origin", origin);//仅用于log分析
        }
        intent.setAction(IVPN.BIND_VPN_HANDLE);
        mNeedBindAfterHandle = needBindAfterHandle;
        mContext.bindService(intent, mVpnHandleConnection, Service.BIND_AUTO_CREATE);
    }

    private ProfileManager getProfileManager() {
        return ProfileManager.getInstance(mContext);
    }

    private ServiceConnection mVpnHandleConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mVPN = ((OpenVPNImpl.OpenVPNHandleBinder) service).getVPNService();
            mVPN.addCallback(OpenVPNOperation.this);
            mVPN.addAppFilter(OpenVPNOperation.this);
            if (mNeedBindAfterHandle) {
                mNeedBindAfterHandle = false;
                if (isConnected() || isConnecting()) {
                    disconnect();
                } else {
                    startConnect();
                }
            }
            VPNLog.d("onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mVPN != null) {
                mVPN.removeCallback(OpenVPNOperation.this);
            }
            mVPN = null;
            VPNLog.d("onServiceDisconnected");
        }
    };

    @Override
    public boolean isConnected() {
        return mCurrentState == IVPN.VPNState.CONNECTED;
    }

    @Override
    public IVPN.VPNState getState() {
        return mCurrentState;
    }

    private void startConnect() {
        Intent intent = VpnService.prepare(mContext);
        if (intent != null) {
            //VpnStatus.updateStateString("USER_VPN_PERMISSION", "", R.string.state_user_vpn_permission, ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);
            VPNLog.d("USER_VPN_PERMISSION");
            // Start the query
            try {
                mActivity.get().startActivityForResult(intent, START_VPN_PROFILE);
                mActivity.clear();
                mActivity = null;
            } catch (ActivityNotFoundException ane) {
                // Shame on you Sony! At least one user reported that
                // an official Sony Xperia Arc S image triggers this exception
                VpnStatus.logError(R.string.no_vpn_support_image);
                VPNLog.d("Phone does not support VPN!");
            }
        } else {
            onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == START_VPN_PROFILE) {
            if (resultCode == Activity.RESULT_OK) {
                if (mVPN == null || mVPN.hasDestroy(OpenVPNImpl.class)) {
                    bindVPNService("connect", true);
                } else {
                    getProfileManager().addProfile(mSelectedProfile);//添加profile到内存map
                    getProfileManager().saveProfileList(mContext);//保存 [uuid,,,,] 列表
                    getProfileManager().saveProfile(mContext, mSelectedProfile);//保存文件到本地 { uuid.vp }
                    VPNLog.d("try connect port : " + mPort);
                    Bundle bundle = mSelectedProfile.getConnectData(mContext);
                    //节点名称
                    bundle.putString(OpenVPNImpl.SERVER_NODE_COUNTRY_NAME, mServerNodeName);
                    mVPN.connect(bundle);
                }
            }
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

    @Override
    public void stateChange(IVPN.VPNState state) {
        VPNLog.d("VPNState : " + state.name());
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
        // TODO: 2020-06-15 二选一
//        builder.addDisallowedApplication()
//        builder.addAllowedApplication()
    }

    private void unbindService() {
        try {
            if (mVPN != null && !mVPN.hasDestroy(OpenVPNImpl.class)) {
                mContext.unbindService(mVpnHandleConnection);
            }
        } catch (Throwable e) {
            VPNLog.e("unbindService : ", e);
        }
    }
}
