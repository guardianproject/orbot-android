/* Copyright (c) 2009-2011, Nathan Freitas, Orbot / The Guardian Project - https://guardianproject.info/apps/orbot */
/* See LICENSE for licensing information */

package org.torproject.android.service;

import static org.torproject.android.service.OrbotConstants.*;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import net.freehaven.tor.control.TorControlCommands;
import net.freehaven.tor.control.TorControlConnection;

import org.torproject.android.service.circumvention.ContentDeliveryNetworkFronts;
import org.torproject.android.service.circumvention.LyrebirdClient;
import org.torproject.android.service.circumvention.SnowflakeClient;
import org.torproject.android.service.circumvention.SnowflakeProxyWrapper;
import org.torproject.android.service.db.OnionServiceColumns;
import org.torproject.android.service.db.V3ClientAuthColumns;
import org.torproject.android.service.ui.Notifications;
import org.torproject.android.service.util.Bridge;
import org.torproject.android.service.util.CustomTorResourceInstaller;
import org.torproject.android.service.util.PowerConnectionReceiver;
import org.torproject.android.service.util.Prefs;
import org.torproject.android.service.util.Utils;
import org.torproject.android.service.vpn.OrbotVpnManager;
import org.torproject.jni.TorService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import IPtProxy.Controller;
import IPtProxy.IPtProxy;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * @noinspection CallToPrintStackTrace
 */
@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
public class OrbotService extends VpnService {

    public final static String BINARY_TOR_VERSION = TorService.VERSION_NAME;
    static final int NOTIFY_ID = 1, ERROR_NOTIFY_ID = 3;
    public final static String NOTIFICATION_CHANNEL_ID = "orbot_channel_1";
    public static int mPortSOCKS = -1, mPortHTTP = -1, mPortDns = -1, mPortTrans = -1;
    public static File appBinHome, appCacheHome;
    protected final ExecutorService mExecutor = Executors.newCachedThreadPool();
    OrbotRawEventListener mOrbotRawEventListener;
    OrbotVpnManager mVpnManager;
    Handler mHandler;
    ActionBroadcastReceiver mActionBroadcastReceiver;
    protected String mCurrentStatus = STATUS_OFF;
    TorControlConnection conn = null;
    private ServiceConnection torServiceConnection;
    private boolean shouldUnbindTorService;
    private NotificationManager mNotificationManager = null;
    private NotificationCompat.Builder mNotifyBuilder;
    private File mV3OnionBasePath, mV3AuthBasePath;

    private PowerConnectionReceiver mPowerReceiver;

    private boolean mHasPower = false, mHasWifi = false;

    private static Controller mIptProxy = null;

    public static synchronized Controller getIptProxyController(Context context) {
        if (mIptProxy == null) {
            mIptProxy = IPtProxy.newController(context.getCacheDir().getPath(), true, false, "DEBUG", (s, e) ->
                    Log.e(TAG, "IPtProxy Error", e));
        }
        return mIptProxy;
    }

    public void debug(String msg) {
        Log.d(TAG, msg);
        if (Prefs.useDebugLogging()) {
            sendCallbackLogMessage(msg);
        }
    }

    private void showConnectedToTorNetworkNotification() {
        mNotifyBuilder.setProgress(0, 0, false);
        showToolbarNotification(getString(R.string.status_activated), NOTIFY_ID, R.drawable.ic_stat_tor);
    }

    private void clearNotifications() {
        if (mNotificationManager != null) mNotificationManager.cancelAll();
        if (mOrbotRawEventListener != null) mOrbotRawEventListener.getNodes().clear();
    }

