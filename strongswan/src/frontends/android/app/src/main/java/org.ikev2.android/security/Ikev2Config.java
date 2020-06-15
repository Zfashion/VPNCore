package org.ikev2.android.security;

import org.ikev2.android.data.VpnProfile;
import org.ikev2.android.data.VpnType;
import org.ikev2.android.logic.TrustedCertificateManager;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;

public final class Ikev2Config {

    private static VpnProfile mVpnProfile;

    public static VpnProfile getVpnProfile() {
        return mVpnProfile;
    }

    public static final class CA{
        String key;
        String username;
        String password;
        String remote_id;

        public CA(String key, String username, String password, String remote_id) {
            this.key = key;
            this.username = username;
            this.password = password;
            this.remote_id = remote_id;
        }
    }

    public static boolean storeRemoteCfg(CA vpnCertificateSet, int version) {
        boolean isOk = storeCertificate(vpnCertificateSet);
        if (restoreProfile(vpnCertificateSet, version)) {
            return isOk;
        }
        return false;
    }

    public static boolean storeCertificate(CA ca) {
        X509Certificate certificate = null;
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            //AppLog.w(new String(data));
            certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(ca.key.getBytes()));
            /* we don't check whether it's actually a CA certificate or not */
        } catch (CertificateException e) {
            e.printStackTrace();
        }
        if (certificate == null) return false;
        try {
            KeyStore store = KeyStore.getInstance("LocalCertificateStore");
            store.load(null, null);
            store.setCertificateEntry(null, certificate);
            TrustedCertificateManager.getInstance().reset();
            new Thread(() -> {
                TrustedCertificateManager.getInstance().load();
            }).start();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * "mUsername":"myvpn",
     * "mId":1,
     * "mRemoteId":"104.248.180.176",
     * "mPassword":"trees",
     * "mFlags":0,
     * "mName":"134.209.1.133",
     * "mPort":4580,
     * "mGateway":"134.209.1.133",
     * "mVpnType":{
     * "mIdentifier":"ikev2-eap",
     * "mFeatures":{
     * "size":1,
     * "bits":2
     * }
     * }
     */
    private static boolean restoreProfile(CA ca, int version) {
        try {
            VpnProfile profile = new VpnProfile();
            //default data
            profile.setId(1);
            profile.setFlags(0);
            profile.setVpnType(VpnType.IKEV2_EAP);
            profile.setUUID(UUID.randomUUID());
//            profile.setGateway("134.209.1.133");
//            profile.setPort(4580);
//            profile.setName("134.209.1.133");
            //remote data
            profile.setUsername(ca.username);
            profile.setPassword(ca.password);
            profile.setRemoteId(ca.remote_id);
            profile.setVersion(version);
            mVpnProfile = profile;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
