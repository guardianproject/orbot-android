package org.torproject.android.service

import androidx.core.net.toUri
import org.torproject.jni.TorService

object OrbotConstants {
    const val TAG = "Orbot"

    const val PREF_REACHABLE_ADDRESSES = "pref_reachable_addresses"
    const val PREF_REACHABLE_ADDRESSES_PORTS = "pref_reachable_addresses_ports"

    const val PREF_DNSPORT = "pref_dnsport"
    const val PREF_HTTP = "pref_http"
    const val PREF_SOCKS = "pref_socks"
    const val PREF_TRANSPORT = "pref_transport"

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

    const val APP_TOR_KEY = "_app_tor"

    const val DIRECTORY_TOR_DATA = "tordata"

    // geoip data file asset key
    const val GEOIP_ASSET_KEY = "geoip"
    const val GEOIP6_ASSET_KEY = "geoip6"

    const val HTTP_PROXY_PORT_DEFAULT = "8118"
    const val SOCKS_PROXY_PORT_DEFAULT = "9050"

    const val TOR_DNS_PORT_DEFAULT = 9053
    const val TOR_TRANSPROXY_PORT_DEFAULT = 9040

    // control port
    const val LOG_NOTICE_HEADER = "NOTICE: "
    const val LOG_NOTICE_BOOTSTRAPPED = "Bootstrapped"

    /**
     * A request to Orbot to transparently start Tor services
     */
    const val ACTION_START = TorService.ACTION_START
    const val ACTION_STOP = "org.torproject.android.intent.action.STOP"

    // needed when Orbot exits and tor is not running, but the notification is still active
    const val ACTION_STOP_FOREGROUND_TASK = "org.torproject.android.intent.action.STOP_FOREGROUND_TASK"

    const val ACTION_RESTART_VPN = "org.torproject.android.intent.action.RESTART_VPN"

    const val ACTION_LOCAL_LOCALE_SET = "org.torproject.android.intent.LOCAL_LOCALE_SET"

    const val ACTION_UPDATE_ONION_NAMES = "org.torproject.android.intent.action.UPDATE_ONION_NAMES"

    /**
     * Intent send by Orbot with `ON/OFF/STARTING/STOPPING` status
     */
    const val ACTION_STATUS = TorService.ACTION_STATUS

    const val ACTION_ERROR = TorService.ACTION_ERROR

    /**
     * `String` that contains a status constant: [.STATUS_ON],
     * [.STATUS_OFF], [.STATUS_STARTING], or
     * [.STATUS_STOPPING]
     */
    const val EXTRA_STATUS = TorService.EXTRA_STATUS

    /**
     * A [String] `packageName` for Orbot to direct its status reply
     * to, used in [.ACTION_START] Intents sent to Orbot
     */
    const val EXTRA_PACKAGE_NAME = TorService.EXTRA_PACKAGE_NAME

    /**
     * The SOCKS proxy settings in URL form.
     */
    const val EXTRA_SOCKS_PROXY = "org.torproject.android.intent.extra.SOCKS_PROXY"
    const val EXTRA_SOCKS_PROXY_HOST = "org.torproject.android.intent.extra.SOCKS_PROXY_HOST"
    const val EXTRA_SOCKS_PROXY_PORT = "org.torproject.android.intent.extra.SOCKS_PROXY_PORT"

    /**
     * The HTTP proxy settings in URL form.
     */
    const val EXTRA_HTTP_PROXY = "org.torproject.android.intent.extra.HTTP_PROXY"
    const val EXTRA_HTTP_PROXY_HOST = "org.torproject.android.intent.extra.HTTP_PROXY_HOST"
    const val EXTRA_HTTP_PROXY_PORT = "org.torproject.android.intent.extra.HTTP_PROXY_PORT"

    const val EXTRA_DNS_PORT = "org.torproject.android.intent.extra.DNS_PORT"
    const val EXTRA_TRANS_PORT = "org.torproject.android.intent.extra.TRANS_PORT"

