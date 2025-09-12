@file:Suppress("NullableBooleanElvis")

package org.torproject.android.service.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.torproject.android.service.circumvention.Transport
import java.util.Locale
import java.util.concurrent.TimeUnit

object Prefs {
    private const val PREF_BRIDGES_LIST = "pref_bridges_list"
    private const val PREF_DEFAULT_LOCALE = "pref_default_locale"
    private const val PREF_DETECT_ROOT = "pref_detect_root"
    private const val PREF_ENABLE_LOGGING = "pref_enable_logging"
    private const val PREF_START_ON_BOOT = "pref_start_boot"
    private const val PREF_ALLOW_BACKGROUND_STARTS = "pref_allow_background_starts"
    private const val PREF_OPEN_PROXY_ON_ALL_INTERFACES = "pref_open_proxy_on_all_interfaces"
    private const val PREF_USE_VPN = "pref_vpn"
    private const val PREF_EXIT_NODES = "pref_exit_nodes"
    private const val PREF_BE_A_SNOWFLAKE = "pref_be_a_snowflake"
    private const val PREF_SHOW_SNOWFLAKE_MSG = "pref_show_snowflake_proxy_msg"
    private const val PREF_BE_A_SNOWFLAKE_LIMIT_WIFI = "pref_be_a_snowflake_limit_wifi"
    private const val PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING = "pref_be_a_snowflake_limit_charing"

    private const val PREF_USE_SMART_CONNECT = "pref_use_smart_connect"
    private const val PREF_SMART_CONNECT_TIMEOUT = "pref_smart_connect_timeout"

    private const val PREF_POWER_USER_MODE = "pref_power_user"


    private const val PREF_HOST_ONION_SERVICES = "pref_host_onionservices"

    private const val PREF_SNOWFLAKES_SERVED_COUNT = "pref_snowflakes_served"
    private const val PREF_SNOWFLAKES_SERVED_COUNT_WEEKLY = "pref_snowflakes_served_weekly"

    private const val PREF_CURRENT_VERSION = "pref_current_version"

    private const val PREF_CAMO_APP_PACKAGE = "pref_key_camo_app"
    private const val PREF_CAMO_APP_DISPLAY_NAME = "pref_key_camo_app_display_name"
    private const val PREF_REQUIRE_PASSWORD = "pref_require_password"
    private const val PREF_DISALLOW_BIOMETRIC_AUTH = "pref_auth_no_biometrics"

    private const val PREF_CONNECTION_PATHWAY = "pref_connection_pathway"

    const val PREF_SECURE_WINDOW_FLAG: String = "pref_flag_secure"

    private var prefs: SharedPreferences? = null

    var currentVersionForUpdate: Int
        get() = prefs?.getInt(PREF_CURRENT_VERSION, 0) ?: 0
        set(version) = putInt(PREF_CURRENT_VERSION, version)

    private const val PREF_REINSTALL_GEOIP = "pref_geoip"

    @JvmStatic
    var isGeoIpReinstallNeeded: Boolean
        get() = prefs?.getBoolean(PREF_REINSTALL_GEOIP, true) ?: true
        set(value) = putBoolean(PREF_REINSTALL_GEOIP, value)

    @JvmStatic
    fun setContext(context: Context?) {
        if (prefs == null) prefs = getSharedPrefs(context)
    }

    fun initWeeklyWorker() {
        val myWorkBuilder =
            PeriodicWorkRequest.Builder(ResetSnowflakesServedWeeklyWorker::class.java, 7, TimeUnit.DAYS)

        val myWork = myWorkBuilder.build()
        WorkManager.getInstance()
            .enqueueUniquePeriodicWork("prefsWeeklyWorker", ExistingPeriodicWorkPolicy.KEEP, myWork)
    }

