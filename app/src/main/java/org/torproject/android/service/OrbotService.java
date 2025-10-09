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
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import net.freehaven.tor.control.TorControlCommands;
import net.freehaven.tor.control.TorControlConnection;

import org.torproject.android.R;
import org.torproject.android.service.circumvention.SmartConnect;
import org.torproject.android.service.db.OnionServiceColumns;
import org.torproject.android.service.db.V3ClientAuthColumns;
import org.torproject.android.service.tor.CustomTorResourceInstaller;
import org.torproject.android.service.util.DiskUtils;
import org.torproject.android.service.util.Prefs;
import org.torproject.android.service.tor.TorConfig;
import org.torproject.android.service.vpn.OrbotVpnManager;
import org.torproject.jni.TorService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import kotlin.Unit;

public class OrbotService extends VpnService {

    public final static String BINARY_TOR_VERSION = TorService.VERSION_NAME;
    static final int NOTIFY_ID = 1, ERROR_NOTIFY_ID = 3;
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
    private File mV3OnionBasePath;

    public void debug(String msg) {
        Log.d(TAG, msg);
        if (Prefs.useDebugLogging()) {
            sendCallbackLogMessage(msg);
        }
    }

    @SuppressLint({"NewApi", "RestrictedApi"})
    protected void showToolbarNotification(String notifyMsg, int notifyType, int icon) {
        var intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        var pendIntent = PendingIntent.getActivity(OrbotService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (mNotifyBuilder == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotifyBuilder = new NotificationCompat
                    .Builder(this, Notifications.ORBOT_SERVICE_NOTIFICATION_CHANNEL)
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

            var shouldStartVpnFromSystemIntent = !intent.getBooleanExtra(EXTRA_NOT_SYSTEM, false);

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
        } catch (IllegalArgumentException iae) {
            //not registered yet
        }
        super.onDestroy();
    }

    private void stopTorAsync(boolean showNotification) {
        debug("stopTorAsync");
        if (showNotification) sendCallbackLogMessage(getString(R.string.status_shutting_down));
        Prefs.getTransport().stop();
        stopTor();

        //stop the foreground priority and make sure to remove the persistent notification
        stopForeground(!showNotification);
        if (showNotification) sendCallbackLogMessage(getString(R.string.status_disabled));

        mPortDns = -1;
        mPortSOCKS = -1;
        mPortHTTP = -1;
        mPortTrans = -1;

        if (!showNotification) { // clear notifications and stopSelf
            if (mNotificationManager != null) mNotificationManager.cancelAll();
            if (mOrbotRawEventListener != null) mOrbotRawEventListener.getNodes().clear();
            stopSelf();
        }
    }

    private void stopTorOnError(String message) {
        stopTorAsync(false);
        showToolbarNotification(getString(R.string.unable_to_start_tor) + ": " + message, ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr);
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
            Log.e(TAG, e.toString());
        }
    }

