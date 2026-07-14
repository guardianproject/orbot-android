package org.torproject.android.util

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.torproject.android.Regionalization
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.tor.ShadowSocks
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.concurrent.TimeUnit

object Prefs {
    private const val PREF_BRIDGES_LIST = "pref_bridges_list"
    const val PREF_BRIDGE_COUNTRY = "pref_bridge_country"
    const val PREF_DEFAULT_LOCALE = "pref_default_locale"
    private const val PREF_DETECT_ROOT = "pref_detect_root"
    private const val PREF_ENABLE_LOGGING = "pref_enable_logging"
    private const val PREF_START_ON_BOOT = "pref_start_boot"
    private const val PREF_ALLOW_BACKGROUND_STARTS = "pref_allow_background_starts"
    const val PREF_OPEN_PROXY_ON_ALL_INTERFACES = "pref_open_proxy_on_all_interfaces"
    private const val PREF_USE_VPN = "pref_vpn"
    private const val PREF_LAST_SNOWFLAKE_QUALITY_CHECK = "pref_last_snowflake_quality_check"
    private const val PREF_EXIT_NODES = "pref_exit_nodes"
    private const val PREF_BE_A_SNOWFLAKE = "pref_be_a_snowflake"
    private const val PREF_SHOW_SNOWFLAKE_MSG = "pref_show_snowflake_proxy_msg"
    private const val PREF_BE_A_SNOWFLAKE_LIMIT_WIFI = "pref_be_a_snowflake_limit_wifi"
    private const val PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING = "pref_be_a_snowflake_limit_charing"
    const val PREF_LAST_SNOWFLAKE_NAT_TYPE = "pref_snowflake_last_nat"
    const val PREF_LAST_SNOWFLAKE_ACTIVE = "pref_is_snowflake_running"

    private const val PREF_USE_SMART_CONNECT = "pref_use_smart_connect"
    private const val PREF_SMART_CONNECT_TIMEOUT = "pref_smart_connect_timeout"

    private const val PREF_POWER_USER_MODE = "pref_power_user"

    private const val PREF_SNOWFLAKES_SERVED_COUNT = "pref_snowflakes_served"
    private const val PREF_SNOWFLAKES_SERVED_COUNT_WEEKLY = "pref_snowflakes_served_weekly"

    private const val PREF_CURRENT_VERSION = "pref_current_version"

    private const val PREF_CAMO_APP_PACKAGE = "pref_key_camo_app"
    private const val PREF_CAMO_APP_DISPLAY_NAME = "pref_key_camo_app_display_name"
    private const val PREF_CAMO_APP_ALT_ICON_INDEX = "pref_key_camo_alticon"
    const val PREF_REQUIRE_PASSWORD = "pref_require_password"
    const val PREF_DISALLOW_BIOMETRIC_AUTH = "pref_auth_no_biometrics"

    private const val PREF_CONNECTION_PATHWAY = "pref_connection_pathway"

    const val PREF_SECURE_WINDOW_FLAG: String = "pref_flag_secure"

    private const val PREF_POWER_BATTERY_DIALOG_HIDE = "hide_battery_opt_dialog"
    const val PREF_ORBOT_SERVICE_LOG = "pref_orbotservice_log"


    const val PREF_REACHABLE_ADDRESSES = "pref_reachable_addresses"
    const val PREF_REACHABLE_ADDRESSES_PORTS = "pref_reachable_addresses_ports"

    const val PREF_DNSPORT = "pref_dnsport"
    const val PREF_HTTP = "pref_http"
    const val PREF_SOCKS = "pref_socks"
    const val PREF_TRANSPORT = "pref_transport"

    const val PREF_DNS_PORT_RESOLVED = "PREFS_DNS_PORT"

    const val PREF_ISOLATE_DEST = "pref_isolate_dest"
    const val PREF_ISOLATE_PORT = "pref_isolate_port"
    const val PREF_ISOLATE_PROTOCOL = "pref_isolate_protocol"
    const val PREF_ISOLATE_KEEP_ALIVE = "pref_isolate_keep_alive"

    const val PREF_CONNECTION_PADDING = "pref_connection_padding"
    const val PREF_REDUCED_CONNECTION_PADDING = "pref_reduced_connection_padding"
    const val PREF_CIRCUIT_PADDING = "pref_circuit_padding"
    const val PREF_REDUCED_CIRCUIT_PADDING = "pref_reduced_circuit_padding"

