/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.blinkt.openvpn.core;

import android.app.Activity;
import android.content.Context;

import com.blinkt.openvpn.VpnProfile;
import com.sino.app.anyvpn.localdata.LocalData;
import com.sino.app.anyvpn.localdata.MMKVLocalData;
import com.common.utils.ObjectSerailizeUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ProfileManager {
    private static final String PREFS_NAME = "VPNList";

    private static final String LAST_CONNECTED_PROFILE = "lastConnectedProfile";
    private static final String TEMPORARY_PROFILE_FILENAME = "temporary-vpn-profile";
    private static ProfileManager instance;

    private static VpnProfile mLastConnectedVpn = null;
    private HashMap<String, VpnProfile> profiles = new HashMap<>();
    private static VpnProfile tmpprofile = null;


    private static VpnProfile get(String key) {
        if (tmpprofile != null && tmpprofile.getUUIDString().equals(key))
            return tmpprofile;

        if (instance == null)
            return null;
        return instance.profiles.get(key);

    }


    private ProfileManager() {
    }

    private static void checkInstance(Context context) {
        if (instance == null) {
            instance = new ProfileManager();
            instance.loadVPNList(context);
        }
    }

    synchronized public static ProfileManager getInstance(Context context) {
        checkInstance(context);
        return instance;
    }

    public static void setConntectedVpnProfileDisconnected(Context c) {
        LocalData.Editor edit = MMKVLocalData.getDefaultInstance(c,false).edit();
        edit.putString(LAST_CONNECTED_PROFILE, null);
        edit.apply();

    }

    /**
     * Sets the profile that is connected (to connect if the service restarts)
     */
    public static void setConnectedVpnProfile(Context c, VpnProfile connectedProfile) {
        LocalData localData = MMKVLocalData.getDefaultInstance(c,false);
        LocalData.Editor edit = localData.edit();

        edit.putString(LAST_CONNECTED_PROFILE, connectedProfile.getUUIDString());
        edit.apply();
        mLastConnectedVpn = connectedProfile;

    }

    /**
     * Returns the profile that was last connected (to connect if the service restarts)
     */
    public static VpnProfile getLastConnectedProfile(Context c) {
        String lastConnectedProfile = MMKVLocalData.getDefaultInstance(c,false).getString(LAST_CONNECTED_PROFILE, null);
        if (lastConnectedProfile != null)
            return get(c, lastConnectedProfile);
        else
            return null;
    }


    public Collection<VpnProfile> getProfiles() {
        return profiles.values();
    }

    public VpnProfile getProfileByName(String name) {
        for (VpnProfile vpnp : profiles.values()) {
            if (vpnp.getName().equals(name)) {
                return vpnp;
            }
        }
        return null;
    }

    public void saveProfileList(Context context) {
        LocalData localData = MMKVLocalData.getInstance(context,PREFS_NAME, true);
        LocalData.Editor editor = localData.edit();
        editor.putStringSet("vpnlist", profiles.keySet());

        // For reasing I do not understand at all
        // Android saves my prefs file only one time
        // if I remove the debug code below :(
        int counter = localData.getInt("counter", 0);
        editor.putInt("counter", counter + 1);
        editor.apply();

    }

    public void addProfile(VpnProfile profile) {
        profiles.put(profile.getUUID().toString(), profile);

    }

    public static void setTemporaryProfile(Context c, VpnProfile tmp) {
        tmp.mTemporaryProfile = true;
        ProfileManager.tmpprofile = tmp;
        saveProfile(c, tmp, true, true);
    }

    public static boolean isTempProfile() {
        return mLastConnectedVpn != null && mLastConnectedVpn  == tmpprofile;
    }

    public void saveProfile(Context context, VpnProfile profile) {
        saveProfile(context, profile, true, false);
    }

    private static void saveProfile(Context context, VpnProfile profile, boolean updateVersion, boolean isTemporary) {

        if (updateVersion){
            profile.mVersion += 1;
        }

        String filename = profile.getUUID().toString() + ".vp";
        if (isTemporary){
            filename = TEMPORARY_PROFILE_FILENAME + ".vp";
        }

        try {
            profile.setConnections();
            ObjectSerailizeUtils.writeObject(context,context.openFileOutput(filename, Activity.MODE_PRIVATE),profile,true);
        } catch (Exception e) {
            VpnStatus.logException("saving VPN profile", e);
            throw new RuntimeException(e);
        }
    }
    public static boolean isVpnProfileExist(Context context){
        try {
            LocalData localData = MMKVLocalData.getInstance(context,PREFS_NAME, true);
            Set<String> vlist = localData.getStringSet("vpnlist", null);
            if (vlist != null && vlist.size() >= 1) {
                for( String vpnentry : vlist) {
                    try {
                        FileInputStream in = context.openFileInput(vpnentry + ".vp");
                        in.close();
                        return true;
                    }catch (Exception e){

                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    public void loadVPNList(Context context) {
        profiles = new HashMap<>();
        LocalData localData = MMKVLocalData.getInstance(context,PREFS_NAME, true);
        Set<String> vlist = localData.getStringSet("vpnlist", null);
        if (vlist == null) {
            vlist = new HashSet<>();
        }
        // Always try to load the temporary profile
        vlist.add(TEMPORARY_PROFILE_FILENAME);

        for (String vpnentry : vlist) {
            ObjectInputStream vpnfile=null;
            try {/*
                vpnfile = new ObjectInputStream(context.openFileInput(vpnentry + ".vp"));
                VpnProfile vp = ((VpnProfile) vpnfile.readObject());*/

                VpnProfile vp = (VpnProfile)ObjectSerailizeUtils.readObject(context,context.openFileInput(vpnentry + ".vp"),true);
                vp.setmUuid();
                vp.resetConnections();
                // Sanity check
                if (vp == null || vp.mName == null || vp.getUUID() == null)
                    continue;

                vp.upgradeProfile();
                if (vpnentry.equals(TEMPORARY_PROFILE_FILENAME)) {
                    tmpprofile = vp;
                } else {
                    profiles.put(vp.getUUID().toString(), vp);
                }


            } catch (Exception e) {
                if (!vpnentry.equals(TEMPORARY_PROFILE_FILENAME))
                    VpnStatus.logException("Loading VPN List", e);
            } finally {
                if (vpnfile!=null) {
                    try {
                        vpnfile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    public void removeProfile(Context context, VpnProfile profile) {
        String vpnentry = profile.getUUID().toString();
        profiles.remove(vpnentry);
        saveProfileList(context);
        context.deleteFile(vpnentry + ".vp");
        if (mLastConnectedVpn == profile)
            mLastConnectedVpn = null;

    }

    public static VpnProfile get(Context context, String profileUUID) {
        return get(context, profileUUID, 0, 10);
    }

    public static VpnProfile get(Context context, String profileUUID, int version, int tries) {
        checkInstance(context);
        VpnProfile profile = get(profileUUID);
        int tried = 0;
        while ((profile == null || profile.mVersion < version) && (tried++ < tries)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            instance.loadVPNList(context);
            profile = get(profileUUID);
            int ver = profile == null ? -1 : profile.mVersion;
        }

        if (tried > 5)

        {
            int ver = profile == null ? -1 : profile.mVersion;
            VpnStatus.logError(String.format(Locale.US, "Used x %d tries to get current version (%d/%d) of the profile", tried, ver, version));
        }
        return profile;
    }

    public static VpnProfile getLastConnectedVpn() {
        return mLastConnectedVpn;
    }

    public static VpnProfile getAlwaysOnVPN(Context context) {
        checkInstance(context);
        String uuid = MMKVLocalData.getDefaultInstance(context,false).getString("alwaysOnVpn", null);
        return get(uuid);

    }

    public static void updateLRU(Context c, VpnProfile profile) {
        profile.mLastUsed = System.currentTimeMillis();
        // LRU does not change the profile, no need for the service to refresh
        if (profile != tmpprofile) {
            saveProfile(c, profile, false, false);
        }
    }
}