    private fun putBoolean(key: String?, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    private fun putInt(key: String?, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    private fun putString(key: String?, value: String?) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    @JvmStatic
    fun hostOnionServicesEnabled(): Boolean {
        return prefs?.getBoolean(PREF_HOST_ONION_SERVICES, true) ?: true
    }

    @JvmStatic
    fun putHostOnionServicesEnabled(value: Boolean) {
        putBoolean(PREF_HOST_ONION_SERVICES, value)
    }

    @JvmStatic
    var bridgesList: List<String>
        get() {
            return prefs?.getString(PREF_BRIDGES_LIST, null)
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                ?.map { it.trim() }
                ?: emptyList()
        }
        set(value) {
            putString(PREF_BRIDGES_LIST,
                value.filter { it.isNotBlank() }.joinToString("\n") { it.trim() })
        }

    @JvmStatic
    var defaultLocale: String
        get() = prefs?.getString(PREF_DEFAULT_LOCALE, null) ?: Locale.getDefault().language
        set(value) = putString(PREF_DEFAULT_LOCALE, value)

    fun detectRoot(): Boolean {
        return prefs?.getBoolean(PREF_DETECT_ROOT, true) ?: true
    }

    @JvmStatic
    fun beSnowflakeProxy(): Boolean {
        return prefs?.getBoolean(PREF_BE_A_SNOWFLAKE, false) ?: false
    }

    fun showSnowflakeProxyToast(): Boolean {
        return prefs?.getBoolean(PREF_SHOW_SNOWFLAKE_MSG, false) ?: false
    }

    @JvmStatic
    fun setBeSnowflakeProxy(beSnowflakeProxy: Boolean) {
        putBoolean(PREF_BE_A_SNOWFLAKE, beSnowflakeProxy)
    }

    fun setBeSnowflakeProxyLimitWifi(beSnowflakeProxy: Boolean) {
        putBoolean(PREF_BE_A_SNOWFLAKE_LIMIT_WIFI, beSnowflakeProxy)
    }

    fun setBeSnowflakeProxyLimitCharging(beSnowflakeProxy: Boolean) {
        putBoolean(PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING, beSnowflakeProxy)
    }

    @JvmStatic
    fun limitSnowflakeProxyingWifi(): Boolean {
        return prefs?.getBoolean(PREF_BE_A_SNOWFLAKE_LIMIT_WIFI, false) ?: false
    }

    @JvmStatic
    fun limitSnowflakeProxyingCharging(): Boolean {
        return prefs?.getBoolean(PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING, false) ?: false
    }

    @JvmStatic
    fun useDebugLogging(): Boolean {
        return prefs?.getBoolean(PREF_ENABLE_LOGGING, false) ?: false
    }

    fun allowBackgroundStarts(): Boolean {
        return prefs?.getBoolean(PREF_ALLOW_BACKGROUND_STARTS, true) ?: true
    }

    @JvmStatic
    fun openProxyOnAllInterfaces(): Boolean {
        return prefs?.getBoolean(PREF_OPEN_PROXY_ON_ALL_INTERFACES, false) ?: false
    }

    @JvmStatic
    fun useVpn(): Boolean {
        return prefs?.getBoolean(PREF_USE_VPN, false) ?: false
    }

    @JvmStatic
    fun putUseVpn(value: Boolean) {
        putBoolean(PREF_USE_VPN, value)
    }

    fun startOnBoot(): Boolean {
        return prefs?.getBoolean(PREF_START_ON_BOOT, true) ?: true
    }

    @JvmStatic
    fun putStartOnBoot(value: Boolean) {
        putBoolean(PREF_START_ON_BOOT, value)
    }

    @JvmStatic
    var exitNodes: String?
        get() = prefs?.getString(PREF_EXIT_NODES, null)
        set(country) {
            putString(PREF_EXIT_NODES, country)
        }

    @JvmStatic
    fun getSharedPrefs(context: Context?): SharedPreferences? {
        //   return context.getSharedPreferences(OrbotConstants.PREF_TOR_SHARED_PREFS, Context.MODE_MULTI_PROCESS);
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    val snowflakesServed: Int
        get() = prefs?.getInt(PREF_SNOWFLAKES_SERVED_COUNT, 0) ?: 0

    val snowflakesServedWeekly: Int
        get() = prefs?.getInt(PREF_SNOWFLAKES_SERVED_COUNT_WEEKLY, 0) ?: 0

    fun addSnowflakeServed() {
        putInt(PREF_SNOWFLAKES_SERVED_COUNT, snowflakesServed + 1)
        putInt(PREF_SNOWFLAKES_SERVED_COUNT_WEEKLY, snowflakesServedWeekly + 1)
    }

    fun resetSnowflakesServedWeekly() {
        putInt(PREF_SNOWFLAKES_SERVED_COUNT_WEEKLY, 0)
    }

    @JvmStatic
    var transport: Transport
        /**
         * @return How Orbot is configured to attempt to connect to Tor
         */
        get() = Transport.fromId(prefs?.getString(PREF_CONNECTION_PATHWAY, null) ?: Transport.NONE.id)
        /**
         * Set how Orbot should initialize a tor connection (direct, with a PT, etc)
         */
        set(value) = putString(PREF_CONNECTION_PATHWAY, value.id)

    var smartConnect: Boolean
        get() = prefs?.getBoolean(PREF_USE_SMART_CONNECT, false) ?: false
        set(value) = putBoolean(PREF_USE_SMART_CONNECT, value)

    var smartConnectTimeout: Int
        get() = prefs?.getInt(PREF_SMART_CONNECT_TIMEOUT, 30) ?: 30
        set(value) = putInt(PREF_SMART_CONNECT_TIMEOUT, value)

    val isPowerUserMode: Boolean
        get() = prefs?.getBoolean(PREF_POWER_USER_MODE, false) ?: false

    var isSecureWindow: Boolean
        get() = prefs?.getBoolean(PREF_SECURE_WINDOW_FLAG, true) ?: true
        set(isFlagSecure) = putBoolean(PREF_SECURE_WINDOW_FLAG, isFlagSecure)

    const val DEFAULT_CAMO_DISABLED_ACTIVITY: String = "org.torproject.android.OrbotActivity"

    @JvmStatic
    val isCamoEnabled: Boolean
        get() {
            val app = prefs?.getString(PREF_CAMO_APP_PACKAGE, DEFAULT_CAMO_DISABLED_ACTIVITY) ?: ""
            return app != DEFAULT_CAMO_DISABLED_ACTIVITY
        }

    val selectedCamoApp: String
        get() = prefs?.getString(PREF_CAMO_APP_PACKAGE, DEFAULT_CAMO_DISABLED_ACTIVITY) ?: ""

    fun setCamoAppPackage(packageName: String?) {
        putString(PREF_CAMO_APP_PACKAGE, packageName)
    }

    var camoAppDisplayName: String?
        get() = prefs?.getString(PREF_CAMO_APP_DISPLAY_NAME, "Android")
        set(name) = putString(PREF_CAMO_APP_DISPLAY_NAME, name)

    val requireDeviceAuthentication: Boolean
        get() = prefs?.getBoolean(PREF_REQUIRE_PASSWORD, false) ?: false

    val disallowBiometricAuthentication: Boolean
        get() = prefs?.getBoolean(PREF_DISALLOW_BIOMETRIC_AUTH, false) ?: false
}
