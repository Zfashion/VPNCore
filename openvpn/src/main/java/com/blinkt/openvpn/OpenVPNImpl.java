package com.blinkt.openvpn;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.base.vpn.IVPN;
import com.base.vpn.IVPNService;
import com.base.vpn.VPNConfig;
import com.blinkt.openvpn.core.CIDRIP;
import com.blinkt.openvpn.core.Connection;
import com.blinkt.openvpn.core.ConnectionStatus;
import com.blinkt.openvpn.core.DeviceStateReceiver;
import com.blinkt.openvpn.core.NetworkSpace;
import com.blinkt.openvpn.core.NetworkUtils;
import com.blinkt.openvpn.core.OpenVPNManagement;
import com.blinkt.openvpn.core.OpenVPNThread;
import com.blinkt.openvpn.core.OpenVpnManagementThread;
import com.blinkt.openvpn.core.ProfileManager;
import com.blinkt.openvpn.core.VPNLaunchHelper;
import com.blinkt.openvpn.core.VpnStatus;
import com.proguard.annotation.NotProguard;
import com.protocol.openvpn.R;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.Vector;

import static com.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTED;
import static com.blinkt.openvpn.core.ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT;

public class OpenVPNImpl implements IVPNService, VpnStatus.StateListener, Handler.Callback, IVPN.ByteCountListener {

    public static final String START_SERVICE = "com.blinkt.openvpn.START_SERVICE";
    public static final String START_SERVICE_STICKY = "com.blinkt.openvpn.START_SERVICE_STICKY";
    public static final String ALWAYS_SHOW_NOTIFICATION = "com.blinkt.openvpn.NOTIFICATION_ALWAYS_VISIBLE";
    public static final String DISCONNECT_VPN = "com.blinkt.openvpn.DISCONNECT_VPN";
    public static final String NOTIFICATION_CHANNEL_BG_ID = "openvpn_bg";
    public static final String NOTIFICATION_CHANNEL_NEWSTATUS_ID = "openvpn_newstat";
    public static final String NOTIFICATION_CHANNEL_USERREQ_ID = "openvpn_userreq";
    public static final int NOTIFICATION_ID = 333;

    public static final String SERVER_NODE_COUNTRY_NAME = "SERVER_NODE_COUNTRY_NAME";
    public static final String VPNSERVICE_TUN = "vpnservice-tun";
    public final static String ORBOT_PACKAGE_NAME = "org.torproject.android";
    private static final String PAUSE_VPN = "com.blinkt.openvpn.PAUSE_VPN";
    private static final String RESUME_VPN = "com.blinkt.openvpn.RESUME_VPN";
    private static final int PRIORITY_MIN = -2;
    private static final int PRIORITY_DEFAULT = 0;
    private static final int PRIORITY_MAX = 2;

    private static boolean mNotificationAlwaysVisible = false;
    private final Vector<String> mDnslist = new Vector<>();
    private final NetworkSpace mRoutes = new NetworkSpace();
    private final NetworkSpace mRoutesv6 = new NetworkSpace();
    private final Object mProcessLock = new Object();
    private String lastChannel;
    private Thread mProcessThread = null;
    private VpnProfile mProfile;
    private String mDomain = null;
    private CIDRIP mLocalIP = null;
    private int mMtu;
    private String mLocalIPv6 = null;
    private DeviceStateReceiver mDeviceStateReceiver;
    private boolean mDisplayBytecount = false;
    private boolean mStarting = false;
    private long mConnecttime;

    private String mLastTunCfg;
    private String mRemoteGW;
    private Handler guiHandler;
    private Toast mlastToast;
    private Runnable mOpenVPNThread;
    private OpenVPNManagement mManagement;

    private VpnService mVpnService;
    private Context mContext;
    private Vector<VPNCallback> mCallbacks;
    private VpnServiceBuilderCreator mBuilderCreator;
    private String mServerNodeName;
    private IVPN.AppFilter mAppFilter;
    private INotificationManager mNotificationManager;

    public OpenVPNImpl(@NonNull VpnService mVpnService) {
        this.mVpnService = mVpnService;
        this.mContext = mVpnService.getApplicationContext();
        mCallbacks = new Vector<>();
    }

    private OpenVPNHandleBinder mHandleBinder = new OpenVPNHandleBinder();

