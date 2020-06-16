package org.ikev2.android.logic;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.base.vpn.IVPN;
import com.base.vpn.IVPNService;
import com.base.vpn.NetTraffics;
import com.base.vpn.VPNConfig;
import com.base.vpn.utils.VPNLog;
import com.proguard.annotation.IClassName;
import com.proguard.annotation.IPublic;
import com.proguard.annotation.NotProguard;

import org.ikev2.android.data.VpnProfile;
import org.ikev2.android.data.VpnType;
import org.ikev2.android.logic.imc.ImcState;
import org.ikev2.android.logic.imc.RemediationInstruction;
import org.ikev2.android.security.Ikev2Config;
import org.ikev2.android.utils.Constants;
import org.ikev2.android.utils.IPRange;
import org.ikev2.android.utils.IPRangeSet;
import org.ikev2.android.utils.SettingsWriter;
import org.strongswan.android.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.Vector;
import java.util.concurrent.Callable;

import static org.ikev2.android.logic.Ikev2VPNImpl.ErrorState.AUTH_ERROR;
import static org.ikev2.android.logic.Ikev2VPNImpl.ErrorState.CERTIFICATE_UNAVAILABLE;
import static org.ikev2.android.logic.Ikev2VPNImpl.ErrorState.LOOKUP_ERROR;
import static org.ikev2.android.logic.Ikev2VPNImpl.ErrorState.PASSWORD_MISSING;
import static org.ikev2.android.logic.Ikev2VPNImpl.ErrorState.PEER_AUTH_ERROR;
import static org.ikev2.android.logic.Ikev2VPNImpl.ErrorState.UNREACHABLE_ERROR;

@NotProguard
public class Ikev2VPNImpl implements IVPNService, Runnable, IVPN.ByteCountListener {

    private static final String TAG = Ikev2VPNImpl.class.getSimpleName();
    private static final String DISCONNECT_ACTION = "org.ikev2.android.CharonVpnService.DISCONNECT";
    private static final String NOTIFICATION_CHANNEL = "org.ikev2.android.CharonVpnService.VPN_STATE_NOTIFICATION";
    private static final String KEY_IS_RETRY = "retry";
    private static final int VPN_STATE_NOTIFICATION_ID = 1;

    public static final String LOG_FILE = "charon.log";

    public static final String KEY_ID = "_id";
    public static final String KEY_UUID = "_uuid";
    public static final String KEY_NAME = "name";
    public static final String KEY_GATEWAY = "gateway";
    public static final String KEY_VPN_TYPE = "vpn_type";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_CERTIFICATE = "certificate";

    private String mLogFile;
    private String mAppDir;
    private Thread mConnectionHandler;
    private VpnProfile mCurrentProfile;
    private volatile String mCurrentCertificateAlias;
    private volatile String mCurrentUserCertificateAlias;
    private VpnProfile mNextProfile;
    private volatile boolean mProfileUpdated;
    private volatile boolean mIsDisconnecting;
    private BuilderAdapter mBuilderAdapter = new BuilderAdapter();
    private Handler mHandler;
    private final Object mServiceLock = new Object();

    private VpnService mVpnService;
    private Context mContext;
    private VpnServiceBuilderCreator mBuilderCreator;
    private Vector<VPNCallback> mCallbacks;
    private State mState;
    private ErrorState mErrorState;
    private ImcState mImcState;

    private Handler mTimeoutHandle;
    private HandlerThread mTimeoutThread;
    private static final int TIME_OUT_WHAT = 123;
    private static final long TIME_OUT = 5000L;
    private static final int TIMEOUT_RETRIES = 2;//最多重试两次
    private int retries = 0;
	private AppFilter mAppFilter;
	private INotificationManager mNotificationManager;

	@Override
	public void updateByteCount(long in, long out, long diffIn, long diffOut) {
		if (mState == State.CONNECTED) {
			Resources resources = mContext.getResources();
			String title = mCurrentProfile.getName();
			if (TextUtils.isEmpty(title)) {
				title = resources.getString(mContext.getApplicationInfo().labelRes);
			}
			if (mNotificationManager != null) {
				mNotificationManager.byteCountChange(mVpnService, title, in, out, diffIn, diffOut);
			}
		}
	}


    public enum State {
        DISABLED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
    }

