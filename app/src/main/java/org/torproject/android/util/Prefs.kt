package org.torproject.android.util

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import org.torproject.android.Regionalization
import java.util.Locale

@Deprecated("Move to Settings object instead")
object Prefs {
    const val PREF_BRIDGE_COUNTRY = "pref_bridge_country"
    const val PREF_DEFAULT_LOCALE = "pref_default_locale"
    private const val PREF_DETECT_ROOT = "pref_detect_root"
    private const val PREF_ENABLE_LOGGING = "pref_enable_logging"
    private const val PREF_START_ON_BOOT = "pref_start_boot"
    const val PREF_OPEN_PROXY_ON_ALL_INTERFACES = "pref_open_proxy_on_all_interfaces"
    private const val PREF_EXIT_NODES = "pref_exit_nodes"
    private const val PREF_SHOW_SNOWFLAKE_MSG = "pref_show_snowflake_proxy_msg"
    const val PREF_BE_A_SNOWFLAKE_LIMIT_WIFI = "pref_be_a_snowflake_limit_wifi"
    const val PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING = "pref_be_a_snowflake_limit_charing"
    const val PREF_LAST_SNOWFLAKE_NAT_TYPE = "pref_snowflake_last_nat"
    const val PREF_LAST_SNOWFLAKE_ACTIVE = "pref_is_snowflake_running"

    private const val PREF_POWER_USER_MODE = "pref_power_user"

    const val PREF_CAMO_APP_PACKAGE = "pref_key_camo_app"
    const val PREF_REQUIRE_PASSWORD = "pref_require_password"
    const val PREF_DISALLOW_BIOMETRIC_AUTH = "pref_auth_no_biometrics"

    const val PREF_SECURE_WINDOW_FLAG: String = "pref_flag_secure"

    const val PREF_ORBOT_SERVICE_LOG = "pref_orbotservice_log"


    private const val PREF_REACHABLE_ADDRESSES = "pref_reachable_addresses"
    private const val PREF_REACHABLE_ADDRESSES_PORTS = "pref_reachable_addresses_ports"

    private const val PREF_DNSPORT = "pref_dnsport"
    const val PREF_HTTP = "pref_http"
    const val PREF_SOCKS = "pref_socks"
    private const val PREF_TRANSPORT = "pref_transport"

    private const val PREF_ISOLATE_DEST = "pref_isolate_dest"
    private const val PREF_ISOLATE_PORT = "pref_isolate_port"
    private const val PREF_ISOLATE_PROTOCOL = "pref_isolate_protocol"
    private const val PREF_ISOLATE_KEEP_ALIVE = "pref_isolate_keep_alive"

    private const val PREF_CONNECTION_PADDING = "pref_connection_padding"
    private const val PREF_REDUCED_CONNECTION_PADDING = "pref_reduced_connection_padding"
    private const val PREF_CIRCUIT_PADDING = "pref_circuit_padding"
    private const val PREF_REDUCED_CIRCUIT_PADDING = "pref_reduced_circuit_padding"

    private const val PREF_PREFER_IPV6 = "pref_prefer_ipv6"
    private const val PREF_DISABLE_IPV4 = "pref_disable_ipv4"

    const val PREF_CUSTOM_TORRC = "pref_custom_torrc"

    const val PREF_PERSISTENT_NOTIFICATIONS = "pref_persistent_notifications"
    const val PREF_KEY_CAMO_DIALOG = "pref_key_camo_dialog"

    private var cr: ContentResolver? = null

    @JvmStatic
    fun setContext(context: Context?) {
        if (cr == null) {
            cr = context?.contentResolver
        }
    }

    var bridgeCountry: String?
        get() = cr?.getPrefString(PREF_BRIDGE_COUNTRY)
        set(value) {
            cr?.let {
                it.putPref(PREF_BRIDGE_COUNTRY, value)
                if (Regionalization.isKindnessModeDisabledForCountry()) {
                    Settings.beSnowflakeProxy = false
                    Settings.snowflakeNeedsQualityCheck = true
                }
            }
        }

    @JvmStatic
    var defaultLocale: String
        get() = cr?.getPrefString(PREF_DEFAULT_LOCALE) ?: Locale.getDefault().language
        set(value) = cr?.putPref(PREF_DEFAULT_LOCALE, value) ?: Unit

    fun detectRoot(): Boolean {
        return cr?.getPrefBoolean(PREF_DETECT_ROOT, true) ?: true
    }

    fun showSnowflakeProxyToast(): Boolean {
        return cr?.getPrefBoolean(PREF_SHOW_SNOWFLAKE_MSG) ?: false
    }

