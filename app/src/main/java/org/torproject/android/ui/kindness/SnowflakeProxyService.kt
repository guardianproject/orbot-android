package org.torproject.android.ui.kindness

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.torproject.android.R
import org.torproject.android.Regionalization
import org.torproject.android.service.Notifications
import org.torproject.android.util.NetworkUtils
import org.torproject.android.util.Prefs
import org.torproject.android.util.Settings

class SnowflakeProxyService : Service() {

    class LocalBinder : Binder()

    private val binder = LocalBinder()

    private lateinit var snowflakeProxyWrapper: SnowflakeProxyWrapper
    private lateinit var powerConnectionReceiver: PowerConnectionReceiver
    private lateinit var regionChangedObserver: SharedPreferences.OnSharedPreferenceChangeListener

    private lateinit var networkCallbacks: ConnectivityManager.NetworkCallback

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: $intent")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        snowflakeProxyWrapper = SnowflakeProxyWrapper(this)
        registerPowerReceiver()
        regionChangedObserver =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (shouldIgnoreSnowflakePreferenceChange(key)) return@OnSharedPreferenceChangeListener
                if (key == Prefs.PREF_CAMO_APP_PACKAGE) {
                    refreshNotification()
                } else if (Regionalization.isKindnessModeDisabledForCountry(Prefs.bridgeCountry)) {
                    stopSelf()
                } else if (key == Prefs.PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING || key == Prefs.PREF_BE_A_SNOWFLAKE_LIMIT_WIFI) {
                    // user has updated constraints UI
                    startOrStopBasedOnConstraints("user updated a preference...")
                }
            }
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(regionChangedObserver)
        initNetworkCallbacks()
        refreshNotification(getString(R.string.kindness_mode_starting))
        startOrStopBasedOnConstraints("starting up, seeing if we can start...")
    }

    private fun registerPowerReceiver() {
        powerConnectionReceiver = PowerConnectionReceiver(this)
        val powerReceiverFilters = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        powerReceiverFilters.addAction(Intent.ACTION_POWER_DISCONNECTED)
        registerReceiver(powerConnectionReceiver, powerReceiverFilters)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SNOWFLAKE_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    fun refreshNotification(
        contentText: String? = null, isRunning: Boolean = snowflakeProxyWrapper.isProxyRunning()
    ) {
        val title = if (isRunning) getString(R.string.kindness_mode_is_running)
        else getString(R.string.kindness_mode_disabled)

        var icon = R.drawable.snowflake_on
        if (!snowflakeProxyWrapper.isProxyRunning()) {
            icon =
                if (contentText == getString(R.string.kindness_mode_starting)) R.drawable.snowflake_starting
                else R.drawable.snowflake_off
        }

        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingActivityIntent =
            PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE)
        val notificationBuilder =
            NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(icon)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE).setContentTitle(title)
                .setContentIntent(pendingActivityIntent).setContentText(
                    contentText ?: getString(
                        R.string.kindness_mode_active_message, Settings.snowflakesServed
                    )
                )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) notificationBuilder.setForegroundServiceBehavior(
            NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
        )
        if (Prefs.isCamoEnabled) Notifications.configureCamoNotification(notificationBuilder)
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun initNetworkCallbacks() {
        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager

        networkCallbacks = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                refreshNotification(
                    getString(R.string.kindness_mode_disabled_internet),
                    isRunning = false
                )
                stopSnowflakeProxy("lost network (limit wifi=${Prefs.limitSnowflakeProxyingWifi()}")
            }

            override fun onAvailable(network: Network) {
                stopSnowflakeProxy("stopping to refresh NAT type")
                startOrStopBasedOnConstraints("joined new network...")
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallbacks)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        Notifications.createCamoflaugeableNotificationChannel(
            this, CHANNEL_ID, R.string.volunteer_mode
        )
    }


    private fun startSnowflakeProxy(logReason: String? = null) {
        Log.d(TAG, "Starting snowflake proxy - $logReason")
        snowflakeProxyWrapper.enableProxy()
    }

    internal fun stopSnowflakeProxy(logMessage: String? = null) {
        Log.d(TAG, "Stopping snowflake proxy - reason: $logMessage")
        Prefs.lastSnowflakeNatType = IPtProxy.IPtProxy.NATUnknown
        snowflakeProxyWrapper.stopProxy()
    }

    internal fun startOrStopBasedOnConstraints(logMessage: String) {
        Log.d(
            TAG, "proxy event triggered: $logMessage" +
                    "(limit wifi=${Prefs.limitSnowflakeProxyingWifi()}, " +
                    "limit power=${Prefs.limitSnowflakeProxyingCharging()}"
        )

        if (!NetworkUtils.isNetworkAvailableForKindnessMode(this)) {
            // there's no network available
            if (Prefs.limitSnowflakeProxyingWifi()) {
                refreshNotification(
                    getString(R.string.kindness_mode_disabled_wifi),
                    isRunning = false
                )
                stopSnowflakeProxy("Wifi Constraint not met")
            } else {
                refreshNotification(
                    getString(R.string.kindness_mode_disabled_internet),
                    isRunning = false
                )
                stopSnowflakeProxy("Internet not available")
            }
            return
        }

        if (Prefs.limitSnowflakeProxyingCharging()) {
            val batteryStatus: Intent? =
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
            Log.d(TAG, "battery status=$status, isCharging=$isCharging")
            if (!isCharging) {
                refreshNotification(
                    getString(R.string.kindness_mode_disabled_power),
                    isRunning = false
                )
                stopSnowflakeProxy("power condition not met")
                return
            } else {
                Log.d(TAG, "power condition met")
            }
        }

        // if we haven't quit at this point, we can start snowflake proxy
        startSnowflakeProxy(logMessage)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        unregisterReceiver(powerConnectionReceiver)
        (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).apply {
            unregisterNetworkCallback(networkCallbacks)
        }
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(regionChangedObserver)
        stopSnowflakeProxy("in onDestroy()")
    }

    companion object {
        const val TAG = "SnowflakeProxyService"
        private const val NOTIFICATION_ID = 103
        private const val CHANNEL_ID = "snowflake"
        private const val ACTION_STOP_SNOWFLAKE_SERVICE = "ACTION_STOP_SNOWFLAKE_SERVICE"

        fun shouldIgnoreSnowflakePreferenceChange(key: String?): Boolean =
            key != Prefs.PREF_BRIDGE_COUNTRY &&
                    key != Prefs.PREF_CAMO_APP_PACKAGE &&
                    key != Prefs.PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING &&
                    key != Prefs.PREF_BE_A_SNOWFLAKE_LIMIT_WIFI


        // Read by the watchdog to tell "off because the user said so" apart
        // from "off because the system killed us" (#1799, #1783).
        @Volatile
        var isRunning = false
            private set

        private fun getIntent(context: Context) =
            Intent(context, SnowflakeProxyService::class.java)

        // start this service, but not necessarily snowflake proxy from the app UI
        fun startSnowflakeProxyForegroundService(context: Context) =
            ContextCompat.startForegroundService(context, getIntent(context))

        // stop this service, and snowflake proxy if its running, from the app UI
        fun stopSnowflakeProxyForegroundService(context: Context) =
            ContextCompat.startForegroundService(
                context, getIntent(context).setAction(ACTION_STOP_SNOWFLAKE_SERVICE)
            )
    }
}