    public final class OpenVPNHandleBinder extends Binder {
        public IVPN getVPNService() {
            return (IVPN) mVpnService;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(ALWAYS_SHOW_NOTIFICATION, false)) {
            mNotificationAlwaysVisible = true;
        }
        if (intent != null && intent.hasExtra(SERVER_NODE_COUNTRY_NAME)) {
            mServerNodeName = intent.getStringExtra(SERVER_NODE_COUNTRY_NAME);
        } else {
            mServerNodeName = mContext.getString(mContext.getApplicationInfo().labelRes);
        }
        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);

        guiHandler = new Handler(mVpnService.getMainLooper());

        if (intent != null && START_SERVICE.equals(intent.getAction())) {
            return Service.START_NOT_STICKY;
        }

        // Always show notification here to avoid problem with startForeground timeout
        VpnStatus.logInfo(R.string.building_configration);
        VpnStatus.updateStateString("VPN_GENERATE_CONFIG", "", R.string.building_configration, ConnectionStatus.LEVEL_START);
        if (mNotificationManager != null) {
            mNotificationManager.connecting(mVpnService,mServerNodeName);
        }
        if (intent != null && intent.hasExtra(mContext.getPackageName() + ".profileUUID")) {
            String profileUUID = intent.getStringExtra(mContext.getPackageName() + ".profileUUID");
            int profileVersion = intent.getIntExtra(mContext.getPackageName() + ".profileVersion", 0);
            // Try for 10s to get current version of the profile
            ProfileManager.getInstance(mContext).loadVPNList(mContext);
            mProfile = ProfileManager.get(mContext, profileUUID, profileVersion, 100);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                updateShortCutUsage(mProfile);
            }
        } else {
            /* The intent is null when we are set as always-on or the service has been restarted. */
            mProfile = ProfileManager.getLastConnectedProfile(mContext);
            VpnStatus.logInfo(R.string.service_restarted);
            /* Got no profile, just stop */
            if (mProfile == null) {
                Log.d("OpenVPN", "Got no last connected profile on null intent. Assuming always on.");
                mProfile = ProfileManager.getAlwaysOnVPN(mContext);

                if (mProfile == null) {
                    mVpnService.stopSelf(startId);
                    return Service.START_NOT_STICKY;
                }
            }
            /* Do the asynchronous keychain certificate stuff */
            mProfile.checkForRestart(mContext);
        }

        if (mProfile == null) {
            mVpnService.stopSelf(startId);
            return Service.START_NOT_STICKY;
        }

        /* start the OpenVPN process itself in a background thread */
        new Thread(this::startOpenVPN).start();
        ProfileManager.setConnectedVpnProfile(mContext, mProfile);
        VpnStatus.setConnectedVPNProfile(mProfile.getUUIDString());

        return Service.START_STICKY;
    }

    // From: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    public static String humanReadableByteCount(long bytes, boolean speed, Resources res) {
        if (speed) {
            bytes = bytes * 8;
        }
        int unit = speed ? 1000 : 1024;
        int exp = Math.max(0, Math.min((int) (Math.log(bytes) / Math.log(unit)), 3));
        float bytesUnit = (float) (bytes / Math.pow(unit, exp));
        if (speed)
            switch (exp) {
                case 0:
                    return res.getString(R.string.bits_per_second, bytesUnit);
                case 1:
                    return res.getString(R.string.kbits_per_second, bytesUnit);
                case 2:
                    return res.getString(R.string.mbits_per_second, bytesUnit);
                default:
                    return res.getString(R.string.gbits_per_second, bytesUnit);
            }
        else
            switch (exp) {
                case 0:
                    return res.getString(R.string.volume_byte, bytesUnit);
                case 1:
                    return res.getString(R.string.volume_kbyte, bytesUnit);
                case 2:
                    return res.getString(R.string.volume_mbyte, bytesUnit);
                default:
                    return res.getString(R.string.volume_gbyte, bytesUnit);
            }

    }

    @Override
    public void connect(Bundle data) {
        checkNotificationManager();
        Intent startVPN = getIntent(mContext);
        startVPN.putExtras(data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mContext.startForegroundService(startVPN);
        } else {
            mContext.startService(startVPN);
        }
    }

    @Override
    public void disconnect() {
        try {
            stopVPN(false);
        } catch (Throwable e) {
        }
    }

    @Override
    public void reconnect() {

    }

    @Override
    public void addCallback(VPNCallback callback) {
        if (callback != null && !mCallbacks.contains(callback)) {
            mCallbacks.add(callback);
        }
    }

    @Override
    public void removeCallback(VPNCallback callback) {
        if (callback != null && mCallbacks.indexOf(callback) != -1) {
            mCallbacks.remove(callback);
        }
    }

    @Override
    public void addAppFilter(AppFilter appFilter) {
        mAppFilter = appFilter;
    }

    @Override
    public void addNotificationManager(INotificationManager notificationManager) {
        this.mNotificationManager = notificationManager;
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case IVPN.BIND_VPN_HANDLE:
                    return mHandleBinder;
                default:
                    return null;
            }
        }
        return null;
    }

    @Override
    public void addBuilderCreator(VpnServiceBuilderCreator creator) {
        mBuilderCreator = creator;
    }

    public boolean protect(int fd) throws RemoteException {
        if (mVpnService != null) {
            return mVpnService.protect(fd);
        }
        return false;
    }


    private boolean stopVPN(boolean replaceConnection) throws RemoteException {
        onRevoke();
        boolean result = false;
        if (getManagement() != null) {
            result = getManagement().stopVPN(replaceConnection);
        }
        //laceHolderService.startService(mContext);
        mVpnService.stopSelf();
        return result;
    }

    @Override
    public void onRevoke() {
        try {
            VpnStatus.logError(R.string.permission_revoked);
            mManagement.stopVPN(false);
            stateChange(VPNState.DISCONNECTED);
            endVpnService();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {

    }

    // Similar to revoke but do not try to stop process
    public void openVpnStopped() {
        endVpnService();
    }

    private void endVpnService() {
        synchronized (mProcessLock) {
            mProcessThread = null;
        }
        VpnStatus.removeByteCountListener(this);
        unregisterDeviceStateReceiver();
        ProfileManager.setConntectedVpnProfileDisconnected(mContext);
        mOpenVPNThread = null;
        if (!mStarting) {
            mVpnService.stopForeground(!mNotificationAlwaysVisible);

            if (!mNotificationAlwaysVisible) {
                mVpnService.stopSelf();
                VpnStatus.removeStateListener(this);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void lpNotificationExtras(Notification.Builder nbuilder, String category) {
        nbuilder.setCategory(category);
        nbuilder.setLocalOnly(true);
    }

    private boolean runningOnAndroidTV() {
        UiModeManager uiModeManager = (UiModeManager) mContext.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void jbNotificationExtras(int priority,
                                      android.app.Notification.Builder nbuilder) {
        try {
            if (priority != 0) {
                Method setpriority = nbuilder.getClass().getMethod("setPriority", int.class);
                setpriority.invoke(nbuilder, priority);

                Method setUsesChronometer = nbuilder.getClass().getMethod("setUsesChronometer", boolean.class);
                setUsesChronometer.invoke(nbuilder, true);
            }

            //ignore exception
        } catch (NoSuchMethodException | IllegalArgumentException |
                InvocationTargetException | IllegalAccessException e) {
            VpnStatus.logException(e);
        }

    }


    PendingIntent getGraphPendingIntent() {
        Class activityClass = VPNConfig.openVPNActivityPendingClass;
        Intent intent = new Intent(mContext, activityClass);
        intent.putExtra("PAGE", "graph");
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent startLW = PendingIntent.getActivity(mContext, 0, intent, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startLW;

    }

    synchronized void registerDeviceStateReceiver(OpenVPNManagement magnagement) {
        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mDeviceStateReceiver = new DeviceStateReceiver(magnagement);

        // Fetch initial network state
        mDeviceStateReceiver.networkStateChange(mContext);

        mVpnService.registerReceiver(mDeviceStateReceiver, filter);
        VpnStatus.addByteCountListener(mDeviceStateReceiver);

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            addLollipopCMListener(); */
    }

    synchronized void unregisterDeviceStateReceiver() {
        if (mDeviceStateReceiver != null)
            try {
                VpnStatus.removeByteCountListener(mDeviceStateReceiver);
                mVpnService.unregisterReceiver(mDeviceStateReceiver);
            } catch (IllegalArgumentException iae) {
                // I don't know why  mContext happens:
                // java.lang.IllegalArgumentException: Receiver not registered: com.blinkt.openvpn.NetworkSateReceiver@41a61a10
                // Ignore for now ...
                iae.printStackTrace();
            }
        mDeviceStateReceiver = null;

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            removeLollipopCMListener();*/

    }

    public void userPause(boolean shouldBePaused) {
        if (mDeviceStateReceiver != null) {
            mDeviceStateReceiver.userPause(shouldBePaused);
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private void updateShortCutUsage(VpnProfile profile) {
        if (profile == null) {
            return;
        }
        ShortcutManager shortcutManager = mContext.getSystemService(ShortcutManager.class);
        //noinspection ConstantConditions
        shortcutManager.reportShortcutUsed(profile.getUUIDString());
    }

    private void startOpenVPN() {
        try {
            mProfile.writeConfigFile(mContext);
        } catch (IOException e) {
            VpnStatus.logException("Error writing config file", e);
            endVpnService();
            return;
        }
        String nativeLibraryDirectory = mContext.getApplicationInfo().nativeLibraryDir;

        // Write OpenVPN binary
        String[] argv = VPNLaunchHelper.buildOpenvpnArgv(mContext);


        // Set a flag that we are starting a new VPN
        mStarting = true;
        // Stop the previous session by interrupting the thread.

        stopOldOpenVPNProcess();
        // An old running VPN should now be exited
        mStarting = false;

        // Open the Management Interface
        // start a Thread that handles incoming messages of the managment socket
        OpenVpnManagementThread managementThread = new OpenVpnManagementThread(mProfile, this);
        if (managementThread.openManagementInterface(mContext)) {
            new Thread(managementThread, "OpenVPNManagementThread").start();
            mManagement = managementThread;
            VpnStatus.logInfo("started Socket Thread");
        } else {
            endVpnService();
            return;
        }
        mOpenVPNThread = new OpenVPNThread(this, argv, nativeLibraryDirectory);

        synchronized (mProcessLock) {
            mProcessThread = new Thread(mOpenVPNThread, "OpenVPNProcessThread");
            mProcessThread.start();
        }

        new Handler(mVpnService.getMainLooper()).post(() -> {
                    if (mDeviceStateReceiver != null) {
                        unregisterDeviceStateReceiver();
                    }
                    registerDeviceStateReceiver(mManagement);
                }
        );
    }


    private void stopOldOpenVPNProcess() {
        if (mManagement != null) {
            if (mOpenVPNThread != null)
                ((OpenVPNThread) mOpenVPNThread).setReplaceConnection();
            if (mManagement.stopVPN(true)) {
                // an old was asked to exit, wait 1s
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }

        forceStopOpenVpnProcess();
    }

    public void forceStopOpenVpnProcess() {
        synchronized (mProcessLock) {
            if (mProcessThread != null) {
                mProcessThread.interrupt();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        synchronized (mProcessLock) {
            if (mManagement != null) {
                mManagement.stopVPN(true);
            }
        }
        try {
            if (mDeviceStateReceiver != null) {
                mContext.unregisterReceiver(mDeviceStateReceiver);
            }
        } catch (Throwable e) {

        }
        // Just in case unregister for state
        VpnStatus.removeStateListener(this);
        VpnStatus.flushLog();
    }

    @Override
    @NotProguard
    public Context getContext() {
        return mContext;
    }


    private String getTunConfigString() {
        // The format of the string is not important, only that
        // two identical configurations produce the same result
        String cfg = "TUNCFG UNQIUE STRING ips:";

        if (mLocalIP != null)
            cfg += mLocalIP.toString();
        if (mLocalIPv6 != null)
            cfg += mLocalIPv6;

        cfg += "routes: " + TextUtils.join("|", mRoutes.getNetworks(true)) + TextUtils.join("|", mRoutesv6.getNetworks(true));
        cfg += "excl. routes:" + TextUtils.join("|", mRoutes.getNetworks(false)) + TextUtils.join("|", mRoutesv6.getNetworks(false));
        cfg += "dns: " + TextUtils.join("|", mDnslist);
        cfg += "domain: " + mDomain;
        cfg += "mtu: " + mMtu;
        return cfg;
    }

    public ParcelFileDescriptor openTun() {

        //Debug.startMethodTracing(getExternalFilesDir(null).toString() + "/opentun.trace", 40* 1024 * 1024);
        VpnService.Builder builder;
        try {
            builder = mBuilderCreator.create();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        VpnStatus.logInfo(R.string.last_openvpn_tun_config);

        boolean allowUnsetAF = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !mProfile.mBlockUnusedAddressFamilies;
        if (allowUnsetAF) {
            allowAllAFFamilies(builder);
        }

        if (mLocalIP == null && mLocalIPv6 == null) {
            VpnStatus.logError(mContext.getString(R.string.opentun_no_ipaddr));
            return null;
        }

        if (mLocalIP != null) {
            addLocalNetworksToRoutes();
            try {
                builder.addAddress(mLocalIP.mIp, mLocalIP.len);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.dns_add_error, mLocalIP, iae.getLocalizedMessage());
                return null;
            }
        }

        if (mLocalIPv6 != null) {
            String[] ipv6parts = mLocalIPv6.split("/");
            try {
                builder.addAddress(ipv6parts[0], Integer.parseInt(ipv6parts[1]));
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.ip_add_error, mLocalIPv6, iae.getLocalizedMessage());
                return null;
            }

        }


        for (String dns : mDnslist) {
            try {
                builder.addDnsServer(dns);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.dns_add_error, dns, iae.getLocalizedMessage());
            }
        }

        String release = Build.VERSION.RELEASE;
        if ((Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT && !release.startsWith("4.4.3")
                && !release.startsWith("4.4.4") && !release.startsWith("4.4.5") && !release.startsWith("4.4.6"))
                && mMtu < 1280) {
            VpnStatus.logInfo(String.format(Locale.US, "Forcing MTU to 1280 instead of %d to workaround Android Bug #70916", mMtu));
            builder.setMtu(1280);
        } else {
            builder.setMtu(mMtu);
        }

        Collection<NetworkSpace.IpAddress> positiveIPv4Routes = mRoutes.getPositiveIPList();
        Collection<NetworkSpace.IpAddress> positiveIPv6Routes = mRoutesv6.getPositiveIPList();

        if ("samsung".equals(Build.BRAND) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mDnslist.size() >= 1) {
            // Check if the first DNS Server is in the VPN range
            try {
                NetworkSpace.IpAddress dnsServer = new NetworkSpace.IpAddress(new CIDRIP(mDnslist.get(0), 32), true);
                boolean dnsIncluded = false;
                for (NetworkSpace.IpAddress net : positiveIPv4Routes) {
                    if (net.containsNet(dnsServer)) {
                        dnsIncluded = true;
                    }
                }
                if (!dnsIncluded) {
                    String samsungwarning = String.format("Warning Samsung Android 5.0+ devices ignore DNS servers outside the VPN range. To enable DNS resolution a route to your DNS Server (%s) has been added.", mDnslist.get(0));
                    VpnStatus.logWarning(samsungwarning);
                    positiveIPv4Routes.add(dnsServer);
                }
            } catch (Exception e) {
                // If it looks like IPv6 ignore error
                if (!mDnslist.get(0).contains(":"))
                    VpnStatus.logError("Error parsing DNS Server IP: " + mDnslist.get(0));
            }
        }

        NetworkSpace.IpAddress multicastRange = new NetworkSpace.IpAddress(new CIDRIP("224.0.0.0", 3), true);

        for (NetworkSpace.IpAddress route : positiveIPv4Routes) {
            try {

                if (multicastRange.containsNet(route))
                    VpnStatus.logDebug(R.string.ignore_multicast_route, route.toString());
                else
                    builder.addRoute(route.getIPv4Address(), route.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(mContext.getString(R.string.route_rejected) + route + " " + ia.getLocalizedMessage());
            }
        }

        for (NetworkSpace.IpAddress route6 : positiveIPv6Routes) {
            try {
                builder.addRoute(route6.getIPv6Address(), route6.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(mContext.getString(R.string.route_rejected) + route6 + " " + ia.getLocalizedMessage());
            }
        }


        if (mDomain != null)
            builder.addSearchDomain(mDomain);

        String ipv4info;
        String ipv6info;
        if (allowUnsetAF) {
            ipv4info = "(not set, allowed)";
            ipv6info = "(not set, allowed)";
        } else {
            ipv4info = "(not set)";
            ipv6info = "(not set)";
        }

        int ipv4len;
        if (mLocalIP != null) {
            ipv4len = mLocalIP.len;
            ipv4info = mLocalIP.mIp;
        } else {
            ipv4len = -1;
        }

        if (mLocalIPv6 != null) {
            ipv6info = mLocalIPv6;
        }

        VpnStatus.logInfo(R.string.local_ip_info, ipv4info, ipv4len, ipv6info, mMtu);
        VpnStatus.logInfo(R.string.dns_server_info, TextUtils.join(", ", mDnslist), mDomain);
        VpnStatus.logInfo(R.string.routes_info_incl, TextUtils.join(", ", mRoutes.getNetworks(true)), TextUtils.join(", ", mRoutesv6.getNetworks(true)));
        VpnStatus.logInfo(R.string.routes_info_excl, TextUtils.join(", ", mRoutes.getNetworks(false)), TextUtils.join(", ", mRoutesv6.getNetworks(false)));
        VpnStatus.logDebug(R.string.routes_debug, TextUtils.join(", ", positiveIPv4Routes), TextUtils.join(", ", positiveIPv6Routes));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setAllowedVpnPackages(builder);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // VPN always uses the default network
            builder.setUnderlyingNetworks(null);
        }

        String session = mProfile.mName;
        if (mLocalIP != null && mLocalIPv6 != null)
            session = mContext.getString(R.string.session_ipv6string, session, mLocalIP, mLocalIPv6);
        else if (mLocalIP != null)
            session = mContext.getString(R.string.session_ipv4string, session, mLocalIP);
        else
            session = mContext.getString(R.string.session_ipv4string, session, mLocalIPv6);

        builder.setSession(session);

        // No DNS Server, log a warning
        if (mDnslist.size() == 0)
            VpnStatus.logInfo(R.string.warn_no_dns);

        mLastTunCfg = getTunConfigString();

        // Reset information
        mDnslist.clear();
        mRoutes.clear();
        mRoutesv6.clear();
        mLocalIP = null;
        mLocalIPv6 = null;
        mDomain = null;

        builder.setConfigureIntent(getGraphPendingIntent());

        try {
            //Debug.stopMethodTracing();
            ParcelFileDescriptor tun = builder.establish();
            if (tun == null)
                throw new NullPointerException("Android establish() method returned null (Really broken network configuration?)");
            return tun;
        } catch (Exception e) {
            VpnStatus.logError(R.string.tun_open_error);
            VpnStatus.logError(mContext.getString(R.string.error) + e.getLocalizedMessage());
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                VpnStatus.logError(R.string.tun_error_helpful);
            }
            return null;
        }

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void allowAllAFFamilies(VpnService.Builder builder) {
        builder.allowFamily(OsConstants.AF_INET);
        builder.allowFamily(OsConstants.AF_INET6);
    }

    private void addLocalNetworksToRoutes() {
        for (String net : NetworkUtils.getLocalNetworks(mContext, false)) {
            String[] netparts = net.split("/");
            String ipAddr = netparts[0];
            int netMask = Integer.parseInt(netparts[1]);
            if (ipAddr.equals(mLocalIP.mIp))
                continue;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT && !mProfile.mAllowLocalLAN) {
                mRoutes.addIPSplit(new CIDRIP(ipAddr, netMask), true);

            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && mProfile.mAllowLocalLAN)
                mRoutes.addIP(new CIDRIP(ipAddr, netMask), false);
        }

        // IPv6 is Lollipop+ only so we can skip the lower than KITKAT case
        if (mProfile.mAllowLocalLAN) {
            for (String net : NetworkUtils.getLocalNetworks(mContext, true)) {
                addRoutev6(net, false);
            }
        }


    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setAllowedVpnPackages(VpnService.Builder builder) {
        boolean profileUsesOrBot = false;

        for (Connection c : mProfile.mConnections) {
            if (c.mProxyType == Connection.ProxyType.ORBOT)
                profileUsesOrBot = true;
        }

        if (profileUsesOrBot) {
            VpnStatus.logDebug("VPN Profile uses at least one server entry with Orbot. Setting up VPN so that OrBot is not redirected over VPN.");
        }

        if (mProfile.mAllowedAppsVpnAreDisallowed && profileUsesOrBot) {
            try {
                builder.addDisallowedApplication(ORBOT_PACKAGE_NAME);
            } catch (PackageManager.NameNotFoundException e) {
                VpnStatus.logDebug("Orbot not installed?");
            }
        }

        if (mAppFilter != null) {
            mAppFilter.applyFilter(mContext, builder);
        }

        if (mProfile.mAllowAppVpnBypass) {
            builder.allowBypass();
            VpnStatus.logDebug("Apps may bypass VPN");
        }
    }

    public void addDNS(String dns) {
        mDnslist.add(dns);
    }

    public void setDomain(String domain) {
        if (mDomain == null) {
            mDomain = domain;
        }
    }

    /**
     * Route that is always included, used by the v3 core
     */
    public void addRoute(CIDRIP route, boolean include) {
        mRoutes.addIP(route, include);
    }

    public void addRoute(String dest, String mask, String gateway, String device) {
        CIDRIP route = new CIDRIP(dest, mask);
        boolean include = isAndroidTunDevice(device);

        NetworkSpace.IpAddress gatewayIP = new NetworkSpace.IpAddress(new CIDRIP(gateway, 32), false);

        if (mLocalIP == null) {
            VpnStatus.logError("Local IP address unset and received. Neither pushed server config nor local config specifies an IP addresses. Opening tun device is most likely going to fail.");
            return;
        }
        NetworkSpace.IpAddress localNet = new NetworkSpace.IpAddress(mLocalIP, true);
        if (localNet.containsNet(gatewayIP))
            include = true;

        if (gateway != null &&
                (gateway.equals("255.255.255.255") || gateway.equals(mRemoteGW)))
            include = true;


        if (route.len == 32 && !mask.equals("255.255.255.255")) {
            VpnStatus.logWarning(R.string.route_not_cidr, dest, mask);
        }

        if (route.normalise())
            VpnStatus.logWarning(R.string.route_not_netip, dest, route.len, route.mIp);

        mRoutes.addIP(route, include);
    }

    public void addRoutev6(String network, String device) {
        // Tun is opened after ROUTE6, no device name may be present
        boolean included = isAndroidTunDevice(device);
        addRoutev6(network, included);
    }

    public void addRoutev6(String network, boolean included) {
        String[] v6parts = network.split("/");

        try {
            Inet6Address ip = (Inet6Address) InetAddress.getAllByName(v6parts[0])[0];
            int mask = Integer.parseInt(v6parts[1]);
            mRoutesv6.addIPv6(ip, mask, included);

        } catch (UnknownHostException e) {
            VpnStatus.logException(e);
        }

    }

    private boolean isAndroidTunDevice(String device) {
        return device != null && (device.startsWith("tun") || "(null)".equals(device) || VPNSERVICE_TUN.equals(device));
    }

    public void setMtu(int mtu) {
        mMtu = mtu;
    }

    public void setLocalIP(CIDRIP cdrip) {
        mLocalIP = cdrip;
    }

    public void setLocalIP(String local, String netmask, int mtu, String mode) {
        mLocalIP = new CIDRIP(local, netmask);
        mMtu = mtu;
        mRemoteGW = null;

        long netMaskAsInt = CIDRIP.getInt(netmask);

        if (mLocalIP.len == 32 && !netmask.equals("255.255.255.255")) {
            // get the netmask as IP

            int masklen;
            long mask;
            if ("net30".equals(mode)) {
                masklen = 30;
                mask = 0xfffffffc;
            } else {
                masklen = 31;
                mask = 0xfffffffe;
            }

            // Netmask is Ip address +/-1, assume net30/p2p with small net
            if ((netMaskAsInt & mask) == (mLocalIP.getInt() & mask)) {
                mLocalIP.len = masklen;
            } else {
                mLocalIP.len = 32;
                if (!"p2p".equals(mode))
                    VpnStatus.logWarning(R.string.ip_not_cidr, local, netmask, mode);
            }
        }
        if (("p2p".equals(mode) && mLocalIP.len < 32) || ("net30".equals(mode) && mLocalIP.len < 30)) {
            VpnStatus.logWarning(R.string.ip_looks_like_subnet, local, netmask, mode);
        }


        /* Workaround for Lollipop, it  does not route traffic to the VPNs own network mask */
        if (mLocalIP.len <= 31 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CIDRIP interfaceRoute = new CIDRIP(mLocalIP.mIp, mLocalIP.len);
            interfaceRoute.normalise();
            addRoute(interfaceRoute, true);
        }


        // Configurations are sometimes really broken...
        mRemoteGW = netmask;
    }

    public void setLocalIPv6(String ipv6Addr) {
        mLocalIPv6 = ipv6Addr;
    }

    @Override
    public void updateState(String state, String logmessage, int resid, ConnectionStatus level) {
        stateTransform(level);
        // If the process is not running, ignore any state,
        // Notification should be invisible in mContext state
//        doSendBroadcast(state, level);
        if (mProcessThread == null && !mNotificationAlwaysVisible) {
            return;
        }
        String channel = NOTIFICATION_CHANNEL_NEWSTATUS_ID;
        // Display byte count only after being connected
        {
            if (level == LEVEL_CONNECTED) {
                mDisplayBytecount = true;
                mConnecttime = System.currentTimeMillis();
                if (!runningOnAndroidTV()) {
                    channel = NOTIFICATION_CHANNEL_NEWSTATUS_ID;
                }
            } else {
                mDisplayBytecount = false;
            }
            // Other notifications are shown,
            // mContext also mean we are no longer connected, ignore bytecount messages until next
            // CONNECTED
            // Does not work :(
            //showNotification(VpnStatus.getLastCleanLogMessage(mContext), VpnStatus.getLastCleanLogMessage(mContext), channel, 0, level);
        }
    }
    private boolean isStarting = false;
    private void stateTransform(ConnectionStatus level) {
        switch (level) {
            case LEVEL_CONNECTED:
                isStarting = true;
                if (mNotificationManager != null) {
                    mNotificationManager.connected(mVpnService,mServerNodeName);
                }
                stateChange(VPNState.CONNECTED);
                break;
            case LEVEL_NONETWORK:
            case LEVEL_NOTCONNECTED:
                if (isStarting){
                    stateChange(VPNState.DISCONNECTED);
                }
                break;
            case LEVEL_AUTH_FAILED:
            case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
                isStarting = true;
                stateChange(VPNState.CONNECT_FAIL);
                break;
            case LEVEL_CONNECTING_SERVER_REPLIED:
            case LEVEL_WAITING_FOR_USER_INPUT:
            case LEVEL_START:
                isStarting = true;
                stateChange(VPNState.CONNECTING);
            break;
        }
    }

    @Override
    public void setConnectedVPN(String uuid) {
    }


    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (mDisplayBytecount) {
            String title = mServerNodeName;
            if (TextUtils.isEmpty(title)) {
                title = mContext.getString(mContext.getApplicationInfo().labelRes);
            }
            if (mNotificationManager != null) {
                mNotificationManager.byteCountChange(mVpnService, title, in, out, diffIn, diffOut);
            }
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        Runnable r = msg.getCallback();
        if (r != null) {
            r.run();
            return true;
        } else {
            return false;
        }
    }

    public OpenVPNManagement getManagement() {
        return mManagement;
    }

    public String getTunReopenStatus() {
        String currentConfiguration = getTunConfigString();
        if (currentConfiguration.equals(mLastTunCfg)) {
            return "NOACTION";
        } else {
            String release = Build.VERSION.RELEASE;
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT && !release.startsWith("4.4.3")
                    && !release.startsWith("4.4.4") && !release.startsWith("4.4.5") && !release.startsWith("4.4.6"))
                // There will be probably no 4.4.4 or 4.4.5 version, so don't waste effort to do parsing here
                return "OPEN_AFTER_CLOSE";
            else
                return "OPEN_BEFORE_CLOSE";
        }
    }

    public void requestInputFromUser(int resid, String needed) {
        VpnStatus.updateStateString("NEED", "need " + needed, resid, LEVEL_WAITING_FOR_USER_INPUT);
    }

    public void trigger_url_open(String info) {
        String channel = NOTIFICATION_CHANNEL_USERREQ_ID;
        String url = info.split(":", 2)[1];

        NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder nbuilder = new Notification.Builder(mVpnService);
        nbuilder.setContentTitle(mContext.getString(R.string.openurl_requested));

        nbuilder.setContentText(url);
        nbuilder.setAutoCancel(true);

        int icon = android.R.drawable.ic_dialog_info;

        nbuilder.setSmallIcon(icon);

        Intent openUrlIntent = new Intent(Intent.ACTION_VIEW);
        openUrlIntent.setData(Uri.parse(url));
        openUrlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        nbuilder.setContentIntent(PendingIntent.getActivity(mContext, 0, openUrlIntent, 0));


        // Try to set the priority available since API 16 (Jellybean)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            jbNotificationExtras(PRIORITY_MAX, nbuilder);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            lpNotificationExtras(nbuilder, Notification.CATEGORY_STATUS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //noinspection NewApi
            nbuilder.setChannelId(channel);
        }

        @SuppressWarnings("deprecation")
        Notification notification = nbuilder.getNotification();

        int notificationId = channel.hashCode();

        mNotificationManager.notify(notificationId, notification);
    }

    public static Intent getIntent(Context context) {
        Intent intent = new Intent(context, VPNConfig.openVPNServiceClass);
        intent.putExtra(IVPNService.VPN_TYPE, IVPNService.VPN_TYPE_OPEN);
        return intent;
    }

    private void message(String msg) {
        for (VPNCallback mCallback : mCallbacks) {
            mCallback.message(msg);
        }
    }

    private void stateChange(VPNState state) {
        if (mNotificationManager != null) {
            mNotificationManager.stateChange(mVpnService, state);
        }
        for (VPNCallback mCallback : mCallbacks) {
            mCallback.stateChange(state);
        }
    }

    private void checkNotificationManager() {
        if (mNotificationManager == null) {
            throw new RuntimeException("Must set a notification manager , please call IVPN.addNotificationManager()");
        }
    }
}
