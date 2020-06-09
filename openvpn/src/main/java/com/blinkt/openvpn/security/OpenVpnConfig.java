package com.blinkt.openvpn.security;

import android.content.Context;

import androidx.annotation.NonNull;

import com.blinkt.openvpn.VpnProfile;
import com.blinkt.openvpn.core.ConfigParser;
import com.blinkt.openvpn.core.ProfileManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class OpenVpnConfig {


    private static VpnProfile doImport(InputStream is) {
        ConfigParser cp = new ConfigParser();
        VpnProfile vpnProfile;
        try {
            InputStreamReader isr = new InputStreamReader(is);
            cp.parseConfig(isr);
            vpnProfile = cp.convertProfile();
            return vpnProfile;

        } catch (IOException | ConfigParser.ConfigParseError e) {
            e.printStackTrace();
        }
        return null;

    }

    public static void storeAndImportVpnConfig(Context context, @NonNull String profileStr, int version) {
        try {
            if (!ProfileManager.isVpnProfileExist(context)) {
                VpnProfile profile = doImport(new ByteArrayInputStream(profileStr.getBytes()));
                profile.mVersion = version;
                ProfileManager.getInstance(context).addProfile(profile);
                ProfileManager.getInstance(context).saveProfileList(context);
                ProfileManager.getInstance(context).saveProfile(context, profile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
