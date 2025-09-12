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
import org.torproject.android.service.util.NetworkUtils
import org.torproject.android.service.util.Prefs

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
        snowflakeProxyWrapper = SnowflakeProxyWrapper(this)
        powerConnectionReceiver = PowerConnectionReceiver(this)
        val powerReceiverFilters = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        powerReceiverFilters.addAction(Intent.ACTION_POWER_DISCONNECTED)
        registerReceiver(powerConnectionReceiver, powerReceiverFilters)
        initNetworkCallbacks()
        notificationChannelId = createNotificationChannel()
        refreshNotification(getString(R.string.kindness_mode_starting))
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
            override fun onLost(network: Network) {
                refreshNotification(getString(R.string.kindness_mode_disabled_internet))
                stopSnowflakeProxy("lost network (limit wifi=${Prefs.limitSnowflakeProxyingWifi()}")
            }

            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val hasWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val hasVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

                if (Prefs.limitSnowflakeProxyingWifi() && !hasWifi) {
                    refreshNotification(getString(R.string.kindness_mode_disabled_wifi))
                    stopSnowflakeProxy("required wifi condition not met")
                } else {
                    if (NetworkUtils.isNetworkAvailable(this@SnowflakeProxyService) || hasVpn) {
                        startSnowflakeProxy("got network (wifi=${hasWifi}, limit wifi=${Prefs.limitSnowflakeProxyingWifi()}")
                    }
                    else {
                        refreshNotification(getString(R.string.kindness_mode_disabled_internet))
                    }
                }
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


    private fun startSnowflakeProxy(logReason: String? = null) {
        Log.d(TAG, "Starting snowflake proxy - $logReason")
        snowflakeProxyWrapper.enableProxy()
    }

    private fun stopSnowflakeProxy(logMessage: String? = null) {
        Log.d(TAG, "Stopping snowflake proxy - reason: $logMessage")
        snowflakeProxyWrapper.stopProxy()
    }

    fun powerConnectedCallback(isPowerConnected: Boolean) {
        if (!Prefs.limitSnowflakeProxyingCharging()) return
        if (isPowerConnected) startSnowflakeProxy("power connected")
        else {
            refreshNotification(getString(R.string.kindness_mode_disabled_power))
            stopSnowflakeProxy("power disconnected")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerConnectionReceiver)
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallbacks)
        stopSnowflakeProxy("in onDestroy()")
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