    /**
     * When present, indicates with certainty that the system itself did *not* send the Intent.
     * Effectively, the lack of this extra indicates that the VPN is being started by the system
     * as a result of the user's always-on preference for the VPN.
     * See: [Detect always-on | VPN | Android Developers](https://developer.android.com/guide/topics/connectivity/vpn#detect_always-on)
     */
    const val EXTRA_NOT_SYSTEM = "org.torproject.android.intent.extra.NOT_SYSTEM"

    const val LOCAL_ACTION_LOG = "log"
    const val LOCAL_ACTION_STATUS = "status"
    const val LOCAL_ACTION_BANDWIDTH = "bandwidth"
    const val LOCAL_EXTRA_TOTAL_READ = "totalRead"
    const val LOCAL_EXTRA_TOTAL_WRITTEN = "totalWritten"
    const val LOCAL_EXTRA_LAST_WRITTEN = "lastWritten"
    const val LOCAL_EXTRA_LAST_READ = "lastRead"
    const val LOCAL_EXTRA_LOG = "log"
    const val LOCAL_EXTRA_BOOTSTRAP_PERCENT = "percent"
    const val LOCAL_ACTION_PORTS = "ports"
    const val LOCAL_ACTION_V3_NAMES_UPDATED = "V3_NAMES_UPDATED"
    const val LOCAL_ACTION_NOTIFICATION_START = "notification_start"


    /**
     * All tor-related services and daemons are stopped
     */
    const val STATUS_OFF = TorService.STATUS_OFF

    /**
     * All tor-related services and daemons have completed starting
     */
    const val STATUS_ON = TorService.STATUS_ON
    const val STATUS_STARTING = TorService.STATUS_STARTING
    const val STATUS_STOPPING = TorService.STATUS_STOPPING

    /**
     * The user has disabled the ability for background starts triggered by
     * apps. Fallback to the old Intent action that brings up Orbot:
     * [.ACTION_START]
     */
    const val STATUS_STARTS_DISABLED = "STARTS_DISABLED"

    // actions for internal command Intents
    const val CMD_SET_EXIT = "setexit"
    const val CMD_ACTIVE = "ACTIVE"

    const val ONION_SERVICES_DIR = "v3_onion_services"
    const val V3_CLIENT_AUTH_DIR = "v3_client_auth"

    const val PREFS_DNS_PORT: String = "PREFS_DNS_PORT"

    const val PREFS_KEY_TORIFIED: String = "PrefTord"

    /**
     * Include packages here to make the VPNService ignore these apps. This is to
     * prevent tor over tor scenarios...
     */
    @JvmField
    val BYPASS_VPN_PACKAGES = mutableListOf(
        "org.torproject.torbrowser_alpha",
        "org.torproject.torbrowser",
        "org.onionshare.android",  // issue #618
        "org.onionshare.android.fdroid",
        "org.briarproject.briar.android",  // https://github.com/guardianproject/orbot/issues/474
        "im.cwtch.flwtch",
    )

    val VPN_SUGGESTED_APPS = mutableListOf(
        "org.thoughtcrime.securesms",  // Signal
        "com.whatsapp",
        "com.instagram.android",
        "im.vector.app",
        "org.telegram.messenger",
        "com.twitter.android",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.brave.browser",
        "org.mozilla.focus"
    )

    const val ONION_EMOJI: String = "\uD83E\uDDC5"


    // Constants for getting bridges in semi-manual ways.

    val GET_BRIDES_BRIDGES_URI = "https://bridges.torproject.org/".toUri()

    const val GET_BRIDES_EMAIL_RECIPIENT = "bridges@torproject.org"

    const val GET_BRIDES_EMAIL_SUBJECT_AND_BODY = "get transport"

    val GET_BRIDES_TELEGRAM_BOT = "https://t.me/GetBridgesBot".toUri()

}