    public enum ErrorState {
        NO_ERROR,
        AUTH_ERROR,
        PEER_AUTH_ERROR,
        LOOKUP_ERROR,
        UNREACHABLE_ERROR,
        GENERIC_ERROR,
        PASSWORD_MISSING,
        CERTIFICATE_UNAVAILABLE,
    }

    public Ikev2VPNImpl(@NonNull VpnService mVpnService) {
        this.mVpnService = mVpnService;
        this.mContext = mVpnService.getApplicationContext();
        this.mCallbacks = new Vector<>();
    }

	@Override
	public void connect(Bundle profileInfo) {
		checkNotificationManager();
		Context context = mContext;
		Intent intent = Ikev2VPNImpl.getIntent(context);
		//从头开始连接
		profileInfo.putBoolean(Ikev2VPNImpl.KEY_IS_RETRY, false);
		intent.putExtras(profileInfo);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			// Pre-O behavior.
			context.startService(intent);
		}
    }

    @Override
    public void disconnect() {
        Context context = mContext;
        Intent intent = Ikev2VPNImpl.getIntent(context);
        intent.setAction(Ikev2VPNImpl.DISCONNECT_ACTION);
        context.startService(intent);
        context.stopService(intent);
    }

    @Override
    public void reconnect() {
        // TODO: 2020-05-29
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


	private Ikev2VPNHandleBinder mHandleBinder = new Ikev2VPNHandleBinder();

    public final class Ikev2VPNHandleBinder extends Binder {
        public IVPN getVPNService() {
            return (IVPN) mVpnService;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mHandleBinder;
    }

    @Override
    public void addBuilderCreator(VpnServiceBuilderCreator creator) {
        mBuilderCreator = creator;
    }

    /**
     * as defined in charonservice.h
     */
    private static final int STATE_CHILD_SA_UP = 1;
    private static final int STATE_CHILD_SA_DOWN = 2;
    private static final int STATE_AUTH_ERROR = 3;
    private static final int STATE_PEER_AUTH_ERROR = 4;
    private static final int STATE_LOOKUP_ERROR = 5;
    private static final int STATE_UNREACHABLE_ERROR = 6;
    private static final int STATE_CERTIFICATE_UNAVAILABLE = 7;
    private static final int STATE_GENERIC_ERROR = 8;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            VpnProfile profile = null;
            boolean retry = false;
            if (!TextUtils.equals(DISCONNECT_ACTION, intent.getAction())) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    retry = bundle.getBoolean(Ikev2VPNImpl.KEY_IS_RETRY, false);
                }
                profile = Ikev2Config.getVpnProfile();
            }
            if (profile != null && !retry) {    /* delete the log file if this is not an automatic retry */
                mContext.deleteFile(LOG_FILE);
            }
            setNextProfile(profile);
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        String basePath = mContext.getFilesDir().getAbsolutePath();
        mAppDir = basePath;
        mLogFile = basePath + File.separator + LOG_FILE;
        /* handler used to do changes in the main UI thread */
        mHandler = new Handler();
        /* use a separate thread as main thread for charon */
        mConnectionHandler = new Thread(this);
        mConnectionHandler.start();

        mTimeoutThread = new HandlerThread("connect time out");
        mTimeoutThread.start();
        mTimeoutHandle = new Handler(mTimeoutThread.getLooper());

        NetTraffics.getInstance().addByteCountListener(this);
    }

    @Override
    public void onRevoke() {
        /*
         *
         * the system revoked the rights grated with the initial prepare() call.
         * called when the user clicks disconnect in the system's VPN dialog
         *
         */
        NetTraffics.getInstance().removeByteByteCountListener(Ikev2VPNImpl.this);
        setNextProfile(null);
    }

    @Override
    public void onDestroy() {
        if (mState != State.DISABLED) {
            setNextProfile(null);
        }
        try {
            mConnectionHandler.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mTimeoutThread.quitSafely();
        }catch (Throwable throwable){
        }
    }

    /**
     * Set the profile that is to be initiated next. Notify the handler thread.
     *
     * @param profile the profile to initiate [VpnProfile为null时代表断开连接]
     */
    private void setNextProfile(VpnProfile profile) {
		if (profile != null) {
			String name = profile.getName();
			if (mNotificationManager != null) {
				mNotificationManager.connecting(mVpnService, name);
			}
		}
        synchronized (this) {
            this.mNextProfile = profile;
            mProfileUpdated = true;
            notifyAll();
        }
    }

    @Override
    public void run() {
        while (true) {
            synchronized (this) {
                try {
                    while (!mProfileUpdated) {
                        wait();
                    }

                    mProfileUpdated = false;
                    stopCurrentConnection();//把之前的干死
                    if (mNextProfile == null) {
                        setState(State.DISABLED);
                        NetTraffics.getInstance().removeByteByteCountListener(Ikev2VPNImpl.this);
						mVpnService.stopForeground(true);
						mVpnService.stopSelf();
                        return;
                    } else {
                        mCurrentProfile = mNextProfile;
                        mNextProfile = null;

                        /* store this in a separate (volatile) variable to avoid
                         * a possible deadlock during deinitialization */
                        mCurrentCertificateAlias = mCurrentProfile.getCertificateAlias();
                        mCurrentUserCertificateAlias = mCurrentProfile.getUserCertificateAlias();

                        startConnection();
                        mIsDisconnecting = false;

                        SimpleFetcher.enable();
                        mBuilderAdapter.setProfile(mCurrentProfile);
                        if (initializeCharon(mBuilderAdapter, mLogFile, mAppDir, mCurrentProfile.getVpnType().has(VpnType.VpnTypeFeature.BYOD))) {
                            VPNLog.d("IKEV2: charon started");
                            if (mCurrentProfile.getVpnType().has(VpnType.VpnTypeFeature.USER_PASS) &&
                                    mCurrentProfile.getPassword() == null) {    /* this can happen if Always-on VPN is enabled with an incomplete profile */
                                setError(PASSWORD_MISSING);
                                continue;
                            }
                            SettingsWriter writer = new SettingsWriter();
                            writer.setValue("global.language", Locale.getDefault().getLanguage());
                            writer.setValue("global.mtu", mCurrentProfile.getMTU());
                            writer.setValue("global.nat_keepalive", mCurrentProfile.getNATKeepAlive());
                            writer.setValue("global.rsa_pss", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_RSA_PSS) != 0);
                            writer.setValue("global.crl", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_DISABLE_CRL) == 0);
                            writer.setValue("global.ocsp", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_DISABLE_OCSP) == 0);
                            writer.setValue("connection.type", mCurrentProfile.getVpnType().getIdentifier());
                            writer.setValue("connection.server", mCurrentProfile.getGateway());
                            writer.setValue("connection.port", mCurrentProfile.getPort());
                            writer.setValue("connection.username", mCurrentProfile.getUsername());
                            writer.setValue("connection.password", mCurrentProfile.getPassword());
                            writer.setValue("connection.local_id", mCurrentProfile.getLocalId());
                            writer.setValue("connection.remote_id", mCurrentProfile.getRemoteId());
                            writer.setValue("connection.certreq", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_SUPPRESS_CERT_REQS) == 0);
                            writer.setValue("connection.strict_revocation", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_STRICT_REVOCATION) != 0);
                            writer.setValue("connection.ike_proposal", mCurrentProfile.getIkeProposal());
                            writer.setValue("connection.esp_proposal", mCurrentProfile.getEspProposal());
                            VPNLog.d("IKEV2: start serialize");
                            initiate(writer.serialize());
                            VPNLog.d("IKEV2: serialize finish");
                        } else {
                            VPNLog.d("IKEV2 : failed to start charon");
                            setError(ErrorState.GENERIC_ERROR);
                            setState(State.DISABLED);
                            mCurrentProfile = null;
                        }
                    }
                } catch (InterruptedException ex) {
                    stopCurrentConnection();
                    setState(State.DISABLED);
                    VPNLog.d("IKEV2: error:", ex);
                }
            }
        }
    }

    /**
     * Update the current VPN state on the state service. Called by the handler
     * thread and any of charon's threads.
     *
     * @param state current state
     */
    private void setState(State state) {
        synchronized (mServiceLock) {
            notifyListeners(() -> {
                mState = state;
                if (mState == State.CONNECTED
                        && mErrorState != ErrorState.NO_ERROR) {
                    mErrorState = ErrorState.NO_ERROR;
                }
                return true;
            });
        }
    }


    /**
     * Set an error on the state service. Called by the handler thread and any
     * of charon's threads.
     *
     * @param error error state
     */
    private void setError(ErrorState error) {
        notifyListeners(() -> {
            mErrorState = error;
            return true;
        });
    }


    /**
     * Set an error on the state service. Called by the handler thread and any
     * of charon's threads.
     *
     * @param error error state
     */
    private void setErrorDisconnect(ErrorState error) {
        synchronized (mServiceLock) {
            if (!mIsDisconnecting) {
                setError(error);
            }
        }
    }

    private Runnable mTimeoutRun = new Runnable() {
        @Override
        public void run() {
            VPNLog.d("IKEV2 : check connection");
            if (mErrorState != ErrorState.NO_ERROR && mState != State.CONNECTED) {
                VPNLog.d("IKEV2 : call CONNECT_FAIL");
                resetTimeout();
                stateChange(VPNState.CONNECT_FAIL);
            } else if (mErrorState == ErrorState.NO_ERROR && mState != State.CONNECTED) {
                if (retries < TIMEOUT_RETRIES) {
                    retries++;
                    VPNLog.d("IKEV2 : try to delay 5s again");
                    mTimeoutHandle.postDelayed(mTimeoutRun, TIME_OUT);//没有出现错误 但没连接成功 再等5秒
                }
            }else {
				VPNLog.d("IKEV2 : mTimeoutRun " + mState);
			}
        }
    };

    private void resetTimeout() {
        VPNLog.d("IKEV2 : resetTimeout()");
        mTimeoutHandle.removeMessages(TIME_OUT_WHAT);
        retries = 0;//重置次数
    }

    private void startTimeout() {
        Message message = Message.obtain(mTimeoutHandle, mTimeoutRun);
        message.what = TIME_OUT_WHAT;
        //连接操作开始5秒内没有连接成功 直接强制认为是连接失败
        mTimeoutHandle.sendMessageDelayed(message, TIME_OUT);
        VPNLog.d("IKEV2 : startTimeout()");
    }

    private void startConnection() {
        synchronized (mServiceLock) {
            notifyListeners(() -> {
                mState = State.CONNECTING;
                mErrorState = ErrorState.NO_ERROR;
                mImcState = ImcState.UNKNOWN;
                startTimeout();
                return true;
            });
        }
    }

    /**
     * Stop any existing connection by deinitializing charon.
     */
    private void stopCurrentConnection() {
        synchronized (this) {
            resetTimeout();
            if (mNextProfile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBuilderAdapter.setProfile(mNextProfile);
                mBuilderAdapter.establishBlocking();
            }
            if (mCurrentProfile != null) {
                setState(State.DISCONNECTING);
                mIsDisconnecting = true;
                SimpleFetcher.disable();
                deinitializeCharon();
                Log.i(TAG, "charon stopped");
                mCurrentProfile = null;
                if (mNextProfile == null) {    /* only do this if we are not connecting to another profile */
                    removeNotification();
                    mBuilderAdapter.closeBlocking();
                }
            }
        }
    }

    /**
     * Remove the permanent notification.
     */
    private void removeNotification() {
        mHandler.post(() -> {
			if (mNotificationManager != null) {
				mNotificationManager.disconnect(mVpnService);
			}
        });
    }


    private void stateChanged() {
		if (mCurrentProfile != null) {
			String name = mCurrentProfile.getName();
			if (!TextUtils.isEmpty(name)) {
				switch (mState) {
					case CONNECTED:
						if (mNotificationManager != null) {
							mNotificationManager.connected(mVpnService, name);
						}
						break;
				}
			}
		}
        if (mState == State.CONNECTED
                && mErrorState != ErrorState.NO_ERROR) {
            mErrorState = ErrorState.NO_ERROR;
        }
        if (mState == State.CONNECTED){
            resetTimeout();
        }else if (mState == State.DISABLED){
            resetTimeout();
        }
        stateTransform();
    }


    /**
     * Updates the state of the current connection.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param status new state
     */
    @NotProguard
    public void updateStatus(int status) {
        switch (status) {
            case STATE_CHILD_SA_DOWN:
                if (!mIsDisconnecting) {
                    setState(State.CONNECTING);
                }
                break;
            case STATE_CHILD_SA_UP:
                setState(State.CONNECTED);
                break;
            case STATE_AUTH_ERROR:
                setErrorDisconnect(AUTH_ERROR);
                break;
            case STATE_PEER_AUTH_ERROR:
                setErrorDisconnect(PEER_AUTH_ERROR);
                break;
            case STATE_LOOKUP_ERROR:
                setErrorDisconnect(LOOKUP_ERROR);
                break;
            case STATE_UNREACHABLE_ERROR:
                setErrorDisconnect(UNREACHABLE_ERROR);
                break;
            case STATE_CERTIFICATE_UNAVAILABLE:
                setErrorDisconnect(CERTIFICATE_UNAVAILABLE);
                break;
            case STATE_GENERIC_ERROR:
                setErrorDisconnect(ErrorState.GENERIC_ERROR);
                break;
            default:
                Log.e(TAG, "Unknown status code received");
                break;
        }
    }

    /**
     * Updates the IMC state of the current connection.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param value new state
     */
    @NotProguard
    public void updateImcState(int value) {
        //nothing to do
        synchronized (mServiceLock) {
            mImcState = ImcState.fromValue(value);
        }
    }

    /**
     * Add a remediation instruction to the VPN state service.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param xml XML text
     */
    @NotProguard
    public void addRemediationInstruction(String xml) {
        for (RemediationInstruction instruction : RemediationInstruction.fromXml(xml)) {
            synchronized (mServiceLock) {
                //nothing to do
            }
        }
    }

    /**
     * Function called via JNI to generate a list of DER encoded CA certificates
     * as byte array.
     *
     * @return a list of DER encoded CA certificates
     */
    @NotProguard
    private byte[][] getTrustedCertificates() {
        ArrayList<byte[]> certs = new ArrayList<>();
        TrustedCertificateManager certman = TrustedCertificateManager.getInstance().load();
        try {
            String alias = this.mCurrentCertificateAlias;
            if (alias != null) {
                X509Certificate cert = certman.getCACertificateFromAlias(alias);
                if (cert == null) {
                    return null;
                }
                certs.add(cert.getEncoded());
            } else {
                for (X509Certificate cert : certman.getAllCACertificates().values()) {
                    certs.add(cert.getEncoded());
                }
            }
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
            return null;
        }
        return certs.toArray(new byte[certs.size()][]);
    }

    /**
     * Function called via JNI to get a list containing the DER encoded certificates
     * of the user selected certificate chain (beginning with the user certificate).
     * <p>
     * Since this method is called from a thread of charon's thread pool we are safe
     * to call methods on KeyChain directly.
     *
     * @return list containing the certificates (first element is the user certificate)
     * @throws InterruptedException
     * @throws KeyChainException
     * @throws CertificateEncodingException
     */
    @NotProguard
    private byte[][] getUserCertificate() throws KeyChainException, InterruptedException, CertificateEncodingException {
        ArrayList<byte[]> encodings = new ArrayList<byte[]>();
        X509Certificate[] chain = KeyChain.getCertificateChain(mContext, mCurrentUserCertificateAlias);
        if (chain == null || chain.length == 0) {
            return null;
        }
        for (X509Certificate cert : chain) {
            encodings.add(cert.getEncoded());
        }
        return encodings.toArray(new byte[encodings.size()][]);
    }

    /**
     * Function called via JNI to get the private key the user selected.
     * <p>
     * Since this method is called from a thread of charon's thread pool we are safe
     * to call methods on KeyChain directly.
     *
     * @return the private key
     * @throws InterruptedException
     * @throws KeyChainException
     */
    @NotProguard
    private PrivateKey getUserKey() throws KeyChainException, InterruptedException {
        return KeyChain.getPrivateKey(mContext, mCurrentUserCertificateAlias);
    }

    /**
     * Initialization of charon, provided by libandroidbridge.so
     *
     * @param builder BuilderAdapter for this connection
     * @param logfile absolute path to the logfile
     * @param appdir  absolute path to the data directory of the app
     * @param byod    enable BYOD features
     * @return TRUE if initialization was successful
     */
    public native boolean initializeCharon(BuilderAdapter builder, String logfile, String appdir, boolean byod);

    /**
     * Deinitialize charon, provided by libandroidbridge.so
     */
    public native void deinitializeCharon();

    /**
     * Initiate VPN, provided by libandroidbridge.so
     */
    public native void initiate(String config);

    /**
     * Adapter for VpnService.Builder which is used to access it safely via JNI.
     * There is a corresponding C object to access it from native code.
     */
    @NotProguard
    public class BuilderAdapter implements IClassName, IPublic {
        private VpnProfile mProfile;
        private VpnService.Builder mBuilder;
        private BuilderCache mCache;
        private BuilderCache mEstablishedCache;
        private PacketDropper mDropper = new PacketDropper();

        public synchronized void setProfile(VpnProfile profile) {
            mProfile = profile;
            mBuilder = createBuilder(mProfile.getName());
            mCache = new BuilderCache(mProfile);
        }

        private VpnService.Builder createBuilder(String name) {
            VpnService.Builder builder = mBuilderCreator.create();
            builder.setSession(name);

            /* even though the option displayed in the system dialog says "Configure"
             * we just use our main Activity */
            Context context = mContext;
            Intent intent = new Intent(context, VPNConfig.ikev2VPNActivityPendingClass);
            PendingIntent pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setConfigureIntent(pending);
            return builder;
        }

        public synchronized boolean addAddress(String address, int prefixLength) {
            try {
                mCache.addAddress(address, prefixLength);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addDnsServer(String address) {
            try {
                mBuilder.addDnsServer(address);
                mCache.recordAddressFamily(address);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addRoute(String address, int prefixLength) {
            try {
                mCache.addRoute(address, prefixLength);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addSearchDomain(String domain) {
            try {
                mBuilder.addSearchDomain(domain);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean setMtu(int mtu) {
            try {
                mCache.setMtu(mtu);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        private synchronized ParcelFileDescriptor establishIntern() {
            ParcelFileDescriptor fd;
            try {
                mCache.applyData(mBuilder);
                fd = mBuilder.establish();
                if (fd != null) {
                    closeBlocking();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
            if (fd == null) {
                return null;
            }
            /* now that the TUN device is created we don't need the current
             * builder anymore, but we might need another when reestablishing */
            mBuilder = createBuilder(mProfile.getName());
            mEstablishedCache = mCache;
            mCache = new BuilderCache(mProfile);
            return fd;
        }

        public synchronized int establish() {
            ParcelFileDescriptor fd = establishIntern();
            return fd != null ? fd.detachFd() : -1;
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public synchronized void establishBlocking() {
            /* just choose some arbitrary values to block all traffic (except for what's configured in the profile) */
            mCache.addAddress("172.16.252.1", 32);
            mCache.addAddress("fd00::fd02:1", 128);
            mCache.addRoute("0.0.0.0", 0);
            mCache.addRoute("::", 0);
            /* set DNS servers to avoid DNS leak later */
            mBuilder.addDnsServer("8.8.8.8");
            mBuilder.addDnsServer("2001:4860:4860::8888");
            /* use blocking mode to simplify packet dropping */
            mBuilder.setBlocking(true);
            ParcelFileDescriptor fd = establishIntern();
            if (fd != null) {
                mDropper.start(fd);
            }
        }

        public synchronized void closeBlocking() {
            mDropper.stop();
        }

        public synchronized int establishNoDns() {
            ParcelFileDescriptor fd;

            if (mEstablishedCache == null) {
                return -1;
            }
            try {
                VpnService.Builder builder = createBuilder(mProfile.getName());
                mEstablishedCache.applyData(builder);
                fd = builder.establish();
            } catch (Exception ex) {
                ex.printStackTrace();
                return -1;
            }
            if (fd == null) {
                return -1;
            }
            return fd.detachFd();
        }

        private class PacketDropper implements Runnable {
            private ParcelFileDescriptor mFd;
            private Thread mThread;

            public void start(ParcelFileDescriptor fd) {
                mFd = fd;
                mThread = new Thread(this);
                mThread.start();
            }

            public void stop() {
                if (mFd != null) {
                    try {
                        mThread.interrupt();
                        mThread.join();
                        mFd.close();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mFd = null;
                }
            }

            @Override
            public synchronized void run() {
                try {
                    FileInputStream plain = new FileInputStream(mFd.getFileDescriptor());
                    ByteBuffer packet = ByteBuffer.allocate(mCache.mMtu);
                    while (true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {    /* just read and ignore all data, regular read() is not interruptible */
                            int len = plain.getChannel().read(packet);
                            packet.clear();
                            if (len < 0) {
                                break;
                            }
                        } else {    /* this is rather ugly but on older platforms not even the NIO version of read() is interruptible */
                            boolean wait = true;
                            if (plain.available() > 0) {
                                int len = plain.read(packet.array());
                                packet.clear();
                                if (len < 0 || Thread.interrupted()) {
                                    break;
                                }
                                /* check again right away, there may be another packet */
                                wait = false;
                            }
                            if (wait) {
                                Thread.sleep(250);
                            }
                        }
                    }
                } catch (ClosedByInterruptException | InterruptedException e) {
                    /* regular interruption */
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Function called via JNI to determine information about the Android version.
     */
    @NotProguard
    private static String getAndroidVersion() {
        String version = "Android " + Build.VERSION.RELEASE + " - " + Build.DISPLAY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            version += "/" + Build.VERSION.SECURITY_PATCH;
        }
        return version;
    }

    /**
     * Function called via JNI to determine information about the device.
     */
    @NotProguard
    private static String getDeviceString() {
        return Build.MODEL + " - " + Build.BRAND + "/" + Build.PRODUCT + "/" + Build.MANUFACTURER;
    }

    /**
     * 此处JNI创建NetWorkManager会调用
     *
     * @return 此处返回Service
     */
    @Override
    @NotProguard
    public Context getContext() {
        return mVpnService;
    }

    @NotProguard
    public boolean protect(int fd) {
        return mVpnService.protect(fd);
    }

    /**
     * Cache non DNS related information so we can recreate the builder without
     * that information when reestablishing IKE_SAs
     */
    public class BuilderCache {
        private final List<IPRange> mAddresses = new ArrayList<>();
        private final List<IPRange> mRoutesIPv4 = new ArrayList<>();
        private final List<IPRange> mRoutesIPv6 = new ArrayList<>();
        private final IPRangeSet mIncludedSubnetsv4 = new IPRangeSet();
        private final IPRangeSet mIncludedSubnetsv6 = new IPRangeSet();
        private final IPRangeSet mExcludedSubnets;
        private final int mSplitTunneling;
        private final VpnProfile.SelectedAppsHandling mAppHandling;
        private final SortedSet<String> mSelectedApps;
        private int mMtu;
        private boolean mIPv4Seen, mIPv6Seen;

        public BuilderCache(VpnProfile profile) {
            IPRangeSet included = IPRangeSet.fromString(profile.getIncludedSubnets());
            for (IPRange range : included) {
                if (range.getFrom() instanceof Inet4Address) {
                    mIncludedSubnetsv4.add(range);
                } else if (range.getFrom() instanceof Inet6Address) {
                    mIncludedSubnetsv6.add(range);
                }
            }
            mExcludedSubnets = IPRangeSet.fromString(profile.getExcludedSubnets());
            Integer splitTunneling = profile.getSplitTunneling();
            mSplitTunneling = splitTunneling != null ? splitTunneling : 0;
            VpnProfile.SelectedAppsHandling appHandling = profile.getSelectedAppsHandling();
            mSelectedApps = profile.getSelectedAppsSet();
            /* exclude our own app, otherwise the fetcher is blocked */
            switch (appHandling) {
                case SELECTED_APPS_DISABLE:
                    appHandling = VpnProfile.SelectedAppsHandling.SELECTED_APPS_EXCLUDE;
                    mSelectedApps.clear();
                    /* fall-through */
                case SELECTED_APPS_EXCLUDE:
                    mSelectedApps.add(mContext.getPackageName());
                    break;
                case SELECTED_APPS_ONLY:
                    mSelectedApps.remove(mContext.getPackageName());
                    break;
            }
            mAppHandling = appHandling;

            /* set a default MTU, will be set by the daemon for regular interfaces */
            Integer mtu = profile.getMTU();
            mMtu = mtu == null ? Constants.MTU_MAX : mtu;
        }

        public void addAddress(String address, int prefixLength) {
            try {
                mAddresses.add(new IPRange(address, prefixLength));
                recordAddressFamily(address);
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        public void addRoute(String address, int prefixLength) {
            try {
                if (isIPv6(address)) {
                    mRoutesIPv6.add(new IPRange(address, prefixLength));
                } else {
                    mRoutesIPv4.add(new IPRange(address, prefixLength));
                }
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        public void setMtu(int mtu) {
            mMtu = mtu;
        }

        public void recordAddressFamily(String address) {
            try {
                if (isIPv6(address)) {
                    mIPv6Seen = true;
                } else {
                    mIPv4Seen = true;
                }
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void applyData(VpnService.Builder builder) {
            for (IPRange address : mAddresses) {
                builder.addAddress(address.getFrom(), address.getPrefix());
            }
            /* add routes depending on whether split tunneling is allowed or not,
             * that is, whether we have to handle and block non-VPN traffic */
            if ((mSplitTunneling & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4) == 0) {
                if (mIPv4Seen) {    /* split tunneling is used depending on the routes and configuration */
                    IPRangeSet ranges = new IPRangeSet();
                    if (mIncludedSubnetsv4.size() > 0) {
                        ranges.add(mIncludedSubnetsv4);
                    } else {
                        ranges.addAll(mRoutesIPv4);
                    }
                    ranges.remove(mExcludedSubnets);
                    for (IPRange subnet : ranges.subnets()) {
                        try {
                            builder.addRoute(subnet.getFrom(), subnet.getPrefix());
                        } catch (IllegalArgumentException e) {    /* some Android versions don't seem to like multicast addresses here,
                         * ignore it for now */
                            if (!subnet.getFrom().isMulticastAddress()) {
                                throw e;
                            }
                        }
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {    /* allow traffic that would otherwise be blocked to bypass the VPN */
                    builder.allowFamily(OsConstants.AF_INET);
                }
            } else if (mIPv4Seen) {    /* only needed if we've seen any addresses.  otherwise, traffic
             * is blocked by default (we also install no routes in that case) */
                builder.addRoute("0.0.0.0", 0);
            }
            /* same thing for IPv6 */
            if ((mSplitTunneling & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6) == 0) {
                if (mIPv6Seen) {
                    IPRangeSet ranges = new IPRangeSet();
                    if (mIncludedSubnetsv6.size() > 0) {
                        ranges.add(mIncludedSubnetsv6);
                    } else {
                        ranges.addAll(mRoutesIPv6);
                    }
                    ranges.remove(mExcludedSubnets);
                    for (IPRange subnet : ranges.subnets()) {
                        try {
                            builder.addRoute(subnet.getFrom(), subnet.getPrefix());
                        } catch (IllegalArgumentException e) {
                            if (!subnet.getFrom().isMulticastAddress()) {
                                throw e;
                            }
                        }
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.allowFamily(OsConstants.AF_INET6);
                }
            } else if (mIPv6Seen) {
                builder.addRoute("::", 0);
            }
			if (builder != null && mAppFilter != null) {
				mAppFilter.applyFilter(mContext, builder);
			}
			builder.setMtu(mMtu);
        }

        private boolean isIPv6(String address) throws UnknownHostException {
            InetAddress addr = InetAddress.getByName(address);
            if (addr instanceof Inet4Address) {
                return false;
            } else if (addr instanceof Inet6Address) {
                return true;
            }
            return false;
        }
    }

    public static Intent getIntent(Context context) {
        Intent intent = new Intent(context, VPNConfig.ikev2VPNServiceClass);
        intent.putExtra(IVPNService.VPN_TYPE, IVPNService.VPN_TYPE_IKEV2);
        return intent;
    }

    private void stateTransform() {
        State state = mState;
        switch (state) {
            case CONNECTING:
                stateChange(VPNState.CONNECTING);
                break;
            case CONNECTED:
                stateChange(VPNState.CONNECTED);
                break;
            case DISABLED:
                stateChange(VPNState.DISCONNECTED);
                break;
            case DISCONNECTING:
                //stateChange(VPNState.DISCONNECTED);
                break;
        }
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

    private int getErrorText() {
        switch (mErrorState) {
            case AUTH_ERROR:
                if (mImcState == ImcState.BLOCK) {
                    return R.string.error_assessment_failed;
                } else {
                    return R.string.error_auth_failed;
                }
            case PEER_AUTH_ERROR:
                return R.string.error_peer_auth_failed;
            case LOOKUP_ERROR:
                return R.string.error_lookup_failed;
            case UNREACHABLE_ERROR:
                return R.string.error_unreachable;
            case PASSWORD_MISSING:
                return R.string.error_password_missing;
            case CERTIFICATE_UNAVAILABLE:
                return R.string.error_certificate_unavailable;
            default:
                return R.string.error_generic;
        }
    }

    private void notifyListeners(final Callable<Boolean> change) {
        mHandler.post(() -> {
            try {
                if (change.call()) {
                    stateChanged();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

	private void checkNotificationManager() {
		if (mNotificationManager == null) {
			throw new RuntimeException("Must set a notification manager , please call IVPN.addNotificationManager()");
		}
	}
}