    const val PREF_PREFER_IPV6 = "pref_prefer_ipv6"
    const val PREF_DISABLE_IPV4 = "pref_disable_ipv4"

    const val PREF_PROXY_HOST = "pref_proxy_host"
    const val PREF_SHADOW_SOCKS_PROXY = "pref_proxy_ss"
    const val PREF_PROXY_TYPE = "pref_proxy_type"
    const val PREF_PROXY_USERNAME = "pref_proxy_username"
    const val PREF_PROXY_PASSWORD = "pref_proxy_password"
    const val PREF_PROXY_PORT = "pref_proxy_port"

    const val PREF_CUSTOM_TORRC = "pref_custom_torrc"

    const val PREF_PERSISTENT_NOTIFICATIONS = "pref_persistent_notifications"
    const val PREF_KEY_CAMO_DIALOG = "pref_key_camo_dialog"
    private const val DEFAULT_PORT_TORRC_AUTO = "auto"
    private const val PREF_TORIFIED_APPS = "PrefTord"
    private const val PREF_APP_KEY_TORIFIED = "_app_tor"

    private var cr: ContentResolver? = null

    var currentVersionForUpdate: Int
        get() = cr?.getPrefInt(PREF_CURRENT_VERSION) ?: 0
        set(version) = cr?.putPref(PREF_CURRENT_VERSION, version) ?: Unit


    private const val PREF_REINSTALL_GEOIP = "pref_geoip"

    @JvmStatic
    var isGeoIpReinstallNeeded: Boolean
        get() = cr?.getPrefBoolean(PREF_REINSTALL_GEOIP) ?: true
        set(value) = cr?.putPref(PREF_REINSTALL_GEOIP, value) ?: Unit

    @JvmStatic
    fun setContext(context: Context?) {
        if (cr == null) {
            cr = context?.contentResolver
        }
    }

