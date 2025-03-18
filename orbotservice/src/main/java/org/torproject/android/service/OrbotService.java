/* Copyright (c) 2009-2011, Nathan Freitas, Orbot / The Guardian Project - https://guardianproject.info/apps/orbot */
/* See LICENSE for licensing information */

package org.torproject.android.service;

import static org.torproject.android.service.OrbotConstants.*;
import static org.torproject.jni.TorService.ACTION_ERROR;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import net.freehaven.tor.control.TorControlCommands;
import net.freehaven.tor.control.TorControlConnection;

import org.torproject.android.service.util.Bridge;
import org.torproject.android.service.util.CustomTorResourceInstaller;
import org.torproject.android.service.util.PowerConnectionReceiver;
import org.torproject.android.service.util.Prefs;
import org.torproject.android.service.util.Utils;
import org.torproject.android.service.vpn.OrbotVpnManager;
import org.torproject.jni.TorService;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import IPtProxy.Controller;
import IPtProxy.IPtProxy;
import IPtProxy.SnowflakeProxy;
import IPtProxy.SnowflakeClientConnected;
import IPtProxy.OnTransportStopped;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
public class OrbotService extends VpnService {

    public final static String BINARY_TOR_VERSION = TorService.VERSION_NAME;

    static final int NOTIFY_ID = 1;
    private static final int ERROR_NOTIFY_ID = 3;

    //these will be set dynamically due to build flavors
    private static Uri V3_ONION_SERVICES_CONTENT_URI = null;//Uri.parse("content://org.torproject.android.ui.v3onionservice/v3");
    private static Uri V3_CLIENT_AUTH_URI = null;//Uri.parse("content://org.torproject.android.ui.v3onionservice.clientauth/v3auth");
    private final static String NOTIFICATION_CHANNEL_ID = "orbot_channel_1";
    private static final String[] V3_ONION_SERVICE_PROJECTION = new String[]{OnionService._ID, OnionService.NAME, OnionService.DOMAIN, OnionService.PORT, OnionService.ONION_PORT, OnionService.ENABLED, OnionService.PATH};
    private static final String[] V3_CLIENT_AUTH_PROJECTION = new String[]{V3ClientAuth._ID, V3ClientAuth.DOMAIN, V3ClientAuth.HASH, V3ClientAuth.ENABLED};

    public static int mPortSOCKS = -1;
    public static int mPortHTTP = -1;
    public static int mPortDns = -1;
    public static int mPortTrans = -1;
    public static File appBinHome;
    public static File appCacheHome;
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    OrbotRawEventListener mOrbotRawEventListener;
    OrbotVpnManager mVpnManager;
    Handler mHandler;
    ActionBroadcastReceiver mActionBroadcastReceiver;
    private String mCurrentStatus = STATUS_OFF;
    TorControlConnection conn = null;
    private ServiceConnection torServiceConnection;
    private boolean shouldUnbindTorService;
    private NotificationManager mNotificationManager = null;
    private NotificationCompat.Builder mNotifyBuilder;
    private File mV3OnionBasePath, mV3AuthBasePath;

    private PowerConnectionReceiver mPowerReceiver;

    private boolean mHasPower = false;
    private boolean mHasWifi = false;

    private static Controller mIptProxy = null;
    private SnowflakeProxy mSnowflakeProxy = null;

    public static synchronized Controller getIptProxyController (Context context) {

        if (mIptProxy == null) {
            mIptProxy = IPtProxy.newController(context.getCacheDir().getPath(), true, false, "DEBUG", new OnTransportStopped() {
                @Override
                public void stopped(String s, Exception e) {
                    Log.e(TAG, "IPtProxy Error", e);
                }
            });
        }

         return mIptProxy;
    }
    public void debug(String msg) {
        Log.d(TAG, msg);

        if (Prefs.useDebugLogging()) {
            sendCallbackLogMessage(msg);
        }
    }

