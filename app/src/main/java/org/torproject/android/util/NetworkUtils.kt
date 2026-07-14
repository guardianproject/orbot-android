package org.torproject.android.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

object NetworkUtils {
    private const val TAG = "NetworkUtils"
    fun isNetworkAvailable(context: Context, allowOtherVpnApps: Boolean = false): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (allowOtherVpnApps && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    /** Used for kindness mode connection test, returns true *if and only if* Orbot is the registered
     * VPN app. We can't use Prefs.useVpn() since this only tells us if Orbot is the registered VPN
     * app when Tor is on. When it's off, we don't know which, if any VPN, is configured.
     *
     * - first cheaply check Prefs.useVpn(), this is true when orbot is running
     * - if not, check to see if the system sees a VPN connection, return false if not
     * - ensure for certain that Orbot isn't the registered VPN, this can be done by seeing if the
     *      system gives Orbot an Intent to register to be the active VPN app. If it's non-null, we
     *      know for certain we have a non-Orbot VPN config on the system
     */
    fun isNonOrbotVpnActive(context: Context, logTag: String = TAG): Boolean {
        if (Prefs.useVpn()) {
            return false
        }

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val deviceUsingVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        Log.d(logTag, "is there an active VPN connection? $deviceUsingVpn")

        // we either don't have a VPN app running, if it is, check for certain it's not Orbot
        if (!deviceUsingVpn) return false
        val isOrbotRegisteredAsVpn = VpnService.prepare(context) != null
        Log.d(logTag, "isOrbotRegisteredAsVpn: $isOrbotRegisteredAsVpn")
        return isOrbotRegisteredAsVpn
    }

    /**
     * Gets Private DNS setting configured by the user on Android 9+
     *
     * see:
     * https://github.com/guardianproject/orbot-android/pull/1707
     * https://android-developers.googleblog.com/2018/04/dns-over-tls-support-in-android-p.html
     */
    sealed class PrivateDns {

        // user is on an older Android, or disabled private DNS on P+
        data object Off : PrivateDns()

        // user lets system choose to use DoH with servers it chooses when they're available
        data object Opportunistic : PrivateDns()

        //user specified and is using a specific host to resolve DNS queries that the system enforces
        data class Strict(val hostname: String) : PrivateDns()

        companion object {
            private const val KEY_MODE = "private_dns_mode"
            private const val KEY_HOSTNAME = "private_dns_specifier"

            private const val MODE_OFF = "off"
            private const val MODE_HOSTNAME = "hostname"
            private const val MODE_AUTOMATIC = "automatic"
            private const val MODE_OPPORTUNISTIC = "opportunistic"

            const val HOSTNAME_UNKNOWN = ""

            // private DNS is only available on Android P+
            fun isPrivateDnsSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

            fun getPrivateDnsConfiguration(context: Context): PrivateDns {
                if (!isPrivateDnsSupported()) return Off
                val dnsMode =
                    Settings.Global.getString(context.contentResolver, KEY_MODE) ?: MODE_OFF
               return when (dnsMode) {
                    MODE_OFF -> Off
                    MODE_AUTOMATIC, MODE_OPPORTUNISTIC -> Opportunistic
                    MODE_HOSTNAME -> Strict(
                        hostname = Settings.Global.getString(context.contentResolver, KEY_HOSTNAME)
                            ?: HOSTNAME_UNKNOWN
                    )

                    else -> Off
                }
            }
        }
    }

    /**
     * Checks whether ACCESS_LOCAL_NETWORK still needs to be requested from the user. Any feature
     * that talks to devices on the LAN (kindness mode's UPnP probing, or the SOCKS/HTTP proxy
     * listening on all interfaces) needs this permission on Android 17+.
     *
     * Returns null on Android 16 and below, where the permission doesn't exist.
     */
    fun needsAccessLocalNetworkPermission(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
            return null
        }
        val checkAccessLocalNetworkPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        )
        if (checkAccessLocalNetworkPerm == PackageManager.PERMISSION_DENIED) {
            Log.d(TAG, "local network permission not granted")
            return true
        }
        Log.d(TAG, "local network permission granted")
        return false
    }
}