    fun initWeeklyWorker(context: Context) {
        val myWorkBuilder =
            PeriodicWorkRequest.Builder(
                ResetSnowflakesServedWeeklyWorker::class.java,
                7,
                TimeUnit.DAYS
            )

        val myWork = myWorkBuilder.build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                uniqueWorkName = "prefsWeeklyWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                myWork
            )
    }

    @JvmStatic
    var bridgesList: List<String>
        get() {
            return cr?.getPrefString(PREF_BRIDGES_LIST)
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                ?.map { it.trim() }
                ?: emptyList()
        }
        set(value) {
            cr?.putPref(
                PREF_BRIDGES_LIST,
                value.filter { it.isNotBlank() }.joinToString("\n") { it.trim() })
        }

    var bridgeCountry: String?
        get() = cr?.getPrefString(PREF_BRIDGE_COUNTRY)
        set(value) {
            cr?.let {
                it.putPref(PREF_BRIDGE_COUNTRY, value)
                if (Regionalization.isKindnessModeDisabledForCountry()) {
                    beSnowflakeProxy = false
                    snowflakeNeedsQualityCheck = true
                }
            }
        }

    @JvmStatic
    var defaultLocale: String
        get() = cr?.getPrefString(PREF_DEFAULT_LOCALE) ?: Locale.getDefault().language
        set(value) = cr?.putPref(PREF_DEFAULT_LOCALE, value) ?: Unit

    fun detectRoot(): Boolean {
        return cr?.getPrefBoolean(PREF_DETECT_ROOT) ?: true
    }

    var beSnowflakeProxy: Boolean
        get() = cr?.getPrefBoolean(PREF_BE_A_SNOWFLAKE) ?: false
        set(value) = cr?.putPref(PREF_BE_A_SNOWFLAKE, value) ?: Unit

    fun showSnowflakeProxyToast(): Boolean {
        return cr?.getPrefBoolean(PREF_SHOW_SNOWFLAKE_MSG) ?: false
    }

    fun setBeSnowflakeProxyLimitWifi(beSnowflakeProxy: Boolean) {
        cr?.putPref(PREF_BE_A_SNOWFLAKE_LIMIT_WIFI, beSnowflakeProxy)
    }

    fun setBeSnowflakeProxyLimitCharging(beSnowflakeProxy: Boolean) {
        cr?.putPref(PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING, beSnowflakeProxy)
    }

    fun limitSnowflakeProxyingWifi(): Boolean {
        return cr?.getPrefBoolean(PREF_BE_A_SNOWFLAKE_LIMIT_WIFI) ?: false
    }

    fun limitSnowflakeProxyingCharging(): Boolean {
        return cr?.getPrefBoolean(PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING) ?: false
    }

    @JvmStatic
    fun useDebugLogging(): Boolean {
        return cr?.getPrefBoolean(PREF_ENABLE_LOGGING) ?: false
    }

    fun allowBackgroundStarts(): Boolean {
        return cr?.getPrefBoolean(PREF_ALLOW_BACKGROUND_STARTS) ?: true
    }

    fun openProxyOnAllInterfaces(context: Context): Boolean {
        val prefSet = cr?.getPrefBoolean(PREF_OPEN_PROXY_ON_ALL_INTERFACES) ?: false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            // on API 37+ this also needs the ACCESS_LOCAL_NETWORK permission
            return prefSet && NetworkUtils.needsAccessLocalNetworkPermission(context) != true
        }
        return prefSet
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    fun resetOpenProxyOnAllInterfacesIfPermissionRevoked(context: Context) {
        // if the preference was set
        if (cr?.getPrefBoolean(PREF_OPEN_PROXY_ON_ALL_INTERFACES) ?: false) {
            // but the permission was revoked by the user outside Orbot
            if (NetworkUtils.needsAccessLocalNetworkPermission(context) == true) {
                cr?.putPref(PREF_OPEN_PROXY_ON_ALL_INTERFACES, false)
            }
        }
    }

    @JvmStatic
    fun useVpn(): Boolean {
        return cr?.getPrefBoolean(PREF_USE_VPN) ?: false
    }

    @JvmStatic
    fun putUseVpn(value: Boolean) {
        cr?.putPref(PREF_USE_VPN, value)
    }

    var snowflakeNeedsQualityCheck: Boolean
        get() {
            val last = cr?.getPrefLong(PREF_LAST_SNOWFLAKE_QUALITY_CHECK) ?: 0

            // A new quality check should be done every 24 hours.
            return last <= System.currentTimeMillis() - 24 * 60 * 60 * 1000
        }
        set(value) {
            cr?.putPref(
                PREF_LAST_SNOWFLAKE_QUALITY_CHECK,
                if (value) 0 else System.currentTimeMillis()
            )
        }

    fun startOnBoot(): Boolean {
        return cr?.getPrefBoolean(PREF_START_ON_BOOT, true) ?: true
    }

    @JvmStatic
    var exitNodes: String?
        get() = cr?.getPrefString(PREF_EXIT_NODES)
        set(country) = cr?.putPref(PREF_EXIT_NODES, country) ?: Unit

    var lastSnowflakeNatType: String
        get() = cr?.getPrefString(PREF_LAST_SNOWFLAKE_NAT_TYPE) ?: IPtProxy.IPtProxy.NATUnknown
        set(natType) = cr?.putPref(PREF_LAST_SNOWFLAKE_NAT_TYPE, natType) ?: Unit

    var snowflakeProxyRunning: Boolean
        get() = cr?.getPrefBoolean(PREF_LAST_SNOWFLAKE_ACTIVE) ?: false
        set(isRunning) = cr?.putPref(PREF_LAST_SNOWFLAKE_ACTIVE, isRunning) ?: Unit

    val snowflakesServed: Int
        get() = cr?.getPrefInt(PREF_SNOWFLAKES_SERVED_COUNT) ?: 0

    val snowflakesServedWeekly: Int
        get() = cr?.getPrefInt(PREF_SNOWFLAKES_SERVED_COUNT_WEEKLY) ?: 0

    fun addSnowflakeServed() {
        cr?.putPref(PREF_SNOWFLAKES_SERVED_COUNT, snowflakesServed + 1)
        cr?.putPref(PREF_SNOWFLAKES_SERVED_COUNT_WEEKLY, snowflakesServedWeekly + 1)
    }

    fun resetSnowflakesServedWeekly() {
        cr?.putPref(PREF_SNOWFLAKES_SERVED_COUNT_WEEKLY, 0)
    }

    @JvmStatic
    var transport: Transport
        /**
         * @return How Orbot is configured to attempt to connect to Tor
         */
        get() = Transport.fromId(cr?.getPrefString(PREF_CONNECTION_PATHWAY) ?: Transport.NONE.id)
        /**
         * Set how Orbot should initialize a tor connection (direct, with a PT, etc)
         */
        set(value) = cr?.putPref(PREF_CONNECTION_PATHWAY, value.id) ?: Unit

    var smartConnect: Boolean
        get() = cr?.getPrefBoolean(PREF_USE_SMART_CONNECT) ?: false
        set(value) = cr?.putPref(PREF_USE_SMART_CONNECT, value) ?: Unit


    var smartConnectTimeout: Int
        get() = cr?.getPrefInt(PREF_SMART_CONNECT_TIMEOUT) ?: 30
        set(value) = cr?.putPref(PREF_SMART_CONNECT_TIMEOUT, value) ?: Unit

    // URI, if config present + valid, malformed URL string if config present + invalid
    val outboundProxy: Pair<URI?, String?>
        get() {
            val scheme = cr?.getPrefString(PREF_PROXY_TYPE)?.lowercase()?.trim()
            if (scheme.isNullOrEmpty()) return Pair(null, null)

            if (scheme == ShadowSocks.SCHEME) {
                val config = cr?.getPrefString(PREF_SHADOW_SOCKS_PROXY)?.trim()
                if (config.isNullOrEmpty()) return Pair(null, null)

                return try {
                    Pair(URI(config), null)
                } catch (_: URISyntaxException) {
                    Pair(null, config)
                }
            }

            val host = cr?.getPrefString(PREF_PROXY_HOST)?.trim()
            if (host.isNullOrEmpty()) return Pair(null, null)

            val url = StringBuilder(scheme)
            url.append("://")

            var needsAt = false
            val username = cr?.getPrefString(PREF_PROXY_USERNAME)
            if (!username.isNullOrEmpty()) {
                url.append(username)
                needsAt = true
            }

            val password = cr?.getPrefString(PREF_PROXY_PASSWORD)
            if (!password.isNullOrEmpty()) {
                url.append(":")
                url.append(password)
                needsAt = true
            }

            if (needsAt) url.append("@")

            url.append(host)

            val port = try {
                cr?.getPrefString(PREF_PROXY_PORT)?.trim()?.toInt() ?: 0
            } catch (_: Throwable) {
                0
            }

            if (port in 1..<65536) {
                url.append(":")
                url.append(port)
            }

            url.append("/")

            return try {
                Pair(URI(url.toString()), null)
            } catch (_: URISyntaxException) {
                // can happen when you say put a space in the hostname
                // https://github.com/guardianproject/orbot-android/issues/1563
                // https://www.rfc-editor.org/rfc/inline-errata/rfc3986.html
                Pair(
                    null,
                    url.toString()
                )
            }
        }

    val isPowerUserMode: Boolean
        get() = cr?.getPrefBoolean(PREF_POWER_USER_MODE) ?: false

    var isSecureWindow: Boolean
        get() = cr?.getPrefBoolean(PREF_SECURE_WINDOW_FLAG) ?: false
        set(isFlagSecure) = cr?.putPref(PREF_SECURE_WINDOW_FLAG, isFlagSecure) ?: Unit

    const val DEFAULT_CAMO_DISABLED_ACTIVITY: String = "org.torproject.android.OrbotActivity"

    /**
     * Returns true if a non-Orbot icon is in use (ie Birdie, Paint, etc)
     * When true, conceal information about Tor in notifications
     *
     * Returns false if icon is changed to an alt Orbot icon
     */
    @JvmStatic
    val isCamoEnabled: Boolean
        get() {
            val app = cr?.getPrefString(PREF_CAMO_APP_PACKAGE, DEFAULT_CAMO_DISABLED_ACTIVITY) ?: ""
            if (camoAppAltIconIndex != -1) return false
            return app != DEFAULT_CAMO_DISABLED_ACTIVITY
        }

    val selectedCamoApp: String
        get() = cr?.getPrefString(PREF_CAMO_APP_PACKAGE, DEFAULT_CAMO_DISABLED_ACTIVITY) ?: ""

    fun setCamoAppPackage(packageName: String?) {
        cr?.putPref(PREF_CAMO_APP_PACKAGE, packageName)
    }

    var camoAppDisplayName: String?
        get() = cr?.getPrefString(PREF_CAMO_APP_DISPLAY_NAME) ?: "Android"
        set(name) = cr?.putPref(PREF_CAMO_APP_DISPLAY_NAME, name) ?: Unit

    var camoAppAltIconIndex: Int?
        get() = cr?.getPrefInt(PREF_CAMO_APP_ALT_ICON_INDEX, -1)
        set(index) = cr?.putPref(PREF_CAMO_APP_ALT_ICON_INDEX, index ?: -1) ?: Unit


    val requireDeviceAuthentication: Boolean
        get() = cr?.getPrefBoolean(PREF_REQUIRE_PASSWORD) ?: false

    val disallowBiometricAuthentication: Boolean
        get() = cr?.getPrefBoolean(PREF_DISALLOW_BIOMETRIC_AUTH) ?: false

    val proxySocksPort: String
        get() = cr?.getPrefString(PREF_SOCKS) ?: DEFAULT_PORT_TORRC_AUTO

    val proxyHttpPort: String
        get() = cr?.getPrefString(PREF_HTTP) ?: DEFAULT_PORT_TORRC_AUTO

    val connectionPadding: Boolean
        get() = cr?.getPrefBoolean(PREF_CONNECTION_PADDING) ?: false

    val reducedConnectionPadding: Boolean
        get() = cr?.getPrefBoolean(PREF_REDUCED_CONNECTION_PADDING) ?: true

    val circuitPadding: Boolean
        get() = cr?.getPrefBoolean(PREF_CIRCUIT_PADDING) ?: true

    val reducedCircuitPadding: Boolean
        get() = cr?.getPrefBoolean(PREF_REDUCED_CIRCUIT_PADDING) ?: true

    val torTransPort: String
        get() = cr?.getPrefString(PREF_TRANSPORT) ?: DEFAULT_PORT_TORRC_AUTO

    val torDnsPort: String
        get() = cr?.getPrefString(PREF_DNSPORT) ?: DEFAULT_PORT_TORRC_AUTO

    val entryNodes: String?
        get() = cr?.getPrefString("pref_entrance_nodes")

    val excludeNodes: String?
        get() = cr?.getPrefString("pref_exclude_nodes")

    val strictNodes: Boolean
        get() = cr?.getPrefBoolean("pref_strict_nodes") ?: false

    val reachableAddresses: Boolean
        get() = cr?.getPrefBoolean(PREF_REACHABLE_ADDRESSES) ?: false

    val reachableAddressesPorts: String?
        get() = cr?.getPrefString(PREF_REACHABLE_ADDRESSES_PORTS)

    val customTorRc: String
        get() = cr?.getPrefString(PREF_CUSTOM_TORRC) ?: ""

    val isolateDest: Boolean
        get() = cr?.getPrefBoolean(PREF_ISOLATE_DEST) ?: false

    val isolatePort: Boolean
        get() = cr?.getPrefBoolean(PREF_ISOLATE_PORT) ?: false

    val isolateProtocol: Boolean
        get() = cr?.getPrefBoolean(PREF_ISOLATE_PROTOCOL) ?: false

    val isolateKeepAlive: Boolean
        get() = cr?.getPrefBoolean(PREF_ISOLATE_KEEP_ALIVE) ?: false

    val preferIpv6: Boolean
        get() = cr?.getPrefBoolean(PREF_PREFER_IPV6) ?: true

    val disableIpv4: Boolean
        get() = cr?.getPrefBoolean(PREF_DISABLE_IPV4) ?: false

    var torifiedApps: String
        get() = cr?.getPrefString(PREF_TORIFIED_APPS) ?: ""
        set(value) = cr?.putPref(PREF_TORIFIED_APPS, value) ?: Unit

    var stopShowingPowerUserBatteryOptDialog: Boolean
        get() = cr?.getPrefBoolean(PREF_POWER_BATTERY_DIALOG_HIDE) ?: false
        set(value) = cr?.putPref(PREF_POWER_BATTERY_DIALOG_HIDE, value) ?: Unit


    @JvmStatic
    var torDnsPortResolved: Int
        get() = cr?.getPrefInt(PREF_DNS_PORT_RESOLVED) ?: 0
        set(value) = cr?.putPref(PREF_DNS_PORT_RESOLVED, value) ?: Unit

    @JvmStatic
    fun isAppTorified(appId: String): Boolean {
        return cr?.getPrefBoolean("$appId$PREF_APP_KEY_TORIFIED") ?: true
    }

    @JvmStatic
    fun orbotServiceLogClear() {
        cr?.putPref(PREF_ORBOT_SERVICE_LOG, "")
    }

    @JvmStatic
    fun orbotServiceLogAppend(logLine: String) {
        cr?.putPref(PREF_ORBOT_SERVICE_LOG, getOrbotServiceLog() + "\n" + logLine)
    }

    fun getOrbotServiceLog(): String {
        return cr?.getPrefString(PREF_ORBOT_SERVICE_LOG) ?: ""
    }
}