    protected void logNotice(String msg) {
        if (msg != null && !msg.trim().isEmpty()) {
            Log.d(TAG, msg);
            sendCallbackLogMessage(msg);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onCreate() {
        super.onCreate();
        configLanguage();

        try {
            try {
                mHandler = new Handler();
                appBinHome = getFilesDir();
                if (!appBinHome.exists()) appBinHome.mkdirs();

                appCacheHome = new File(getDataDir(), DIRECTORY_TOR_DATA);

                if (!appCacheHome.exists()) appCacheHome.mkdirs();

                mV3OnionBasePath = OnionServiceColumns.createV3OnionDir(this);
                var mV3AuthBasePath = V3ClientAuthColumns.createV3AuthDir(this);

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
                        Prefs.setGeoIpReinstallNeeded(false);
                    } catch (IOException io) { // user has < 10MB free space on disk...
                        Log.e(TAG, "Error installing geoip files", io);
                    }
                }

                mVpnManager = new OrbotVpnManager(this);
            } catch (Exception e) {
                Log.e(TAG, "Error setting up Orbot", e);
                logNotice(getString(R.string.couldn_t_start_tor_process_) + " " + e.getClass().getSimpleName());
            }
        } catch (RuntimeException re) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    private void configLanguage() {
        var config = getApplicationContext().getResources().getConfiguration();
        Prefs.setContext(getApplicationContext());
        var locale = new Locale(Prefs.getDefaultLocale());
        Locale.setDefault(locale);
        config.setLocale(locale);
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
    }

    @NonNull
    private File updateTorrcCustomFile() throws IOException {
        var conf = TorConfig.build(this, new File(appBinHome, GEOIP_ASSET_KEY),
                new File(appBinHome, GEOIP6_ASSET_KEY));

        logNotice(getString(R.string.log_notice_updating_torrc));
        debug("torrc.custom=\n" + conf);

        var fileTorRcCustom = TorService.getTorrc(this);
        DiskUtils.flushTextToFile(fileTorRcCustom, conf, false);
        return fileTorRcCustom;
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
        if (torServiceConnection != null && conn != null) {
            debug("already started, ignoring start request");
            mNotifyBuilder.setProgress(0, 0, false);
            showToolbarNotification(getString(R.string.status_activated), NOTIFY_ID, R.drawable.ic_stat_tor);
            return;
        }
        mNotifyBuilder.setProgress(100, 0, false);
        showToolbarNotification("", NOTIFY_ID, R.drawable.ic_stat_tor);

        SmartConnect.handle(this, () -> {
            try {
                startTorService();
                showTorServiceErrorMsg = true;
            } catch (Exception e) {
                return e;
            }

            return null;
        }, () -> {
            if (conn == null) return false;

            try {
                conn.resetConf(Arrays.asList("UseBridges", "ClientTransportPlugin", "Bridge"));
                conn.setConf(Prefs.getTransport().getTorConfig(this));
            }
            catch (IOException e) {
                logNotice(e.getLocalizedMessage());

                return false;
            }

            return true;
        }, (Exception e) -> {
            if (e != null) {
                logNotice(getString(R.string.unable_to_start_tor) + " " + e.getLocalizedMessage());
                stopTorOnError(e.getLocalizedMessage());
            } else {
                stopTorAsync(true);
            }

            return Unit.INSTANCE;
        }, () -> {
            if (Prefs.hostOnionServicesEnabled()) {
                try {
                    updateV3OnionNames();
                } catch (SecurityException se) {
                    logNotice(getString(R.string.log_notice_unable_to_update_onions));
                }
            }

            return Unit.INSTANCE;
        });
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
        DiskUtils.flushTextToFile(TorService.getDefaultsTorrc(this), """
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
                        Log.e(TAG, e.toString());
                    }
                }

                try { //wait another second before we set our own event listener
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
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

                    var events = new ArrayList<>(Arrays.asList(TorControlCommands.EVENT_OR_CONN_STATUS, TorControlCommands.EVENT_CIRCUIT_STATUS, TorControlCommands.EVENT_NOTICE_MSG, TorControlCommands.EVENT_WARN_MSG, TorControlCommands.EVENT_ERR_MSG, TorControlCommands.EVENT_BANDWIDTH_USED, TorControlCommands.EVENT_NEW_DESC, TorControlCommands.EVENT_ADDRMAP));
                    if (Prefs.useDebugLogging())
                        events.addAll(Arrays.asList(TorControlCommands.EVENT_DEBUG_MSG, TorControlCommands.EVENT_INFO_MSG, TorControlCommands.EVENT_STREAM_STATUS));
                    conn.setEvents(events);
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.d(TAG, "TorService: onServiceDisconnected");
                sendLocalStatusOffBroadcast();
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

                var prefs = Prefs.getSharedPrefs(getApplicationContext());
                if (prefs != null) prefs.edit().putInt(PREFS_DNS_PORT, mPortDns).apply();
            }

            var confTrans = conn.getInfo("net/listeners/trans");
            st = new StringTokenizer(confTrans, " ");
            if (st.hasMoreTokens()) {
                confTrans = st.nextToken().split(":")[1];
                confTrans = confTrans.substring(0, confTrans.length() - 1);
                mPortTrans = Integer.parseInt(confTrans);
            }
            sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans);

        } catch (IOException | NullPointerException e) {
            Log.e(TAG, e.toString());
            stopTorOnError(e.getLocalizedMessage());
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
                SmartConnect.updateProgress(prog);
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

    void showBandwidthNotification(String message, boolean isActiveTransfer) {
        if (!mCurrentStatus.equals(STATUS_ON)) return;
        var icon = !isActiveTransfer ? R.drawable.ic_stat_tor : R.drawable.ic_stat_tor_xfer;
        showToolbarNotification(message, NOTIFY_ID, icon);
    }

    public void setNotificationSubtext(String message) {
        if (mNotifyBuilder != null)
            mNotifyBuilder.setSubText(message);
    }

    // system calls this method when VPN disconnects (either by the user or another VPN app)
    @Override
    public void onRevoke() {
        Prefs.putUseVpn(false);
        mVpnManager.handleIntent(new Builder(), new Intent(ACTION_STOP));
        // tell UI, if it's open, to update immediately (don't wait for onResume() in Activity...)
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_STOP));
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
                conn.setConf("ExitNodes", Prefs.getExitNodes());
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
                    var transport = Prefs.getTransport();
                    transport.start(OrbotService.this);
                    startTor();
                    replyWithStatus(mIntent);
                    if (!Prefs.useVpn()) {
                        if (mVpnManager != null && !mVpnManager.isStarted()) { // start VPN here
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
                    // When user cancels connecting, make sure, the SmartConnect timer is also cancelled.
                    SmartConnect.cancel();
                    if (mVpnManager != null) mVpnManager.handleIntent(new Builder(), mIntent);
                    stopTorAsync(!userIsQuittingOrbot);
                }
                case ACTION_UPDATE_ONION_NAMES -> updateV3OnionNames();
                case ACTION_STOP_FOREGROUND_TASK -> stopForeground(true);
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

                    // Make sure, Smart Connect finishes successfully, even when, for some reason,
                    // progress isn't received up to 100.
                    if (STATUS_ON.equals(mCurrentStatus)) {
                        SmartConnect.updateProgress(100);
                    }

                    var localStatus = new Intent(LOCAL_ACTION_STATUS).putExtra(EXTRA_STATUS, mCurrentStatus);
                    LocalBroadcastManager.getInstance(OrbotService.this).sendBroadcast(localStatus); // update the activity with what's new
                }
            }
        }
    }
}