    public void logException(String msg, Exception e) {
        if (Prefs.useDebugLogging()) {
            Log.e(TAG, msg, e);
            var baos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(baos));

            sendCallbackLogMessage(msg + '\n' + baos);
        } else sendCallbackLogMessage(msg);

    }

    private void showConnectedToTorNetworkNotification() {
        mNotifyBuilder.setProgress(0, 0, false);
        showToolbarNotification(getString(R.string.status_activated), NOTIFY_ID, R.drawable.ic_stat_tor);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        //this doesn't need to be shown to the user unless there is something to do
        debug(getString(R.string.log_notice_low_memory_warning));
    }

    private void clearNotifications() {
        if (mNotificationManager != null) mNotificationManager.cancelAll();

        if (mOrbotRawEventListener != null) mOrbotRawEventListener.getNodes().clear();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        var mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        var mChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        mChannel.setDescription(getString(R.string.app_description));
        mChannel.enableLights(false);
        mChannel.enableVibration(false);
        mChannel.setShowBadge(false);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        mNotificationManager.createNotificationChannel(mChannel);
    }

    @SuppressLint({"NewApi", "RestrictedApi"})
    protected void showToolbarNotification(String notifyMsg, int notifyType, int icon) {
        var intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        var pendIntent = PendingIntent.getActivity(OrbotService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (mNotifyBuilder == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotifyBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_tor).setContentIntent(pendIntent).setCategory(Notification.CATEGORY_SERVICE);
        }

        mNotifyBuilder.setOngoing(true);

        var title = getString(R.string.status_disabled);
        if (mCurrentStatus.equals(STATUS_STARTING) || notifyMsg.equals(getString(R.string.status_starting_up)))
            title = getString(R.string.status_starting_up);
        else if (mCurrentStatus.equals(STATUS_ON)) {
            title = getString(R.string.status_activated);
        }

        mNotifyBuilder.setContentTitle(title);

        mNotifyBuilder.mActions.clear(); // clear out any notification actions, if any
        if (conn != null && mCurrentStatus.equals(STATUS_ON)) { // only add new identity action when there is a connection
            var pendingIntentNewNym = PendingIntent.getBroadcast(this, 0, new Intent(TorControlCommands.SIGNAL_NEWNYM), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            mNotifyBuilder.addAction(R.drawable.ic_refresh_white_24dp, getString(R.string.menu_new_identity), pendingIntentNewNym);
        } else if (mCurrentStatus.equals(STATUS_OFF)) {
            var pendingIntentConnect = PendingIntent.getBroadcast(this, 0, new Intent(LOCAL_ACTION_NOTIFICATION_START), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            mNotifyBuilder.addAction(R.drawable.ic_stat_tor, getString(R.string.connect_to_tor), pendingIntentConnect);
        }

        mNotifyBuilder.setContentText(notifyMsg).setSmallIcon(icon).setTicker(notifyType != NOTIFY_ID ? notifyMsg : null);

        if (!mCurrentStatus.equals(STATUS_ON)) {
            mNotifyBuilder.setSubText(null);
        }

        if (!mCurrentStatus.equals(STATUS_STARTING)) {
            mNotifyBuilder.setProgress(0, 0, false); // removes progress bar
        }

        startForeground(NOTIFY_ID, mNotifyBuilder.build());
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent == null) {
                Log.d(TAG, "Got null onStartCommand() intent");
                return Service.START_REDELIVER_INTENT;
            }

            final boolean shouldStartVpnFromSystemIntent = !intent.getBooleanExtra(OrbotConstants.EXTRA_NOT_SYSTEM, false);

            if (mCurrentStatus.equals(STATUS_OFF))
                showToolbarNotification(getString(R.string.open_orbot_to_connect_to_tor), NOTIFY_ID, R.drawable.ic_stat_tor);

            if (shouldStartVpnFromSystemIntent) {
                Log.d(TAG, "Starting VPN from system intent: " + intent);
                showToolbarNotification(getString(R.string.status_starting_up), NOTIFY_ID, R.drawable.ic_stat_tor);
                if (VpnService.prepare(this) == null) {
                    // Power-user mode doesn't matter here. If the system is starting the VPN, i.e.
                    // via always-on VPN, we need to start it regardless.
                    Prefs.putUseVpn(true);
                    mExecutor.execute(new IncomingIntentRouter(new Intent(ACTION_START)));
                    mExecutor.execute(new IncomingIntentRouter(new Intent(ACTION_START_VPN)));
                } else {
                    Log.wtf(TAG, "Could not start VPN from system because it is not prepared, which should be impossible!");
                }
            } else {
                mExecutor.execute(new IncomingIntentRouter(intent));
            }
        } catch (RuntimeException re) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }

        return Service.START_REDELIVER_INTENT;
    }

    private void showDeactivatedNotification() {
        showToolbarNotification(getString(R.string.open_orbot_to_connect_to_tor), NOTIFY_ID, R.drawable.ic_stat_tor);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mActionBroadcastReceiver);

            unregisterReceiver(mPowerReceiver);

        } catch (IllegalArgumentException iae) {
            //not registered yet
        }
        super.onDestroy();
    }

    private void stopTorAsync(boolean showNotification) {
        debug("stopTorAsync");

        if (showNotification) sendCallbackLogMessage(getString(R.string.status_shutting_down));

        var connectionPathway = Prefs.getConnectionPathway();
        // todo this needs to handle a lot of different cases that haven't been defined yet
        // todo particularly this is true for the smart connection case...
        if (connectionPathway.startsWith(Prefs.PATHWAY_SNOWFLAKE) || Prefs.getPrefSmartTrySnowflake()) {
            mIptProxy.stop(IPtProxy.Snowflake);
        }
        else if (connectionPathway.equals(Prefs.PATHWAY_CUSTOM) || Prefs.getPrefSmartTryObfs4() != null) {
            mIptProxy.stop(IPtProxy.MeekLite);
            mIptProxy.stop(IPtProxy.Obfs4);
            mIptProxy.stop(IPtProxy.Webtunnel);
        }

        stopTor();

        //stop the foreground priority and make sure to remove the persistent notification
        stopForeground(!showNotification);

        if (showNotification) sendCallbackLogMessage(getString(R.string.status_disabled));

        mPortDns = -1;
        mPortSOCKS = -1;
        mPortHTTP = -1;
        mPortTrans = -1;

        if (!showNotification) {
            clearNotifications();
            stopSelf();
        }
    }

    private void stopTorOnError(String message) {
        stopTorAsync(false);
        showToolbarNotification(getString(R.string.unable_to_start_tor) + ": " + message, ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
    }

    private static HashMap<String, String> mFronts;
    private static List<String> mSnowflakeBridges;

    public static void loadSnowflakeBridges(Context context) {
        if (mSnowflakeBridges != null) return;
        mSnowflakeBridges = new ArrayList<>();
        try {
            var reader = new BufferedReader(new InputStreamReader(context.getAssets().open("snowflake-brokers")));
            String line;
            while ((line =reader.readLine()) != null) mSnowflakeBridges.add(line);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadCdnFronts(Context context) {
        if (mFronts == null) {
            mFronts = new HashMap<>();

            try {
                var reader = new BufferedReader(new InputStreamReader(context.getAssets().open("fronts")));
                String line;
                while ((line = reader.readLine()) != null) {
                    int spaceIdx = line.indexOf(' ');
                    String key = line.substring(0, spaceIdx);
                    String val = line.substring(spaceIdx + 1);
                    mFronts.put(key, val);
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getCdnFront(String service) {
        return mFronts.get(service);
    }


    private void startSnowflakeClientDomainFronting() {
        //this is using the current, default Tor snowflake infrastructure
        var target = getCdnFront("snowflake-target");
        var front = getCdnFront("snowflake-front");
        var stunServer = getCdnFront("snowflake-stun");

        /*
        // @param ice Comma-separated list of ICE servers.
        // @param url URL of signaling broker.
        // @param fronts Comma-separated list of front domains.
        // @param ampCache OPTIONAL. URL of AMP cache to use as a proxy for signaling.
        //	Only needed when you want to do the rendezvous over AMP instead of a domain fronted server.
        // @param sqsQueueURL OPTIONAL. URL of SQS Queue to use as a proxy for signaling.
        // @param sqsCredsStr OPTIONAL. Credentials to access SQS Queue.
        // @param logFile Name of log file. OPTIONAL. Defaults to no log.
        // @param logToStateDir Resolve the log file relative to Tor's PT state dir.
        // @param keepLocalAddresses Keep local LAN address ICE candidates.
        // @param unsafeLogging Prevent logs from being scrubbed.
        // @param maxPeers Capacity for number of multiplexed WebRTC peers. DEFAULTs to 1 if less than that.
        // @return Port number where Snowflake will listen on, if no error happens during start up.
         */
       // IPtProxy.startSnowflake(stunServer, target, front, null, null, null, null,true, false, false, 1);


        try {
            mIptProxy.setSnowflakeBrokerUrl(target);
            mIptProxy.setSnowflakeFrontDomains(front);
            mIptProxy.setSnowflakeIceServers(stunServer);
            mIptProxy.setSnowflakeMaxPeers(1);
            mIptProxy.start(IPtProxy.Snowflake,"");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void startSnowflakeClientAmpRendezvous() {
        var stunServers = getCdnFront("snowflake-stun");
        var target = getCdnFront("snowflake-target-direct");
        var front = getCdnFront("snowflake-amp-front");
        var ampCache = getCdnFront("snowflake-amp-cache");
        //IPtProxy.startSnowflake(stunServers, target, front, ampCache, null, null, null, true, false, false, 1);


        try {
            mIptProxy.setSnowflakeBrokerUrl(target);
            mIptProxy.setSnowflakeFrontDomains(front);
            mIptProxy.setSnowflakeIceServers(stunServers);
            mIptProxy.setSnowflakeAmpCacheUrl(ampCache);
            mIptProxy.setSnowflakeMaxPeers(1);
            mIptProxy.start(IPtProxy.Snowflake,"");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final SecureRandom mSecureRandGen = new SecureRandom(); //used to randomly select STUN servers for snowflake

    public synchronized void enableSnowflakeProxy() { // This is to host a snowflake entrance node / bridge

            if (Prefs.limitSnowflakeProxyingWifi() && (!mHasWifi))
                return;

            if (Prefs.limitSnowflakeProxyingCharging() && (!mHasPower))
                return;

            if (mSnowflakeProxy == null) {
                var capacity = 1;
                var pollInterval = 120;
                var stunServers = getCdnFront("snowflake-stun").split(",");

                int randomIndex = mSecureRandGen.nextInt(stunServers.length);
                var stunUrl = stunServers[randomIndex];
                var relayUrl = getCdnFront("snowflake-relay-url");
                var natProbeUrl = getCdnFront("snowflake-nat-probe");
                var brokerUrl = getCdnFront("snowflake-target-direct");

                mSnowflakeProxy = new SnowflakeProxy();
                mSnowflakeProxy.setBrokerUrl(brokerUrl);
                mSnowflakeProxy.setCapacity(capacity);
                mSnowflakeProxy.setPollInterval(pollInterval);
                mSnowflakeProxy.setRelayUrl(relayUrl);
                mSnowflakeProxy.setStunServer(stunUrl);
                mSnowflakeProxy.setNatProbeUrl(natProbeUrl);


                mSnowflakeProxy.setClientConnected( new SnowflakeClientConnected (){

                    @Override
                    public void connected() {
                        snowflakeProxyClientConnected();
                    }

                });
                mSnowflakeProxy.start();


            }

            logNotice(getString(R.string.log_notice_snowflake_proxy_enabled));

            if (Prefs.showSnowflakeProxyMessage()) {
                var message = getString(R.string.log_notice_snowflake_proxy_enabled);
                new Handler(getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
            }


    }

    private void snowflakeProxyClientConnected () {
        Prefs.addSnowflakeServed();
        if (!Prefs.showSnowflakeProxyMessage()) return;
        var message = String.format(getString(R.string.snowflake_proxy_client_connected_msg), ONION_EMOJI, ONION_EMOJI);
        new Handler(getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    private void enableSnowflakeProxyNetworkListener () {
        if (Prefs.limitSnowflakeProxyingWifi()) {
            //check if on wifi
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connMgr.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        super.onAvailable(network);
                        checkNetworkForSnowflakeProxy ();
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        super.onLost(network);
                        checkNetworkForSnowflakeProxy ();
                    }
                });
            }
        }
    }

    public void setHasPower (boolean hasPower) {
        mHasPower = hasPower;
        if (Prefs.beSnowflakeProxy()) {
            if (Prefs.limitSnowflakeProxyingCharging()) {
                if (mHasPower) enableSnowflakeProxy();
                else disableSnowflakeProxy();
            }
        }
    }
    private void checkNetworkForSnowflakeProxy () {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NetworkCapabilities netCap = connMgr.getNetworkCapabilities(connMgr.getActiveNetwork());
            if (netCap != null)
                mHasWifi = netCap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            else
                mHasWifi = false;
        }
        else {
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            if (netInfo != null)
                mHasWifi = netInfo.getType() == ConnectivityManager.TYPE_WIFI;
        }

        if (Prefs.beSnowflakeProxy()) {
            if (Prefs.limitSnowflakeProxyingWifi()) {
                if (mHasWifi) enableSnowflakeProxy();
                else disableSnowflakeProxy();
            }
        }
    }

    public synchronized void disableSnowflakeProxy() {

        if (mSnowflakeProxy != null) {
            mSnowflakeProxy.stop();
            logNotice(getString(R.string.log_notice_snowflake_proxy_disabled));

            if (Prefs.showSnowflakeProxyMessage()) {
                var message = getString(R.string.log_notice_snowflake_proxy_disabled);
                new Handler(getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
            }
            mSnowflakeProxy = null;
        }


    }

    // if someone stops during startup, we may have to wait for the conn port to be setup, so we can properly shutdown tor
    private void stopTor() {
        if (shouldUnbindTorService) {
            debug("unbinding tor service");
            unbindService(torServiceConnection); //unbinding from the tor service will stop tor
            shouldUnbindTorService = false;
            conn = null;
        } else {
            sendLocalStatusOffBroadcast();
        }
    }

    private void requestTorRereadConfig() {
        try {
            if (conn != null) {
                conn.signal(TorControlCommands.SIGNAL_RELOAD);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void logNotice(String msg) {
        if (msg != null && msg.trim().length() > 0) {
            if (Prefs.useDebugLogging()) Log.d(TAG, msg);
            sendCallbackLogMessage(msg);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onCreate() {
        super.onCreate();
        configLanguage();

        getIptProxyController(this);

        try {
            //set proper content URIs for current build flavor
            V3_ONION_SERVICES_CONTENT_URI = Uri.parse("content://" + getApplicationContext().getPackageName() + ".ui.v3onionservice/v3");
            V3_CLIENT_AUTH_URI = Uri.parse("content://" + getApplicationContext().getPackageName() + ".ui.v3onionservice.clientauth/v3auth");

            try {
                mHandler = new Handler();

                appBinHome = getFilesDir();
                if (!appBinHome.exists()) appBinHome.mkdirs();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appCacheHome = new File(getDataDir(), DIRECTORY_TOR_DATA);
                } else {
                    appCacheHome = getDir(DIRECTORY_TOR_DATA, Application.MODE_PRIVATE);
                }

                if (!appCacheHome.exists()) appCacheHome.mkdirs();

                mV3OnionBasePath = new File(getFilesDir().getAbsolutePath(), ONION_SERVICES_DIR);
                if (!mV3OnionBasePath.isDirectory()) mV3OnionBasePath.mkdirs();

                mV3AuthBasePath = new File(getFilesDir().getAbsolutePath(), V3_CLIENT_AUTH_DIR);
                if (!mV3AuthBasePath.isDirectory()) mV3AuthBasePath.mkdirs();

                if (mNotificationManager == null) {
                    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                }

                IntentFilter filter = new IntentFilter(TorControlCommands.SIGNAL_NEWNYM);
                filter.addAction(CMD_ACTIVE);
                filter.addAction(ACTION_STATUS);
                filter.addAction(ACTION_ERROR);
                filter.addAction(LOCAL_ACTION_NOTIFICATION_START);

                mActionBroadcastReceiver = new ActionBroadcastReceiver();
                ContextCompat.registerReceiver(this, mActionBroadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel();

                var hasGeoip = new File(appBinHome, GEOIP_ASSET_KEY).exists();
                var hasGeoip6 = new File(appBinHome, GEOIP6_ASSET_KEY).exists();

                // only write out geoip files if there's an app update or they don't exist
                if (!hasGeoip || !hasGeoip6 || Prefs.isGeoIpReinstallNeeded()) {
                    try {
                        Log.d(TAG, "Installing geoip files...");
                        new CustomTorResourceInstaller(this, appBinHome).installGeoIP();
                        Prefs.setIsGeoIpReinstallNeeded(false);
                    } catch (IOException io) { // user has < 10MB free space on disk...
                        Log.e(TAG, "Error installing geoip files", io);
                    }
                }

                mVpnManager = new OrbotVpnManager(this);
                loadSnowflakeBridges(this);
                loadCdnFronts(this);
            } catch (Exception e) {
                Log.e(TAG, "Error setting up Orbot", e);
                logNotice(getString(R.string.couldn_t_start_tor_process_) + " " + e.getClass().getSimpleName());
            }

            mPowerReceiver = new PowerConnectionReceiver(this);

            IntentFilter ifilter = new IntentFilter();
            ifilter.addAction(Intent.ACTION_POWER_CONNECTED);
            ifilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            registerReceiver(mPowerReceiver, ifilter);

            enableSnowflakeProxyNetworkListener();

            if (Prefs.beSnowflakeProxy()
                    && !(Prefs.limitSnowflakeProxyingCharging() || Prefs.limitSnowflakeProxyingWifi()))
                enableSnowflakeProxy();

        } catch (RuntimeException re) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    private void configLanguage() {
        Configuration config = getBaseContext().getResources().getConfiguration();
        Locale locale = new Locale(Prefs.getDefaultLocale());
        Locale.setDefault(locale);
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
    }

    protected String getCurrentStatus() {
        return mCurrentStatus;
    }

    private File updateTorrcCustomFile() throws IOException {
        var prefs = Prefs.getSharedPrefs(getApplicationContext());
        var extraLines = new StringBuffer();

        extraLines.append("\n");
        extraLines.append("RunAsDaemon 0").append('\n');
        extraLines.append("AvoidDiskWrites 1").append('\n');

        var socksPortPref = prefs.getString(PREF_SOCKS, SOCKS_PROXY_PORT_DEFAULT);
        if (socksPortPref.indexOf(':') != -1) socksPortPref = socksPortPref.split(":")[1];

        socksPortPref = checkPortOrAuto(socksPortPref);

        var httpPortPref = prefs.getString(PREF_HTTP, HTTP_PROXY_PORT_DEFAULT);
        if (httpPortPref.indexOf(':') != -1) httpPortPref = httpPortPref.split(":")[1];

        httpPortPref = checkPortOrAuto(httpPortPref);

        var isolate = "";
        if (prefs.getBoolean(PREF_ISOLATE_DEST, false)) {
            isolate += " IsolateDestAddr ";
        }
        if (prefs.getBoolean(PREF_ISOLATE_PORT, false)) {
            isolate += " IsolateDestPort ";
        }
        if (prefs.getBoolean(PREF_ISOLATE_PROTOCOL, false)) {
            isolate += " IsolateClientProtocol ";
        }

        var ipv6Pref = "";
        if (prefs.getBoolean(PREF_PREFER_IPV6, true)) {
            ipv6Pref += " IPv6Traffic PreferIPv6 ";
        }

        if (prefs.getBoolean(PREF_DISABLE_IPV4, false)) {
            ipv6Pref += " IPv6Traffic NoIPv4Traffic ";
        }

        if (!Prefs.openProxyOnAllInterfaces()) {
            extraLines.append("SOCKSPort ").append(socksPortPref).append(isolate).append(ipv6Pref).append('\n');
        } else {
            extraLines.append("SOCKSPort 0.0.0.0:").append(socksPortPref).append(ipv6Pref).append(isolate).append("\n");
            extraLines.append("SocksPolicy accept *:*").append('\n');
        }

        extraLines.append("SafeSocks 0").append('\n');
        extraLines.append("TestSocks 0").append('\n');
        extraLines.append("HTTPTunnelPort ").append(httpPortPref).append(isolate).append('\n');


        if (prefs.getBoolean(PREF_CONNECTION_PADDING, false)) {
            extraLines.append("ConnectionPadding 1").append('\n');
        }

        if (prefs.getBoolean(PREF_REDUCED_CONNECTION_PADDING, true)) {
            extraLines.append("ReducedConnectionPadding 1").append('\n');
        }

        if (prefs.getBoolean(PREF_CIRCUIT_PADDING, true)) {
            extraLines.append("CircuitPadding 1").append('\n');
        } else {
            extraLines.append("CircuitPadding 0").append('\n');
        }

        if (prefs.getBoolean(PREF_REDUCED_CIRCUIT_PADDING, true)) {
            extraLines.append("ReducedCircuitPadding 1").append('\n');
        }

        var transPort = prefs.getString("pref_transport", TOR_TRANSPROXY_PORT_DEFAULT + "");
        var dnsPort = prefs.getString("pref_dnsport", TOR_DNS_PORT_DEFAULT + "");

        extraLines.append("TransPort ").append(checkPortOrAuto(transPort)).append(isolate).append('\n');
        extraLines.append("DNSPort ").append(checkPortOrAuto(dnsPort)).append(isolate).append('\n');

        extraLines.append("VirtualAddrNetwork 10.192.0.0/10").append('\n');
        extraLines.append("AutomapHostsOnResolve 1").append('\n');

        extraLines.append("DormantClientTimeout 10 minutes").append('\n');
        // extraLines.append("DormantOnFirstStartup 0").append('\n');
        extraLines.append("DormantCanceledByStartup 1").append('\n');

        extraLines.append("DisableNetwork 0").append('\n');

        if (Prefs.useDebugLogging()) {
            extraLines.append("Log debug syslog").append('\n');
            extraLines.append("SafeLogging 0").append('\n');
        }

        extraLines = processSettingsImpl(extraLines);

        if (extraLines == null) return null;

        extraLines.append('\n');
        extraLines.append(prefs.getString("pref_custom_torrc", "")).append('\n');

        logNotice(getString(R.string.log_notice_updating_torrc));

        debug("torrc.custom=" + extraLines);

        var fileTorRcCustom = TorService.getTorrc(this);
        updateTorConfigCustom(fileTorRcCustom, extraLines.toString(), false);
        return fileTorRcCustom;
    }

    private String checkPortOrAuto(String portString) {
        if (!portString.equalsIgnoreCase("auto")) {
            var isPortUsed = true;
            var port = Integer.parseInt(portString);

            while (isPortUsed) {
                isPortUsed = Utils.isPortOpen("127.0.0.1", port, 500);

                if (isPortUsed) //the specified port is not available, so let Tor find one instead
                    port++;
            }
            return port + "";
        }

        return portString;
    }

    public void updateTorConfigCustom(File fileTorRcCustom, String extraLines, boolean append) throws IOException {
        var ps = new PrintWriter(new FileWriter(fileTorRcCustom, append));
        ps.print(extraLines);
        ps.flush();
        ps.close();
    }

    /**
     * Send Orbot's status in reply to an
     * {@link #ACTION_START} {@link Intent}, targeted only to
     * the app that sent the initial request. If the user has disabled auto-
     * starts, the reply {@code ACTION_START Intent} will include the extra
     * {@link #STATUS_STARTS_DISABLED}
     */
    private void replyWithStatus(Intent startRequest) {
        String packageName = startRequest.getStringExtra(EXTRA_PACKAGE_NAME);

        Intent reply = new Intent(ACTION_STATUS);
        reply.putExtra(EXTRA_STATUS, mCurrentStatus);
        reply.putExtra(EXTRA_SOCKS_PROXY, "socks://127.0.0.1:" + mPortSOCKS);
        reply.putExtra(EXTRA_SOCKS_PROXY_HOST, "127.0.0.1");
        reply.putExtra(EXTRA_SOCKS_PROXY_PORT, mPortSOCKS);
        reply.putExtra(EXTRA_HTTP_PROXY, "http://127.0.0.1:" + mPortHTTP);
        reply.putExtra(EXTRA_HTTP_PROXY_HOST, "127.0.0.1");
        reply.putExtra(EXTRA_HTTP_PROXY_PORT, mPortHTTP);
        reply.putExtra(EXTRA_DNS_PORT, mPortDns);

        if (packageName != null) {
            reply.setPackage(packageName);
            sendBroadcast(reply);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(reply.setAction(LOCAL_ACTION_STATUS));

        if (mPortSOCKS != -1 && mPortHTTP != -1)
            sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);

    }

    private boolean showTorServiceErrorMsg = false;

    private static final int TIMEOUT_MS = 15000;

    /**
     * The entire process for starting tor and related services is run from this method.
     */
    private void startTor() {
        try {
            if (torServiceConnection != null && conn != null) {
                sendCallbackLogMessage(getString(R.string.log_notice_ignoring_start_request));
                showConnectedToTorNetworkNotification();
                return;
            }

            mNotifyBuilder.setProgress(100, 0, false);
            showToolbarNotification("", NOTIFY_ID, R.drawable.ic_stat_tor);

            if (Prefs.getConnectionPathway().equals(Prefs.PATHWAY_SMART)) {
                smartConnectionPathwayStartTor();
            }
            startTorService();
            showTorServiceErrorMsg = true;

            if (Prefs.hostOnionServicesEnabled()) {
                try {
                    updateV3OnionNames();
                } catch (SecurityException se) {
                    logNotice(getString(R.string.log_notice_unable_to_update_onions));
                }
            }
        } catch (Exception e) {
            logException(getString(R.string.unable_to_start_tor) + " " + e.getLocalizedMessage(), e);
            stopTorOnError(e.getLocalizedMessage());
        }
    }

    static int TRIES_DELETE = 0;

    private void smartConnectionPathwayStartTor() {
        Log.d(TAG, "timing out in " + TIMEOUT_MS + "ms");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "timed out mCurrentStatus=" + mCurrentStatus);
            if (!mCurrentStatus.equals(STATUS_ON)) {
                Log.d(TAG, "stopping tor...");
                if (Prefs.getPrefSmartTrySnowflake()) {
                    Log.d(TAG, "trying snowflake didnt work");
                    clearEphemeralSmartConnectionSettings();
                    sendSmartStatusToActivity(SMART_STATUS_CIRCUMVENTION_ATTEMPT_FAILED);
                } else if (Prefs.getPrefSmartTryObfs4() != null) {
                    Log.d(TAG, "trying obfs4 didnt work");
                    clearEphemeralSmartConnectionSettings();
                    sendSmartStatusToActivity(SMART_STATUS_CIRCUMVENTION_ATTEMPT_FAILED);
                } else {
                    sendSmartStatusToActivity(SMART_STATUS_NO_DIRECT);
                }
                stopTorAsync(true);
            } else {
                // tor was connected in the allotted time
                var obfs4 = Prefs.getPrefSmartTryObfs4();
                if (obfs4 != null) {
                    // set these obfs4 bridges
                    Prefs.setBridgesList(obfs4);
                    Prefs.putConnectionPathway(Prefs.PATHWAY_CUSTOM);
                } else if (Prefs.getPrefSmartTrySnowflake()) {
                    // set snowflake
                    Prefs.putConnectionPathway(Prefs.PATHWAY_SNOWFLAKE);
                }
                clearEphemeralSmartConnectionSettings();
            }
        }, ((TRIES_DELETE++) != 2) ? TIMEOUT_MS : 10000);
    }

    private void clearEphemeralSmartConnectionSettings() {
        Prefs.putPrefSmartTryObfs4(null);
        Prefs.putPrefSmartTrySnowflake(false);
    }

    private void sendSmartStatusToActivity(String status) {
        var intent = new Intent(LOCAL_ACTION_SMART_CONNECT_EVENT).putExtra(LOCAL_EXTRA_SMART_STATUS, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    private void updateV3OnionNames() throws SecurityException {
        var contentResolver = getApplicationContext().getContentResolver();
        var onionServices = contentResolver.query(V3_ONION_SERVICES_CONTENT_URI, null, null, null, null);
        if (onionServices != null) {
            try {
                while (onionServices.moveToNext()) {
                    var domain_index = onionServices.getColumnIndex(OnionService.DOMAIN);
                    var path_index = onionServices.getColumnIndex(OnionService.PATH);
                    var id_index = onionServices.getColumnIndex(OnionService._ID);
                    if (domain_index < 0 || path_index < 0 || id_index < 0) continue;
                    var domain = onionServices.getString(domain_index);
                    if (domain == null || TextUtils.isEmpty(domain)) {
                        var path = onionServices.getString(path_index);
                        var v3OnionDirPath = new File(mV3OnionBasePath.getAbsolutePath(), path).getCanonicalPath();
                        var hostname = new File(v3OnionDirPath, "hostname");
                        if (hostname.exists()) {
                            int id = onionServices.getInt(id_index);
                            domain = Utils.readInputStreamAsString(new FileInputStream(hostname)).trim();
                            var fields = new ContentValues();
                            fields.put(OnionService.DOMAIN, domain);
                            contentResolver.update(V3_ONION_SERVICES_CONTENT_URI, fields, OnionService._ID + "=" + id, null);
                        }
                    }
                }
                /*
                This old status hack is temporary and fixes the issue reported by syphyr at
                https://github.com/guardianproject/orbot/pull/556
                Down the line a better approach needs to happen for sending back the onion names updated
                status, perhaps just adding it as an extra to the normal Intent callback...
                 */
                var oldStatus = mCurrentStatus;
                var intent = new Intent(LOCAL_ACTION_V3_NAMES_UPDATED);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                mCurrentStatus = oldStatus;
            } catch (Exception e) {
                e.printStackTrace();
            }
            onionServices.close();
        }
    }

    private synchronized void startTorService() throws Exception {
        updateTorConfigCustom(TorService.getDefaultsTorrc(this), """
                DNSPort 0
                TransPort 0
                DisableNetwork 1
                """, false);

        var fileTorrcCustom = updateTorrcCustomFile();
        if ((!fileTorrcCustom.exists()) || (!fileTorrcCustom.canRead())) return;

        sendCallbackLogMessage(getString(R.string.status_starting_up));

        torServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

                //moved torService to a local variable, since we only need it once
                TorService torService = ((TorService.LocalBinder) iBinder).getService();

                while ((conn = torService.getTorControlConnection()) == null) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                //wait another second before we set our own event listener
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mOrbotRawEventListener = new OrbotRawEventListener(OrbotService.this);

                if (conn != null) {
                    try {
                        initControlConnection();
                        if (conn == null)
                            return; // maybe there was an error setting up the control connection

                        //override the TorService event listener
                        conn.addRawEventListener(mOrbotRawEventListener);

                        logNotice(getString(R.string.log_notice_connected_to_tor_control_port));

                        //now set our own events
                        ArrayList<String> events = new ArrayList<>(Arrays.asList(TorControlCommands.EVENT_OR_CONN_STATUS, TorControlCommands.EVENT_CIRCUIT_STATUS, TorControlCommands.EVENT_NOTICE_MSG, TorControlCommands.EVENT_WARN_MSG, TorControlCommands.EVENT_ERR_MSG, TorControlCommands.EVENT_BANDWIDTH_USED, TorControlCommands.EVENT_NEW_DESC, TorControlCommands.EVENT_ADDRMAP));
                        if (Prefs.useDebugLogging()) {
                            events.add(TorControlCommands.EVENT_DEBUG_MSG);
                            events.add(TorControlCommands.EVENT_INFO_MSG);
                        }

                        if (Prefs.useDebugLogging())
                            events.add(TorControlCommands.EVENT_STREAM_STATUS);

                        conn.setEvents(events);
                        logNotice(getString(R.string.log_notice_added_event_handler));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                if (Prefs.useDebugLogging()) Log.d(TAG, "TorService: onServiceDisconnected");
                sendLocalStatusOffBroadcast();
            }

            @Override
            public void onNullBinding(ComponentName componentName) {
                Log.w(TAG, "TorService: was unable to bind: onNullBinding");
            }

            @Override
            public void onBindingDied(ComponentName componentName) {
                Log.w(TAG, "TorService: onBindingDied");
                sendLocalStatusOffBroadcast();
            }
        };

        Intent serviceIntent = new Intent(this, TorService.class);
        debug("binding tor service");
        if (Build.VERSION.SDK_INT < 29) {
            shouldUnbindTorService = bindService(serviceIntent, torServiceConnection, BIND_AUTO_CREATE);
        } else {
            shouldUnbindTorService = bindService(serviceIntent, BIND_AUTO_CREATE, mExecutor, torServiceConnection);
        }
    }

    private void sendLocalStatusOffBroadcast() {
        var localOffStatus = new Intent(LOCAL_ACTION_STATUS).putExtra(EXTRA_STATUS, STATUS_OFF);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localOffStatus);
    }

    protected void exec(Runnable run) {
        mExecutor.execute(run);
    }

    private void initControlConnection() {
        if (conn != null) {
            try {
                String confSocks = conn.getInfo("net/listeners/socks");
                StringTokenizer st = new StringTokenizer(confSocks, " ");
                if (confSocks.trim().isEmpty()) {
                    mPortSOCKS = 0;
                } else {
                    confSocks = st.nextToken().split(":")[1];
                    confSocks = confSocks.substring(0, confSocks.length() - 1);
                    mPortSOCKS = Integer.parseInt(confSocks);
                }
                String confHttp = conn.getInfo("net/listeners/httptunnel");
                if (confHttp.trim().isEmpty()) {
                    mPortHTTP = 0;
                } else {
                    st = new StringTokenizer(confHttp, " ");
                    confHttp = st.nextToken().split(":")[1];
                    confHttp = confHttp.substring(0, confHttp.length() - 1);
                    mPortHTTP = Integer.parseInt(confHttp);
                }
                String confDns = conn.getInfo("net/listeners/dns");
                st = new StringTokenizer(confDns, " ");
                if (st.hasMoreTokens()) {
                    confDns = st.nextToken().split(":")[1];
                    confDns = confDns.substring(0, confDns.length() - 1);
                    mPortDns = Integer.parseInt(confDns);
                    Prefs.getSharedPrefs(getApplicationContext()).edit().putInt(PREFS_DNS_PORT, mPortDns).apply();
                }

                String confTrans = conn.getInfo("net/listeners/trans");
                st = new StringTokenizer(confTrans, " ");
                if (st.hasMoreTokens()) {
                    confTrans = st.nextToken().split(":")[1];
                    confTrans = confTrans.substring(0, confTrans.length() - 1);
                    mPortTrans = Integer.parseInt(confTrans);
                }

                sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);

            } catch (IOException e) {
                e.printStackTrace();
                stopTorOnError(e.getLocalizedMessage());
                conn = null;
            } catch (NullPointerException npe) {
                Log.e(TAG, "NPE reached... how???");
                npe.printStackTrace();
                stopTorOnError("stopping from NPE");
                conn = null;
            }
        }
    }

    public void sendSignalActive() {
        if (conn != null && mCurrentStatus.equals(STATUS_ON)) {
            try {
                conn.signal("ACTIVE");
            } catch (IOException e) {
                debug("error send active: " + e.getLocalizedMessage());
            }
        }
    }

    public void newIdentity() {
        if (conn == null) return;
        new Thread() {
            public void run() {
                try {
                    if (conn != null && mCurrentStatus.equals(STATUS_ON)) {
                        mNotifyBuilder.setSubText(null); // clear previous exit node info if present
                        showToolbarNotification(getString(R.string.newnym), NOTIFY_ID, R.drawable.ic_stat_tor);
                        conn.signal(TorControlCommands.SIGNAL_NEWNYM);
                    }
                } catch (Exception ioe) {
                    debug("error requesting newnym: " + ioe.getLocalizedMessage());
                }
            }
        }.start();
    }

    protected void sendCallbackBandwidth(long lastWritten, long lastRead, long totalWritten, long totalRead) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(LOCAL_ACTION_BANDWIDTH).putExtra(LOCAL_EXTRA_TOTAL_WRITTEN, totalWritten).putExtra(LOCAL_EXTRA_TOTAL_READ, totalRead).putExtra(LOCAL_EXTRA_LAST_WRITTEN, lastWritten).putExtra(LOCAL_EXTRA_LAST_READ, lastRead));
    }

    private void sendCallbackLogMessage(final String logMessage) {
        var notificationMessage = logMessage;
        var localIntent = new Intent(LOCAL_ACTION_LOG).putExtra(LOCAL_EXTRA_LOG, logMessage);
        if (logMessage.contains(LOG_NOTICE_HEADER)) {
            notificationMessage = notificationMessage.substring(LOG_NOTICE_HEADER.length());
            if (notificationMessage.contains(LOG_NOTICE_BOOTSTRAPPED)) {
                var percent = notificationMessage.substring(LOG_NOTICE_BOOTSTRAPPED.length());
                percent = percent.substring(0, percent.indexOf('%')).trim();
                localIntent.putExtra(LOCAL_EXTRA_BOOTSTRAP_PERCENT, percent);
                mNotifyBuilder.setProgress(100, Integer.parseInt(percent), false);
                notificationMessage = notificationMessage.substring(notificationMessage.indexOf(':') + 1).trim();
            }
        }
        showToolbarNotification(notificationMessage, NOTIFY_ID, R.drawable.ic_stat_tor);
        mHandler.post(() -> LocalBroadcastManager.getInstance(OrbotService.this).sendBroadcast(localIntent));
    }

    private void sendCallbackPorts(int socksPort, int httpPort, int dnsPort, int transPort) {
        var intent = new Intent(LOCAL_ACTION_PORTS).putExtra(EXTRA_SOCKS_PROXY_PORT, socksPort).putExtra(EXTRA_HTTP_PROXY_PORT, httpPort).putExtra(EXTRA_DNS_PORT, dnsPort).putExtra(EXTRA_TRANS_PORT, transPort);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        if (Prefs.useVpn() && mVpnManager != null) mVpnManager.handleIntent(new Builder(), intent);
    }

    private StringBuffer processSettingsImpl(StringBuffer extraLines) throws IOException {
        logNotice(getString(R.string.updating_settings_in_tor_service));
        var prefs = Prefs.getSharedPrefs(getApplicationContext());
        var becomeRelay = prefs.getBoolean(OrbotConstants.PREF_OR, false);
        var ReachableAddresses = prefs.getBoolean(OrbotConstants.PREF_REACHABLE_ADDRESSES, false);
        var enableStrictNodes = prefs.getBoolean("pref_strict_nodes", false);
        var entranceNodes = prefs.getString("pref_entrance_nodes", "");
        var exitNodes = prefs.getString("pref_exit_nodes", "");
        var excludeNodes = prefs.getString("pref_exclude_nodes", "");

        String pathway = Prefs.getConnectionPathway();
        if (pathway.equals(Prefs.PATHWAY_SMART)) {
            // todo for now ...
        } else if (pathway.equals(Prefs.PATHWAY_DIRECT)) {
            extraLines = processSettingsImplDirectPathway(extraLines);
        } else {
            // snowflake or obfs4
            extraLines.append("UseBridges 1").append('\n');
            if (pathway.startsWith(Prefs.PATHWAY_SNOWFLAKE) || Prefs.getPrefSmartTrySnowflake()) {
                extraLines = processSettingsImplSnowflake(extraLines);
            } else if (pathway.equals(Prefs.PATHWAY_CUSTOM) || Prefs.getPrefSmartTryObfs4() != null) {
                extraLines = processSettingsLyrebird(extraLines);
            }
        }
        var fileGeoIP = new File(appBinHome, GEOIP_ASSET_KEY);
        var fileGeoIP6 = new File(appBinHome, GEOIP6_ASSET_KEY);

        if (fileGeoIP.exists()) { // only apply geoip if it exists
            extraLines.append("GeoIPFile" + ' ').append(fileGeoIP.getCanonicalPath()).append('\n');
            extraLines.append("GeoIPv6File" + ' ').append(fileGeoIP6.getCanonicalPath()).append('\n');
        }

        if (!TextUtils.isEmpty(entranceNodes))
            extraLines.append("EntryNodes ").append(entranceNodes).append('\n');

        if (!TextUtils.isEmpty(exitNodes))
            extraLines.append("ExitNodes ").append(exitNodes).append('\n');

        if (!TextUtils.isEmpty(excludeNodes))
            extraLines.append("ExcludeNodes ").append(excludeNodes).append('\n');

        extraLines.append("StrictNodes ").append(enableStrictNodes ? "1" : "0").append('\n');

        extraLines.append("\n");

        try {
            if (ReachableAddresses) {
                var ReachableAddressesPorts = prefs.getString(PREF_REACHABLE_ADDRESSES_PORTS, "*:80,*:443");
                extraLines.append("ReachableAddresses" + ' ').append(ReachableAddressesPorts).append('\n');
            }

        } catch (Exception e) {
            showToolbarNotification(getString(R.string.your_reachableaddresses_settings_caused_an_exception_), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
            return null;
        }

        try {
            if (becomeRelay && (!Prefs.bridgesEnabled()) && (!ReachableAddresses)) {
                var ORPort = Integer.parseInt(Objects.requireNonNull(prefs.getString(PREF_OR_PORT, "9001")));
                var nickname = prefs.getString(PREF_OR_NICKNAME, "Orbot");
                var dnsFile = writeDNSFile();

                extraLines.append("ServerDNSResolvConfFile").append(' ').append(dnsFile).append('\n'); // DNSResolv is not a typo
                extraLines.append("ORPort").append(' ').append(ORPort).append('\n');
                extraLines.append("Nickname").append(' ').append(nickname).append('\n');
                extraLines.append("ExitPolicy").append(' ').append("reject *:*").append('\n');

            }
        } catch (Exception e) {
            showToolbarNotification(getString(R.string.your_relay_settings_caused_an_exception_), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
            return null;
        }

        if (Prefs.hostOnionServicesEnabled()) {
            var contentResolver = getApplicationContext().getContentResolver();
            addV3OnionServicesToTorrc(extraLines, contentResolver);
            addV3ClientAuthToTorrc(extraLines, contentResolver);
        }

        return extraLines;
    }

    private StringBuffer processSettingsImplSnowflake(StringBuffer extraLines) {
        Log.d(TAG, "in snowflake torrc config");
        extraLines.append("ClientTransportPlugin snowflake socks5 127.0.0.1:" + mIptProxy.port(IPtProxy.Snowflake)).append('\n');
        for (String bridge : mSnowflakeBridges) {
            extraLines.append("Bridge ").append(bridge).append("\n");
        }
        return extraLines;
    }

    private StringBuffer processSettingsLyrebird(StringBuffer extraLines) {
        Log.d(TAG, "in Lyrebird torrc config");

        var customBridges = getCustomBridges();

        for (String transport : Bridge.getTransports(customBridges)) {
            extraLines
                    .append(String.format(Locale.US, "ClientTransportPlugin %s socks5 127.0.0.1:%d",
                            transport, mIptProxy.port(transport)))
                    .append('\n');
        }

        for (var b : customBridges) {
            extraLines.append("Bridge ").append(b).append("\n");
        }

        return extraLines;
    }

    private List<Bridge> getCustomBridges() {
        return Bridge.parseBridges(
                Prefs.getConnectionPathway().equals(Prefs.PATHWAY_CUSTOM)
                        ? Prefs.getBridgesList()
                        : Prefs.getPrefSmartTryObfs4());
    }

    private StringBuffer processSettingsImplDirectPathway(StringBuffer extraLines) {
        var prefs = Prefs.getSharedPrefs(getApplicationContext());
        extraLines.append("UseBridges 0").append('\n');
        if (!Prefs.useVpn()) { //set the proxy here if we aren't using a bridge
            var proxyType = prefs.getString("pref_proxy_type", null);
            if (proxyType != null && proxyType.length() > 0) {
                var proxyHost = prefs.getString("pref_proxy_host", null);
                var proxyPort = prefs.getString("pref_proxy_port", null);
                var proxyUser = prefs.getString("pref_proxy_username", null);
                var proxyPass = prefs.getString("pref_proxy_password", null);

                if ((proxyHost != null && proxyHost.length() > 0) && (proxyPort != null && proxyPort.length() > 0)) {
                    extraLines.append(proxyType).append("Proxy").append(' ').append(proxyHost).append(':').append(proxyPort).append('\n');

                    if (proxyUser != null && proxyPass != null) {
                        if (proxyType.equalsIgnoreCase("socks5")) {
                            extraLines.append("Socks5ProxyUsername").append(' ').append(proxyUser).append('\n');
                            extraLines.append("Socks5ProxyPassword").append(' ').append(proxyPass).append('\n');
                        } else
                            extraLines.append(proxyType).append("ProxyAuthenticator").append(' ').append(proxyUser).append(':').append(proxyPort).append('\n');

                    } else if (proxyPass != null)
                        extraLines.append(proxyType).append("ProxyAuthenticator").append(' ').append(proxyUser).append(':').append(proxyPort).append('\n');
                }
            }
        }
        return extraLines;
    }

    void showBandwidthNotification(String message, boolean isActiveTransfer) {
        if (!mCurrentStatus.equals(STATUS_ON)) return;
        var icon = !isActiveTransfer ? R.drawable.ic_stat_tor : R.drawable.ic_stat_tor_xfer;
        showToolbarNotification(message, NOTIFY_ID, icon);
    }

    public static String formatBandwidthCount(Context context, long bitsPerSecond) {
        var nf = NumberFormat.getInstance(Locale.getDefault());
        if (bitsPerSecond < 1e6)
            return nf.format(Math.round(((float) ((int) (bitsPerSecond * 10 / 1024)) / 10))) + context.getString(R.string.kibibyte_per_second);
        else
            return nf.format(Math.round(((float) ((int) (bitsPerSecond * 100 / 1024 / 1024)) / 100))) + context.getString(R.string.mebibyte_per_second);
    }

    private void addV3OnionServicesToTorrc(StringBuffer torrc, ContentResolver contentResolver) {
        try {
            var onionServices = contentResolver.query(V3_ONION_SERVICES_CONTENT_URI, V3_ONION_SERVICE_PROJECTION, OnionService.ENABLED + "=1", null, null);
            if (onionServices != null) {
                while (onionServices.moveToNext()) {
                    var id_index = onionServices.getColumnIndex(OnionService._ID);
                    var port_index = onionServices.getColumnIndex(OnionService.PORT);
                    var onion_port_index = onionServices.getColumnIndex(OnionService.ONION_PORT);
                    var path_index = onionServices.getColumnIndex(OnionService.PATH);
                    var domain_index = onionServices.getColumnIndex(OnionService.DOMAIN);
                    // Ensure that are have all the indexes before trying to use them
                    if (id_index < 0 || port_index < 0 || onion_port_index < 0 || path_index < 0 || domain_index < 0)
                        continue;

                    var id = onionServices.getInt(id_index);
                    var localPort = onionServices.getInt(port_index);
                    var onionPort = onionServices.getInt(onion_port_index);
                    var path = onionServices.getString(path_index);
                    var domain = onionServices.getString(domain_index);
                    if (path == null) {
                        path = "v3";
                        if (domain == null) path += UUID.randomUUID().toString();
                        else path += localPort;
                        var cv = new ContentValues();
                        cv.put(OnionService.PATH, path);
                        contentResolver.update(V3_ONION_SERVICES_CONTENT_URI, cv, OnionService._ID + "=" + id, null);
                    }
                    var v3DirPath = new File(mV3OnionBasePath.getAbsolutePath(), path).getCanonicalPath();
                    torrc.append("HiddenServiceDir ").append(v3DirPath).append("\n");
                    torrc.append("HiddenServiceVersion 3").append("\n");
                    torrc.append("HiddenServicePort ").append(onionPort).append(" 127.0.0.1:").append(localPort).append("\n");
                }
                onionServices.close();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public static String buildV3ClientAuthFile(String domain, String keyHash) {
        return domain + ":descriptor:x25519:" + keyHash;
    }

    private void addV3ClientAuthToTorrc(StringBuffer torrc, ContentResolver contentResolver) {
        var v3auths = contentResolver.query(V3_CLIENT_AUTH_URI, V3_CLIENT_AUTH_PROJECTION, V3ClientAuth.ENABLED + "=1", null, null);
        if (v3auths != null) {
            for (File file : mV3AuthBasePath.listFiles()) {
                if (!file.isDirectory())
                    file.delete(); // todo the adapter should maybe just write these files and not do this in service...
            }
            torrc.append("ClientOnionAuthDir " + mV3AuthBasePath.getAbsolutePath()).append('\n');
            try {
                int i = 0;
                while (v3auths.moveToNext()) {
                    var domain_index = v3auths.getColumnIndex(V3ClientAuth.DOMAIN);
                    var hash_index = v3auths.getColumnIndex(V3ClientAuth.HASH);
                    // Ensure that are have all the indexes before trying to use them
                    if (domain_index < 0 || hash_index < 0) continue;
                    var domain = v3auths.getString(domain_index);
                    var hash = v3auths.getString(hash_index);
                    var authFile = new File(mV3AuthBasePath, (i++) + ".auth_private");
                    authFile.createNewFile();
                    var fos = new FileOutputStream(authFile);
                    fos.write(buildV3ClientAuthFile(domain, hash).getBytes());
                    fos.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "error adding v3 client auth...");
            } finally {
                v3auths.close();
            }
        }
    }

    //using Google DNS for now as the public DNS server
    private String writeDNSFile() throws IOException {
        var file = new File(appBinHome, "resolv.conf");
        var bw = new PrintWriter(new FileWriter(file));
        bw.println("nameserver 8.8.8.8");
        bw.println("nameserver 8.8.4.4");
        bw.close();
        return file.getCanonicalPath();
    }

    @SuppressLint("NewApi")
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        switch (level) {
            case TRIM_MEMORY_BACKGROUND -> debug("trim memory requested: app in the background");
            case TRIM_MEMORY_COMPLETE -> debug("trim memory requested: cleanup all memory");
            case TRIM_MEMORY_MODERATE -> debug("trim memory requested: clean up some memory");
            case TRIM_MEMORY_RUNNING_CRITICAL -> debug("trim memory requested: memory on device is very low and critical");
            case TRIM_MEMORY_RUNNING_LOW -> debug("trim memory requested: memory on device is running low");
            case TRIM_MEMORY_RUNNING_MODERATE -> debug("trim memory requested: memory on device is moderate");
            case TRIM_MEMORY_UI_HIDDEN -> debug("trim memory requested: app is not showing UI anymore");
        }
    }

    public void setNotificationSubtext(String message) {
        if (mNotifyBuilder != null) {
            // stop showing expanded notifications if the user changed the after starting Orbot
            // if (!Prefs.showExpandedNotifications()) message = null;
            mNotifyBuilder.setSubText(message);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "OrbotService: onBind");
        return super.onBind(intent); // invoking super class will call onRevoke() when appropriate
    }

    // system calls this method when VPN disconnects (either by the user or another VPN app)
    @Override
    public void onRevoke() {
        Prefs.putUseVpn(false);
        mVpnManager.handleIntent(new Builder(), new Intent(ACTION_STOP_VPN));
        // tell UI, if it's open, to update immediately (don't wait for onResume() in Activity...)
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_STOP_VPN));
    }

    private void setExitNode(String newExits) {

        if (TextUtils.isEmpty(newExits)) {
            Prefs.setExitNodes("");

            if (conn != null) {
                try {
                    var resetBuffer = new ArrayList<String>();
                    resetBuffer.add("ExitNodes");
                    resetBuffer.add("StrictNodes");
                    conn.resetConf(resetBuffer);
                    conn.setConf("DisableNetwork", "1");
                    conn.setConf("DisableNetwork", "0");

                } catch (Exception ioe) {
                    Log.e(TAG, "Connection exception occurred resetting exits", ioe);
                }
            }
        } else {

            Prefs.setExitNodes("{" + newExits + "}");

            if (conn != null) {
                try {
                    var fileGeoIP = new File(appBinHome, GEOIP_ASSET_KEY);
                    var fileGeoIP6 = new File(appBinHome, GEOIP6_ASSET_KEY);

                    conn.setConf("GeoIPFile", fileGeoIP.getCanonicalPath());
                    conn.setConf("GeoIPv6File", fileGeoIP6.getCanonicalPath());
                    conn.setConf("ExitNodes", newExits);
                    conn.setConf("StrictNodes", "1");
                    conn.setConf("DisableNetwork", "1");
                    conn.setConf("DisableNetwork", "0");

                } catch (Exception ioe) {
                    Log.e(TAG, "Connection exception occurred resetting exits", ioe);
                }
            }
        }
    }

    public static final class OnionService implements BaseColumns {
        public static final String NAME = "name";
        public static final String PORT = "port";
        public static final String ONION_PORT = "onion_port";
        public static final String DOMAIN = "domain";
        public static final String ENABLED = "enabled";
        public static final String PATH = "filepath";
    }

    public static final class V3ClientAuth implements BaseColumns {
        public static final String DOMAIN = "domain";
        public static final String HASH = "hash";
        public static final String ENABLED = "enabled";
    }


    private class IncomingIntentRouter implements Runnable {
        final Intent mIntent;

        public IncomingIntentRouter(Intent intent) {
            mIntent = intent;
        }

        public void run() {
            var action = mIntent.getAction();
            if (TextUtils.isEmpty(action)) return;
            switch (action) {
                case ACTION_START -> {
                    var connectionPathway = Prefs.getConnectionPathway();
                    if (connectionPathway.equals(Prefs.PATHWAY_SNOWFLAKE) || Prefs.getPrefSmartTrySnowflake()) {
                        startSnowflakeClientDomainFronting();
                    } else if (connectionPathway.equals(Prefs.PATHWAY_SNOWFLAKE_AMP)) {
                        startSnowflakeClientAmpRendezvous();
                    } else if (connectionPathway.equals(Prefs.PATHWAY_CUSTOM) || Prefs.getPrefSmartTryObfs4() != null) {
                        for (var transport : Bridge.getTransports(getCustomBridges())) {
                            try {
                                mIptProxy.start(transport, "");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    startTor();
                    replyWithStatus(mIntent);
                    if (Prefs.useVpn()) {
                        if (mVpnManager != null && (!mVpnManager.isStarted())) { // start VPN here
                            Intent vpnIntent = VpnService.prepare(OrbotService.this);
                            if (vpnIntent == null) { //then we can run the VPN
                                mVpnManager.handleIntent(new Builder(), mIntent);
                            }
                        }

                        if (mPortSOCKS != -1 && mPortHTTP != -1)
                            sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);
                    }
                }
                case ACTION_STOP -> {
                    var userIsQuittingOrbot = mIntent.getBooleanExtra(ACTION_STOP_FOREGROUND_TASK, false);
                    stopTorAsync(!userIsQuittingOrbot);
                }
                case ACTION_UPDATE_ONION_NAMES -> updateV3OnionNames();
                case ACTION_STOP_FOREGROUND_TASK -> stopForeground(true);
                case ACTION_START_VPN -> {
                    if (mVpnManager != null && (!mVpnManager.isStarted())) {
                        //start VPN here
                        Intent vpnIntent = VpnService.prepare(OrbotService.this);
                        if (vpnIntent == null) { //then we can run the VPN
                            mVpnManager.handleIntent(new Builder(), mIntent);
                        }
                    }
                    if (mPortSOCKS != -1 && mPortHTTP != -1)
                        sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);
                }
                case ACTION_STOP_VPN -> {
                    if (mVpnManager != null) mVpnManager.handleIntent(new Builder(), mIntent);
                }
                case ACTION_RESTART_VPN -> {
                    if (mVpnManager != null) mVpnManager.restartVPN(new Builder());
                }
                case ACTION_STATUS -> {
                    if (mCurrentStatus.equals(STATUS_OFF))
                        showToolbarNotification(getString(R.string.open_orbot_to_connect_to_tor), NOTIFY_ID, R.drawable.ic_stat_tor);
                    replyWithStatus(mIntent);
                }
                case TorControlCommands.SIGNAL_RELOAD -> requestTorRereadConfig();
                case TorControlCommands.SIGNAL_NEWNYM -> newIdentity();
                case CMD_ACTIVE -> {
                    sendSignalActive();
                    replyWithStatus(mIntent);
                }
                case CMD_SET_EXIT -> setExitNode(mIntent.getStringExtra("exit"));
                case ACTION_LOCAL_LOCALE_SET -> configLanguage();
                case CMD_SNOWFLAKE_PROXY -> {
                    if (Prefs.beSnowflakeProxy()) {
                        enableSnowflakeProxy();
                    } else disableSnowflakeProxy();
                }
                default -> Log.w(TAG, "unhandled OrbotService Intent: " + action);
            }
        }
    }

    private class ActionBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case TorControlCommands.SIGNAL_NEWNYM -> newIdentity();
                case CMD_ACTIVE -> sendSignalActive();
                case LOCAL_ACTION_NOTIFICATION_START -> startTor();
                case ACTION_ERROR -> {
                    if (showTorServiceErrorMsg) {
                        Toast.makeText(context, getString(R.string.orbot_config_invalid), Toast.LENGTH_LONG).show();
                        showTorServiceErrorMsg = false;
                    }
                    stopTor();
                }
                case ACTION_STATUS -> {
                    // hack for https://github.com/guardianproject/tor-android/issues/73 remove when fixed
                    var newStatus = intent.getStringExtra(EXTRA_STATUS);
                    if (mCurrentStatus.equals(STATUS_OFF) && newStatus.equals(STATUS_STOPPING))
                        break;
                    mCurrentStatus = newStatus;
                    if (mCurrentStatus.equals(STATUS_OFF)) {
                        showDeactivatedNotification();
                    }
                    sendStatusToOrbotActivity();
                }
            }
        }
    }

    private void sendStatusToOrbotActivity() {
        var localStatus = new Intent(LOCAL_ACTION_STATUS).putExtra(EXTRA_STATUS, mCurrentStatus);
        LocalBroadcastManager.getInstance(OrbotService.this).sendBroadcast(localStatus); // update the activity with what's new
    }
}