    @SuppressLint({"NewApi", "RestrictedApi"})
    protected void showToolbarNotification(String notifyMsg, int notifyType, int icon) {
        var intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        var pendIntent = PendingIntent.getActivity(OrbotService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (mNotifyBuilder == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotifyBuilder = new NotificationCompat
                    .Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(R.drawable.ic_stat_tor);
        }

        mNotifyBuilder.setOngoing(true);
        mNotifyBuilder.mActions.clear(); // clear out any notification actions, if any

        if (Prefs.isCamoEnabled()) {
            // basically ignore all params and set a simple notification
            Notifications.configureCamoNotification(mNotifyBuilder);
        } else {
            mNotifyBuilder
                    .setSmallIcon(icon)
                    .setContentText(notifyMsg)
                    .setContentIntent(pendIntent)
                    .setContentTitle(Notifications.getNotificationTitleForStatus(this, mCurrentStatus));
            // Tor connection is active
            if (conn != null && mCurrentStatus.equals(STATUS_ON)) { // only add new identity action when there is a connection
                mNotifyBuilder.setProgress(0, 0, false); // removes progress bar
                var pendingIntentNewNym = PendingIntent.getBroadcast(this, 0, new Intent(TorControlCommands.SIGNAL_NEWNYM), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                mNotifyBuilder.addAction(R.drawable.ic_refresh_white_24dp, getString(R.string.menu_new_identity), pendingIntentNewNym);
            } // Tor connection is off
            else if (mCurrentStatus.equals(STATUS_OFF)) {
                var pendingIntentConnect = PendingIntent.getBroadcast(this, 0, new Intent(LOCAL_ACTION_NOTIFICATION_START), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                mNotifyBuilder
                        .addAction(R.drawable.ic_stat_tor, getString(R.string.connect_to_tor), pendingIntentConnect)
                        .setContentText(notifyMsg)
                        .setSubText(null)
                        .setProgress(0, 0, false)
                        .setTicker(notifyType != NOTIFY_ID ? notifyMsg : null);
            }
        }
        ServiceCompat.startForeground(this, NOTIFY_ID, mNotifyBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
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
            Log.e(TAG, "error with OrbotService", re);
        }

        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mActionBroadcastReceiver);
            unregisterReceiver(mPowerReceiver);
            mSnowflakeProxyWrapper.stopProxy(); // stop snowflake proxy if its somehow running
        } catch (IllegalArgumentException iae) {
            //not registered yet
        }
        super.onDestroy();
    }

    private void stopTorAsync(boolean showNotification) {
        debug("stopTorAsync");

        if (showNotification) sendCallbackLogMessage(getString(R.string.status_shutting_down));
        var connectionPathway = Prefs.getTorConnectionPathway();
        // todo this needs to handle a lot of different cases that haven't been defined yet
        // todo particularly this is true for the smart connection case...
        if (connectionPathway.startsWith(Prefs.CONNECTION_PATHWAY_SNOWFLAKE) || Prefs.getPrefSmartTrySnowflake()) {
            SnowflakeClient.stop(mIptProxy);
        } else if (connectionPathway.equals(Prefs.CONNECTION_PATHWAY_MEEK)) {
            LyrebirdClient.stop(mIptProxy);
        } else if (connectionPathway.equals(Prefs.CONNECTION_PATHWAY_OBFS4) || Prefs.getPrefSmartTryObfs4() != null) {
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

    public static void loadCdnFronts(Context context) {
        if (mFronts != null) return;
        mFronts = ContentDeliveryNetworkFronts.localFronts(context);
    }

    public static String getCdnFront(String service) {
        return mFronts.get(service);
    }

    public synchronized void enableSnowflakeProxy() { // This is to host a snowflake entrance node / bridge
        mSnowflakeProxyWrapper.enableProxy(mHasWifi, mHasPower);
        logNotice(getString(R.string.log_notice_snowflake_proxy_enabled));
    }

    private void enableSnowflakeProxyNetworkListener() {
        if (!Prefs.limitSnowflakeProxyingWifi()) return;
        var connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        connMgr.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override // update if on wifi
            public void onAvailable(@NonNull Network network) {
                checkNetworkForSnowflakeProxy();
            }

            @Override // or if lost
            public void onLost(@NonNull Network network) {
                checkNetworkForSnowflakeProxy();
            }
        });
    }

    public void setHasPower(boolean hasPower) {
        mHasPower = hasPower;
        if (Prefs.beSnowflakeProxy()) {
            if (Prefs.limitSnowflakeProxyingCharging()) {
                if (mHasPower) enableSnowflakeProxy();
                else disableSnowflakeProxy();
            }
        }
    }

    private void checkNetworkForSnowflakeProxy() {
        var connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var netCap = connMgr.getNetworkCapabilities(connMgr.getActiveNetwork());
            if (netCap != null)
                mHasWifi = netCap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            else
                mHasWifi = false;
        } else {
            var netInfo = connMgr.getActiveNetworkInfo();
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
        mSnowflakeProxyWrapper.stopProxy();
        logNotice(getString(R.string.log_notice_snowflake_proxy_disabled));
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
            if (conn == null) return;
            conn.signal(TorControlCommands.SIGNAL_RELOAD);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void logNotice(String msg) {
        if (msg != null && !msg.trim().isEmpty()) {
            Log.d(TAG, msg);
            sendCallbackLogMessage(msg);
        }
    }

    private SnowflakeProxyWrapper mSnowflakeProxyWrapper;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onCreate() {
        super.onCreate();
        configLanguage();
        getIptProxyController(this);

        try {
            try {
                mHandler = new Handler();
                appBinHome = getFilesDir();
                if (!appBinHome.exists()) appBinHome.mkdirs();

                appCacheHome = new File(getDataDir(), DIRECTORY_TOR_DATA);

                if (!appCacheHome.exists()) appCacheHome.mkdirs();

                mV3OnionBasePath = OnionServiceColumns.createV3OnionDir(this);
                mV3AuthBasePath = V3ClientAuthColumns.createV3AuthDir(this);

                if (mNotificationManager == null)
                    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                var filter = new IntentFilter(TorControlCommands.SIGNAL_NEWNYM);
                filter.addAction(CMD_ACTIVE);
                filter.addAction(ACTION_STATUS);
                filter.addAction(ACTION_ERROR);
                filter.addAction(LOCAL_ACTION_NOTIFICATION_START);

                mActionBroadcastReceiver = new ActionBroadcastReceiver();
                ContextCompat.registerReceiver(this, mActionBroadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    Notifications.createNotificationChannel(this);

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
                loadCdnFronts(this);
            } catch (Exception e) {
                Log.e(TAG, "Error setting up Orbot", e);
                logNotice(getString(R.string.couldn_t_start_tor_process_) + " " + e.getClass().getSimpleName());
            }

            mPowerReceiver = new PowerConnectionReceiver(this);

            var iFilter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
            iFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            registerReceiver(mPowerReceiver, iFilter);

            enableSnowflakeProxyNetworkListener();

            mSnowflakeProxyWrapper = new SnowflakeProxyWrapper(this);
            if (Prefs.beSnowflakeProxy()
                    && !(Prefs.limitSnowflakeProxyingCharging() || Prefs.limitSnowflakeProxyingWifi()))
                enableSnowflakeProxy();

        } catch (RuntimeException re) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    private void configLanguage() {
        var config = getApplicationContext().getResources().getConfiguration();
        Prefs.setContext(getApplicationContext());
        var locale = new Locale(Prefs.getDefaultLocale());
        Locale.setDefault(locale);
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
    }

    private File updateTorrcCustomFile() throws IOException {
        var prefs = Prefs.getSharedPrefs(getApplicationContext());
        var extraLines = new StringBuffer("RunAsDaemon 0\n")
                .append("AvoidDiskWrites 1\n");

        var socksPortPref = prefs.getString(PREF_SOCKS, SOCKS_PROXY_PORT_DEFAULT);
        if (socksPortPref.indexOf(':') != -1) socksPortPref = socksPortPref.split(":")[1];
        socksPortPref = Utils.checkPortOrAuto(socksPortPref);

        var httpPortPref = prefs.getString(PREF_HTTP, HTTP_PROXY_PORT_DEFAULT);
        if (httpPortPref.indexOf(':') != -1) httpPortPref = httpPortPref.split(":")[1];
        httpPortPref = Utils.checkPortOrAuto(httpPortPref);

        var isolate = "";
        if (prefs.getBoolean(PREF_ISOLATE_DEST, false))
            isolate += " IsolateDestAddr ";
        if (prefs.getBoolean(PREF_ISOLATE_PORT, false))
            isolate += " IsolateDestPort ";
        if (prefs.getBoolean(PREF_ISOLATE_PROTOCOL, false))
            isolate += " IsolateClientProtocol ";
        if (prefs.getBoolean(PREF_ISOLATE_KEEP_ALIVE, false))
            isolate += " KeepAliveIsolateSOCKSAuth ";

        var ipv6Pref = "";
        if (prefs.getBoolean(PREF_PREFER_IPV6, true))
            ipv6Pref += " IPv6Traffic PreferIPv6 ";

        if (prefs.getBoolean(PREF_DISABLE_IPV4, false))
            ipv6Pref += " IPv6Traffic NoIPv4Traffic ";

        if (!Prefs.openProxyOnAllInterfaces()) {
            extraLines.append("SOCKSPort " + socksPortPref + ipv6Pref + isolate + "\n");
        } else {
            extraLines
                    .append("SOCKSPort 0.0.0.0:" + socksPortPref + ipv6Pref + isolate + "\n")
                    .append("SocksPolicy accept *:*\n");
        }
        extraLines
                .append("SafeSocks 0\n")
                .append("TestSocks 0\n")
                .append("HTTPTunnelPort " + httpPortPref + isolate + "\n");

        if (prefs.getBoolean(PREF_CONNECTION_PADDING, false))
            extraLines.append("ConnectionPadding 1\n");


        if (prefs.getBoolean(PREF_REDUCED_CONNECTION_PADDING, true))
            extraLines.append("ReducedConnectionPadding 1\n");


        if (prefs.getBoolean(PREF_CIRCUIT_PADDING, true))
            extraLines.append("CircuitPadding 1\n");
        else
            extraLines.append("CircuitPadding 0\n");

        if (prefs.getBoolean(PREF_REDUCED_CIRCUIT_PADDING, true))
            extraLines.append("ReducedCircuitPadding 1\n");

        var transPort = prefs.getString(PREF_TRANSPORT, String.valueOf(TOR_TRANSPROXY_PORT_DEFAULT));
        var dnsPort = prefs.getString(PREF_DNSPORT, String.valueOf(TOR_DNS_PORT_DEFAULT));

        extraLines.append("TransPort " + Utils.checkPortOrAuto(transPort) + isolate + "\n")
                .append("DNSPort " + Utils.checkPortOrAuto(dnsPort) + isolate + "\n")
                .append("VirtualAddrNetwork 10.192.0.0/10\n")
                .append("AutomapHostsOnResolve 1\n")
                .append("DormantClientTimeout 10 minutes\n")
                .append("DormantCanceledByStartup 1\n")
                .append("DisableNetwork 0\n");

        if (Prefs.useDebugLogging()) {
            extraLines.append("Log debug syslog\n")
                    .append("SafeLogging 0\n");
        }

        extraLines = processSettingsImpl(extraLines);
        if (extraLines == null) return null;

        extraLines.append("\n" + prefs.getString("pref_custom_torrc", "") + "\n");

        logNotice(getString(R.string.log_notice_updating_torrc));
        debug("torrc.custom=\n" + extraLines);

        var fileTorRcCustom = TorService.getTorrc(this);
        updateTorConfigCustom(fileTorRcCustom, extraLines.toString(), false);
        return fileTorRcCustom;
    }

    public void updateTorConfigCustom(File fileTorRcCustom, String extraLines, boolean append) throws IOException {
        var ps = new PrintWriter(new FileWriter(fileTorRcCustom, append));
        ps.print(extraLines);
        ps.flush();
        ps.close();
    }

    /**
     * Send Orbot's status in reply to an ACTION_START Intent, targeted only to the app that sent the
     * initial request. If the user has disabled auto-starts, the reply ACTION_START Intent will
     * include the Intent extra STATUS_STARTS_DISABLED
     */
    private void replyWithStatus(Intent startRequest) {
        String packageName = startRequest.getStringExtra(EXTRA_PACKAGE_NAME);
        Intent reply = new Intent(ACTION_STATUS)
                .putExtra(EXTRA_STATUS, mCurrentStatus)
                .putExtra(EXTRA_SOCKS_PROXY, "socks://127.0.0.1:" + mPortSOCKS)
                .putExtra(EXTRA_SOCKS_PROXY_HOST, "127.0.0.1")
                .putExtra(EXTRA_SOCKS_PROXY_PORT, mPortSOCKS)
                .putExtra(EXTRA_HTTP_PROXY, "http://127.0.0.1:" + mPortHTTP)
                .putExtra(EXTRA_HTTP_PROXY_HOST, "127.0.0.1")
                .putExtra(EXTRA_HTTP_PROXY_PORT, mPortHTTP)
                .putExtra(EXTRA_DNS_PORT, mPortDns);

        if (packageName != null)
            sendBroadcast(reply.setPackage(packageName));

        LocalBroadcastManager.getInstance(this).sendBroadcast(reply.setAction(LOCAL_ACTION_STATUS));
        if (mPortSOCKS != -1 && mPortHTTP != -1)
            sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);
    }

    private boolean showTorServiceErrorMsg = false;

    // The entire process for starting tor and related services is run from this method.
    private void startTor() {
        try {
            if (torServiceConnection != null && conn != null) {
                sendCallbackLogMessage(getString(R.string.log_notice_ignoring_start_request));
                showConnectedToTorNetworkNotification();
                return;
            }
            mNotifyBuilder.setProgress(100, 0, false);
            showToolbarNotification("", NOTIFY_ID, R.drawable.ic_stat_tor);

            if (Prefs.getTorConnectionPathway().equals(Prefs.CONNECTION_PATHWAY_SMART))
                smartConnectionPathwayStartTor();

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
            logNotice(getString(R.string.unable_to_start_tor) + " " + e.getLocalizedMessage());
            stopTorOnError(e.getLocalizedMessage());
        }
    }

    static int TRIES_DELETE = 0;

    private void smartConnectionPathwayStartTor() {
        Log.d(TAG, "timing out in " + 150000 + "ms");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "timed out mCurrentStatus=" + mCurrentStatus);
            if (!mCurrentStatus.equals(STATUS_ON)) {
                Log.d(TAG, "tor isn't on");
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
                    Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_OBFS4);
                } else if (Prefs.getPrefSmartTrySnowflake()) {
                    Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_SNOWFLAKE); // set snowflake
                }
                clearEphemeralSmartConnectionSettings();
            }
        }, ((TRIES_DELETE++) != 2) ? 15000 : 10000);
    }

    private void clearEphemeralSmartConnectionSettings() {
        Prefs.putPrefSmartTryObfs4(null);
        Prefs.putPrefSmartTrySnowflake(false);
    }

    private void sendSmartStatusToActivity(String status) {
        var intent = new Intent(LOCAL_ACTION_SMART_CONNECT_EVENT).putExtra(LOCAL_EXTRA_SMART_STATUS, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    private void updateV3OnionNames() {
        OnionServiceColumns.updateV3OnionNames(this, mV3OnionBasePath);
        // This old status hack is temporary and fixes the issue reported by syphyr at
        // https://github.com/guardianproject/orbot/pull/556
        // Down the line a better approach needs to happen for sending back the onion names' updated
        // status, perhaps just adding it as an extra to the normal Intent callback...
        var oldStatus = mCurrentStatus;
        var intent = new Intent(LOCAL_ACTION_V3_NAMES_UPDATED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        mCurrentStatus = oldStatus;
    }

    private synchronized void startTorService() throws Exception {
        updateTorConfigCustom(TorService.getDefaultsTorrc(this), """
                DNSPort 0
                TransPort 0
                DisableNetwork 1
                """, false);

        var fileTorrcCustom = updateTorrcCustomFile();
        assert fileTorrcCustom != null;
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

                try { //wait another second before we set our own event listener
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mOrbotRawEventListener = new OrbotRawEventListener(OrbotService.this);

                if (conn == null) return;
                try {
                    initControlConnection();
                    if (conn == null)
                        return; // maybe there was an error setting up the control connection

                    //override the TorService event listener
                    conn.addRawEventListener(mOrbotRawEventListener);

                    logNotice(getString(R.string.log_notice_connected_to_tor_control_port));

                    //now set our own events
                    var events = new ArrayList<>(Arrays.asList(TorControlCommands.EVENT_OR_CONN_STATUS, TorControlCommands.EVENT_CIRCUIT_STATUS, TorControlCommands.EVENT_NOTICE_MSG, TorControlCommands.EVENT_WARN_MSG, TorControlCommands.EVENT_ERR_MSG, TorControlCommands.EVENT_BANDWIDTH_USED, TorControlCommands.EVENT_NEW_DESC, TorControlCommands.EVENT_ADDRMAP));
                    if (Prefs.useDebugLogging()) {
                        events.add(TorControlCommands.EVENT_DEBUG_MSG);
                        events.add(TorControlCommands.EVENT_INFO_MSG);
                        events.add(TorControlCommands.EVENT_STREAM_STATUS);
                    }
                    conn.setEvents(events);
                    logNotice(getString(R.string.log_notice_added_event_handler));
                } catch (IOException e) {
                    e.printStackTrace();
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

        var serviceIntent = new Intent(this, TorService.class);
        debug("binding tor service");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            shouldUnbindTorService = bindService(serviceIntent, torServiceConnection, BIND_AUTO_CREATE);
        else
            shouldUnbindTorService = bindService(serviceIntent, BIND_AUTO_CREATE, mExecutor, torServiceConnection);
    }

    private void sendLocalStatusOffBroadcast() {
        var localOffStatus = new Intent(LOCAL_ACTION_STATUS).putExtra(EXTRA_STATUS, STATUS_OFF);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localOffStatus);
    }

    private void initControlConnection() {
        if (conn == null) return;
        try {
            var confSocks = conn.getInfo("net/listeners/socks");
            var st = new StringTokenizer(confSocks, " ");
            if (confSocks.trim().isEmpty()) {
                mPortSOCKS = 0;
            } else {
                confSocks = st.nextToken().split(":")[1];
                confSocks = confSocks.substring(0, confSocks.length() - 1);
                mPortSOCKS = Integer.parseInt(confSocks);
            }
            var confHttp = conn.getInfo("net/listeners/httptunnel").trim();
            if (confHttp.isEmpty()) {
                mPortHTTP = 0;
            } else {
                st = new StringTokenizer(confHttp, " ");
                confHttp = st.nextToken().split(":")[1];
                confHttp = confHttp.substring(0, confHttp.length() - 1);
                mPortHTTP = Integer.parseInt(confHttp);
            }
            var confDns = conn.getInfo("net/listeners/dns");
            st = new StringTokenizer(confDns, " ");
            if (st.hasMoreTokens()) {
                confDns = st.nextToken().split(":")[1];
                confDns = confDns.substring(0, confDns.length() - 1);
                mPortDns = Integer.parseInt(confDns);
                Prefs.getSharedPrefs(getApplicationContext()).edit().putInt(PREFS_DNS_PORT, mPortDns).apply();
            }

            var confTrans = conn.getInfo("net/listeners/trans");
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
            @Override
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

    private void sendCallbackLogMessage(final String logMessage) {
        var notificationMessage = logMessage;
        var localIntent = new Intent(LOCAL_ACTION_LOG).putExtra(LOCAL_EXTRA_LOG, logMessage);
        if (logMessage.contains(LOG_NOTICE_HEADER)) {
            notificationMessage = notificationMessage.substring(LOG_NOTICE_HEADER.length());
            if (notificationMessage.contains(LOG_NOTICE_BOOTSTRAPPED)) {
                var percent = notificationMessage.substring(LOG_NOTICE_BOOTSTRAPPED.length());
                percent = percent.substring(0, percent.indexOf('%')).trim();
                localIntent.putExtra(LOCAL_EXTRA_BOOTSTRAP_PERCENT, percent);
                var prog = Integer.parseInt(percent);
                mNotifyBuilder.setProgress(100, prog, false);
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
        var ReachableAddresses = prefs.getBoolean(OrbotConstants.PREF_REACHABLE_ADDRESSES, false);
        var enableStrictNodes = prefs.getBoolean("pref_strict_nodes", false);
        var entranceNodes = prefs.getString("pref_entrance_nodes", "");
        var exitNodes = prefs.getString("pref_exit_nodes", "");
        var excludeNodes = prefs.getString("pref_exclude_nodes", "");
        String pathway = Prefs.getTorConnectionPathway();

        if (pathway.equals(Prefs.CONNECTION_PATHWAY_SMART)) {
            // todo for now ...
        } else if (pathway.equals(Prefs.CONNECTION_PATHWAY_DIRECT)) {
            processSettingsImplDirectPathway(extraLines);
        } else {
            // snowflake or obfs4
            extraLines.append("UseBridges 1\n");
            if (pathway.equals(Prefs.CONNECTION_PATHWAY_SNOWFLAKE_AMP) || pathway.equals(Prefs.CONNECTION_PATHWAY_SNOWFLAKE_SQS))
                processSettingsImplSnowflakeAmpAndSqsModes(extraLines);
            else if (pathway.startsWith(Prefs.CONNECTION_PATHWAY_SNOWFLAKE) || Prefs.getPrefSmartTrySnowflake())
                processSettingsImplSnowflake(extraLines);
            else if (pathway.equals(Prefs.CONNECTION_PATHWAY_MEEK))
                processSettingsImplMeekLite(extraLines);
            else if (pathway.equals(Prefs.CONNECTION_PATHWAY_OBFS4) || Prefs.getPrefSmartTryObfs4() != null)
                processSettingsLyrebird(extraLines);
        }
        var fileGeoIP = new File(appBinHome, GEOIP_ASSET_KEY);
        var fileGeoIP6 = new File(appBinHome, GEOIP6_ASSET_KEY);

        if (fileGeoIP.exists()) { // only apply geoip if it exists
            extraLines.append("GeoIPFile " + fileGeoIP.getCanonicalPath() + "\n")
                    .append("GeoIPv6File " + fileGeoIP6.getCanonicalPath() + "\n");
        }

        if (!TextUtils.isEmpty(entranceNodes))
            extraLines.append("EntryNodes " + entranceNodes + "\n");

        if (!TextUtils.isEmpty(exitNodes))
            extraLines.append("ExitNodes " + exitNodes + "\n");

        if (!TextUtils.isEmpty(excludeNodes))
            extraLines.append("ExcludeNodes " + excludeNodes + "\n");

        extraLines.append("StrictNodes ").append(enableStrictNodes ? "1\n" : "0\n");

        try {
            if (ReachableAddresses) {
                var ReachableAddressesPorts = prefs.getString(PREF_REACHABLE_ADDRESSES_PORTS, "*:80,*:443");
                extraLines.append("ReachableAddresses " + ReachableAddressesPorts + "\n");
            }

        } catch (Exception e) {
            showToolbarNotification(getString(R.string.your_reachableaddresses_settings_caused_an_exception_), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
            return null;
        }

        if (Prefs.hostOnionServicesEnabled()) {
            // add any needed client authorization and hosted onion service config lines to torrc
            V3ClientAuthColumns.addClientAuthToTorrc(extraLines, this, mV3AuthBasePath);
            OnionServiceColumns.addV3OnionServicesToTorrc(extraLines, this, mV3OnionBasePath);
        }

        return extraLines;
    }

    private void processSettingsImplMeekLite(StringBuffer extraLines) {
        extraLines.append(LyrebirdClient.getClientTransportPluginTorrcLine(mIptProxy));
        var brokers = LyrebirdClient.getLocalBrokersMeek(this);
        for (String bridge : brokers)
            extraLines.append("Bridge " + bridge + "\n");
    }

    private void processSettingsImplSnowflake(StringBuffer extraLines) {
        extraLines.append(SnowflakeClient.getClientTransportPluginTorrcLine(mIptProxy));
        var brokers = SnowflakeClient.getLocalBrokers(this);
        for (String bridge : brokers)
            extraLines.append("Bridge " + bridge + "\n");
    }

    private void processSettingsImplSnowflakeAmpAndSqsModes(StringBuffer extraLines) {
        extraLines.append(SnowflakeClient.getClientTransportPluginTorrcLine(mIptProxy));
        var brokers = SnowflakeClient.getLocalBrokersAmp(this);
        for (String bridge : brokers)
            extraLines.append("Bridge " + bridge + "\n");
    }

    @SuppressLint("DefaultLocale")
    private void processSettingsLyrebird(StringBuffer extraLines) {
        var customBridges = getConfiguredObfs4Bridges();
        for (String transport : Bridge.getTransports(customBridges))
            extraLines.append(String.format("ClientTransportPlugin %s socks5 127.0.0.1:%d\n", transport, mIptProxy.port(transport)));

        for (var b : customBridges)
            extraLines.append("Bridge ").append(b).append("\n");
    }

    private List<Bridge> getConfiguredObfs4Bridges() {
        return Bridge.parseBridges(
                Prefs.getTorConnectionPathway().equals(Prefs.CONNECTION_PATHWAY_OBFS4)
                        ? Prefs.getBridgesList()
                        : Prefs.getPrefSmartTryObfs4());
    }

    private void processSettingsImplDirectPathway(StringBuffer extraLines) {
        var prefs = Prefs.getSharedPrefs(getApplicationContext());
        extraLines.append("UseBridges 0\n");
        if (Prefs.useVpn()) return;
        //set the proxy here if we aren't using a bridge
        var proxyType = prefs.getString("pref_proxy_type", null);
        if (proxyType != null && !proxyType.isEmpty()) {
            var proxyHost = prefs.getString("pref_proxy_host", null);
            var proxyPort = prefs.getString("pref_proxy_port", null);
            var proxyUser = prefs.getString("pref_proxy_username", null);
            var proxyPass = prefs.getString("pref_proxy_password", null);

            if ((proxyHost != null && !proxyHost.isEmpty()) && (proxyPort != null && !proxyPort.isEmpty())) {
                extraLines.append(proxyType).append("Proxy " + proxyHost + ":" + proxyPort + "\n");

                if (proxyUser != null && proxyPass != null) {
                    if (proxyType.equalsIgnoreCase("socks5")) {
                        extraLines.append("Socks5ProxyUsername " + proxyUser + "\n")
                                .append("Socks5ProxyPassword " + proxyPass + "\n");
                    } else
                        extraLines.append(proxyType).append(" ProxyAuthenticator " + proxyUser + ":" + proxyPort + "\n");
                }
            }
        }
    }

    void showBandwidthNotification(String message, boolean isActiveTransfer) {
        if (!mCurrentStatus.equals(STATUS_ON)) return;
        var icon = !isActiveTransfer ? R.drawable.ic_stat_tor : R.drawable.ic_stat_tor_xfer;
        showToolbarNotification(message, NOTIFY_ID, icon);
    }

    @Override
    public void onTrimMemory(int level) {
        debug("TRIM MEMORY REQUESTED level=" + level);
    }

    public void setNotificationSubtext(String message) {
        if (mNotifyBuilder != null) {
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

            if (conn == null) return;
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
        } else {
            Prefs.setExitNodes("{" + newExits + "}");
            if (conn == null) return;
            try {
                conn.setConf("GeoIPFile", new File(appBinHome, GEOIP_ASSET_KEY).getCanonicalPath());
                conn.setConf("GeoIPv6File", new File(appBinHome, GEOIP6_ASSET_KEY).getCanonicalPath());
                conn.setConf("ExitNodes", newExits);
                conn.setConf("StrictNodes", "1");
                conn.setConf("DisableNetwork", "1");
                conn.setConf("DisableNetwork", "0");
            } catch (Exception ioe) {
                Log.e(TAG, "Connection exception occurred resetting exits", ioe);
            }
        }
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
                    var connectionPathway = Prefs.getTorConnectionPathway();
                    if (connectionPathway.equals(Prefs.CONNECTION_PATHWAY_SNOWFLAKE) || Prefs.getPrefSmartTrySnowflake()) {
                        SnowflakeClient.startWithDomainFronting(mIptProxy);
                    } else if (connectionPathway.equals(Prefs.CONNECTION_PATHWAY_SNOWFLAKE_AMP)) {
                        SnowflakeClient.startWithAmpRendezvous(mIptProxy);
                    } else if (connectionPathway.equals(Prefs.CONNECTION_PATHWAY_SNOWFLAKE_SQS)) {
                        SnowflakeClient.startWithSqsRendezvous(mIptProxy);
                    } else if (connectionPathway.equals(Prefs.CONNECTION_PATHWAY_MEEK)) {
                        LyrebirdClient.startWithMeekLite(mIptProxy);
                    } else if (connectionPathway.equals(Prefs.CONNECTION_PATHWAY_OBFS4) || Prefs.getPrefSmartTryObfs4() != null) {
                        for (var transport : Bridge.getTransports(getConfiguredObfs4Bridges())) {
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
                        var vpnIntent = VpnService.prepare(OrbotService.this);
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
        @Override
        public void onReceive(Context context, Intent intent) {
            var action = intent.getAction();
            if (action == null) return;
            switch (action) {
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
                    if (STATUS_OFF.equals(mCurrentStatus) && STATUS_STOPPING.equals(newStatus))
                        break;
                    mCurrentStatus = newStatus;
                    if (STATUS_OFF.equals(mCurrentStatus)) {
                        showToolbarNotification(getString(R.string.open_orbot_to_connect_to_tor), NOTIFY_ID, R.drawable.ic_stat_tor);
                    }
                    var localStatus = new Intent(LOCAL_ACTION_STATUS).putExtra(EXTRA_STATUS, mCurrentStatus);
                    LocalBroadcastManager.getInstance(OrbotService.this).sendBroadcast(localStatus); // update the activity with what's new
                }
            }
        }
    }
}
