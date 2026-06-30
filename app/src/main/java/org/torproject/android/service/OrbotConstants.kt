package org.torproject.android.service

import androidx.core.net.toUri

object OrbotConstants {
    const val TAG = "Orbot"

    const val APP_TOR_KEY = "_app_tor"

    const val DIRECTORY_TOR_DATA = "tordata"

    // geoip data file asset key
    const val GEOIP_ASSET_KEY = "geoip"
    const val GEOIP6_ASSET_KEY = "geoip6"

    // control port
    const val LOG_NOTICE_HEADER = "NOTICE: "
    const val LOG_NOTICE_BOOTSTRAPPED = "BOOTSTRAP PROGRESS="


    // needed when Orbot exits and tor is not running, but the notification is still active
    const val ACTION_STOP_FOREGROUND_TASK = "org.torproject.android.intent.action.STOP_FOREGROUND_TASK"

    const val ACTION_RESTART_VPN_IF_RUNNING = "org.torproject.android.intent.action.RESTART_VPN"

    const val ACTION_LOCAL_LOCALE_SET = "org.torproject.android.intent.LOCAL_LOCALE_SET"

    const val ACTION_UPDATE_ONION_NAMES = "org.torproject.android.intent.action.UPDATE_ONION_NAMES"


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

    const val LOCAL_ACTION_QUICK_SETTINGS_NEWNYM = "TILE_NEWNYM"



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
        "com.duckduckgo.mobile.android",
        "com.whatsapp",
        "com.instagram.android",
        "im.vector.app",
        "org.telegram.messenger",
        "com.twitter.android",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.brave.browser",
        "org.mozilla.focus",
        "app.vanadium.browser",
        "im.molly.app",
        "app.accrescent.client"
    )

    // Constants for getting bridges in semi-manual ways.

    val GET_BRIDES_BRIDGES_URI = "https://bridges.torproject.org/".toUri()

    const val GET_BRIDES_EMAIL_RECIPIENT = "bridges@torproject.org"

    const val GET_BRIDES_EMAIL_SUBJECT_AND_BODY = "get transport"

    val GET_BRIDES_TELEGRAM_BOT = "https://t.me/GetBridgesBot".toUri()

}
