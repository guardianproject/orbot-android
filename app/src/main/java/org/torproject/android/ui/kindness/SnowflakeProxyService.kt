package org.torproject.android.ui.kindness

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.torproject.android.R
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.util.NetworkUtils
import org.torproject.android.util.Prefs

class SnowflakeProxyService : Service() {

    private lateinit var snowflakeProxyWrapper: SnowflakeProxyWrapper
    private lateinit var powerConnectionReceiver: PowerConnectionReceiver
    private lateinit var notificationChannelId: String

    private lateinit var networkCallbacks: ConnectivityManager.NetworkCallback

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind: $intent")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        notificationChannelId = createNotificationChannel()
        snowflakeProxyWrapper = SnowflakeProxyWrapper(this)
        powerConnectionReceiver = PowerConnectionReceiver(this)

        val powerReceiverFilters = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        powerReceiverFilters.addAction(Intent.ACTION_POWER_DISCONNECTED)
        registerReceiver(powerConnectionReceiver, powerReceiverFilters)

        initNetworkCallbacks()
        refreshNotification(getString(R.string.kindness_mode_starting))
        attemptToStartSnowflakeProxy("service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SNOWFLAKE_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    fun refreshNotification(contentText: String? = null) {
        val title =
            if (snowflakeProxyWrapper.isProxyRunning()) getString(R.string.kindness_mode_is_running)
            else getString(R.string.kindness_mode_disabled)

        var icon = R.drawable.snowflake_on
        if (!snowflakeProxyWrapper.isProxyRunning()) {
            icon = if (contentText == getString(R.string.kindness_mode_starting))
                R.drawable.snowflake_starting
            else R.drawable.snowflake_off
        }

        val activityIntent =
            packageManager.getLaunchIntentForPackage(packageName)
        val pendingActivityIntent =
            PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE)
        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(title)
            .setContentIntent(pendingActivityIntent)
            .setContentText(
                contentText ?: getString(
                    R.string.kindness_mode_active_message,
                    Prefs.snowflakesServed
                )
            )
        // .setSubText("Shown on third line of notification...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            notificationBuilder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun initNetworkCallbacks() {
        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager

        networkCallbacks = object : ConnectivityManager.NetworkCallback() {
            // we lost a network, but may still be online
            override fun onLost(network: Network) {
                attemptToStartSnowflakeProxy("networkLost$network")
            }

            // we got a new network connection, refresh things
            override fun onAvailable(network: Network) {
                attemptToStartSnowflakeProxy("networkConnected$network")
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallbacks)
    }


    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return ""
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.volunteer_mode),
            NotificationManager.IMPORTANCE_LOW
        )
        val service = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return CHANNEL_ID
    }


    private fun attemptToStartSnowflakeProxy(logReason: String? = null) {
        Log.d(TAG, "Attempting to start snowflake proxy: $logReason")

        if (!isTorConstraintMet()) {
            stopSnowflakeProxyIfRunning("tor is in use with a bridge")
            return
        }

        if (!isPowerConstraintMet()) {
            stopSnowflakeProxyIfRunning("power constraint isn't met")
            return
        }
        if (!isNetworkConstraintMet()) {
            stopSnowflakeProxyIfRunning("network constraint isn't met")
            return
        }
        snowflakeProxyWrapper.enableProxy()
    }

    private fun stopSnowflakeProxyIfRunning(logMessage: String? = null) {
        Log.d(TAG, "Stopping snowflake proxy - reason: $logMessage")
        snowflakeProxyWrapper.stopProxy()
    }

    fun powerConnectedCallback(isPowerConnected: Boolean) {
        if (!Prefs.limitSnowflakeProxyingCharging()) return
        if (isPowerConnected) attemptToStartSnowflakeProxy("power connected")
        else {
            refreshNotification(getString(R.string.kindness_mode_disabled_power))
            stopSnowflakeProxyIfRunning("power disconnected")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerConnectionReceiver)
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallbacks)
        stopSnowflakeProxyIfRunning("in onDestroy()")
    }


    private fun isTorConstraintMet(): Boolean {
        if (Prefs.useVpn()) {
            // if Tor VPN is actively running, make sure no bridges are used
            return Prefs.transport == Transport.NONE
        }
        return true
    }

    private fun isPowerConstraintMet(): Boolean {
        return !Prefs.limitSnowflakeProxyingCharging() || PowerConnectionReceiver.getChargingStatusOnDemand(
            this
        )
    }

    private fun isNetworkConstraintMet(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(nw) ?: return false
        val hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        // first see if device is online
        if (!NetworkUtils.isNetworkAvailable(this, hasVpn)) {
            refreshNotification(getString(R.string.kindness_mode_disabled_internet))
            Log.d(TAG, "no internet")
            return false
        }

        if (hasVpn && !Prefs.useVpn()) {
            refreshNotification(getString(R.string.kindness_mode_disabled_internet))
            Log.d(TAG, "abort another VPN app")
            return false
        }

        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (Prefs.limitSnowflakeProxyingCharging() && !hasWifi) {
            refreshNotification(getString(R.string.kindness_mode_disabled_wifi))
            Log.d(TAG, "wifi limiting on but no wifi")
            return false
        }

        return true
    }

    companion object {
        const val TAG = "SnowflakeProxyService" // "GoLog"
        private const val NOTIFICATION_ID = 103
        private const val CHANNEL_ID = "snowflake"
        private const val ACTION_STOP_SNOWFLAKE_SERVICE = "ACTION_STOP_SNOWFLAKE_SERVICE"

        private fun getIntent(context: Context) = Intent(context, SnowflakeProxyService::class.java)

        // start this service, but not necessarily snowflake proxy from the app UI
        fun startSnowflakeProxyForegroundService(context: Context) {
            ContextCompat.startForegroundService(
                context,
                getIntent(context)
            )
        }

        // stop this service, and snowflake proxy if its running, from the app UI

        fun stopSnowflakeProxyForegroundService(context: Context) {
            ContextCompat.startForegroundService(
                context,
                getIntent(context).setAction(ACTION_STOP_SNOWFLAKE_SERVICE)
            )
        }
    }
}