    fun setBeSnowflakeProxyLimitWifi(beSnowflakeProxy: Boolean) {
        cr?.putPref(PREF_BE_A_SNOWFLAKE_LIMIT_WIFI, beSnowflakeProxy)
    }

    fun setBeSnowflakeProxyLimitCharging(beSnowflakeProxy: Boolean) {
        cr?.putPref(PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING, beSnowflakeProxy)
    }

    // https://github.com/guardianproject/orbot-android/issues/1798
    // this *should* be true, we want snowflake to be Wi-Fi only
    fun limitSnowflakeProxyingWifi(): Boolean {
        return cr?.getPrefBoolean(PREF_BE_A_SNOWFLAKE_LIMIT_WIFI, true) ?: true
    }

    fun limitSnowflakeProxyingCharging(): Boolean {
        return cr?.getPrefBoolean(PREF_BE_A_SNOWFLAKE_LIMIT_CHARGING) ?: false
    }

    @JvmStatic
    fun useDebugLogging(): Boolean {
        return cr?.getPrefBoolean(PREF_ENABLE_LOGGING) ?: false
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

    val isPowerUserMode: Boolean
        get() = cr?.getPrefBoolean(PREF_POWER_USER_MODE) ?: false

    var isSecureWindow: Boolean
        get() = cr?.getPrefBoolean(PREF_SECURE_WINDOW_FLAG) ?: false
        set(isFlagSecure) = cr?.putPref(PREF_SECURE_WINDOW_FLAG, isFlagSecure) ?: Unit


    /**
     * Returns true if a non-Orbot icon is in use (ie Birdie, Paint, etc)
     * When true, conceal information about Tor in notifications
     *
     * Returns false if icon is changed to an alt Orbot icon
     */
    @JvmStatic
    val isCamoEnabled: Boolean
        get() {
            val app =
                cr?.getPrefString(
                    PREF_CAMO_APP_PACKAGE,
                    Settings.DEFAULT_CAMO_DISABLED_ACTIVITY
                ) ?: ""
            if (Settings.camoAppAltIconIndex != -1) return false
            return app != Settings.DEFAULT_CAMO_DISABLED_ACTIVITY
        }

    val selectedCamoApp: String
        get() = cr?.getPrefString(
            PREF_CAMO_APP_PACKAGE,
            Settings.DEFAULT_CAMO_DISABLED_ACTIVITY
        ) ?: ""

    fun setCamoAppPackage(packageName: String?) {
        cr?.putPref(PREF_CAMO_APP_PACKAGE, packageName)
    }

    val requireDeviceAuthentication: Boolean
        get() = cr?.getPrefBoolean(PREF_REQUIRE_PASSWORD) ?: false

    val disallowBiometricAuthentication: Boolean
        get() = cr?.getPrefBoolean(PREF_DISALLOW_BIOMETRIC_AUTH) ?: false

    val proxySocksPort: String?
        get() = cr?.getPrefString(PREF_SOCKS)

    val proxyHttpPort: String?
        get() = cr?.getPrefString(PREF_HTTP)

    val connectionPadding: Boolean
        get() = cr?.getPrefBoolean(PREF_CONNECTION_PADDING) ?: false

    val reducedConnectionPadding: Boolean
        get() = cr?.getPrefBoolean(PREF_REDUCED_CONNECTION_PADDING, true) ?: true

    val circuitPadding: Boolean
        get() = cr?.getPrefBoolean(PREF_CIRCUIT_PADDING, true) ?: true

    val reducedCircuitPadding: Boolean
        get() = cr?.getPrefBoolean(PREF_REDUCED_CIRCUIT_PADDING, true) ?: true

    val torTransPort: String?
        get() = cr?.getPrefString(PREF_TRANSPORT)

    val torDnsPort: String?
        get() = cr?.getPrefString(PREF_DNSPORT)

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

    val customTorRc: String?
        get() = cr?.getPrefString(PREF_CUSTOM_TORRC)

    val isolateDest: Boolean
        get() = cr?.getPrefBoolean(PREF_ISOLATE_DEST) ?: false

    val isolatePort: Boolean
        get() = cr?.getPrefBoolean(PREF_ISOLATE_PORT) ?: false

    val isolateProtocol: Boolean
        get() = cr?.getPrefBoolean(PREF_ISOLATE_PROTOCOL) ?: false

    val isolateKeepAlive: Boolean
        get() = cr?.getPrefBoolean(PREF_ISOLATE_KEEP_ALIVE) ?: false

    val preferIpv6: Boolean
        get() = cr?.getPrefBoolean(PREF_PREFER_IPV6, true) ?: true

    val disableIpv4: Boolean
        get() = cr?.getPrefBoolean(PREF_DISABLE_IPV4) ?: false

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
