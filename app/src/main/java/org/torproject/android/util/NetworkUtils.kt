package org.torproject.android.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.util.Log

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
}