package org.torproject.android.service.vpn

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.annotation.DeprecatedSinceApi
import androidx.fragment.app.Fragment
import org.torproject.android.R
import org.torproject.android.util.putNotSystem

// some of this logic is adapted from Mullvad's amazing VpnServiceUtils.kt
// handles obscure cases around Android's opaque VpnService.prepare method
object VpnServicePrepareWrapper {

    interface Result {
        // Orbot VPN is setup to run
        data object Prepared : Result

        // Orbot VPN can't run, we are able to know there's another always-on VPN.
        data class CantPrepare(val errorMsg: String) : Result

        // Orbot might be able to run, but we have to try. Depending on the activity result it's
        // either good to go, or we have to display a generic error about always-on VPNs...
        data class ShouldAttempt(val prepareIntent: Intent) : Result
    }


    /**
     * Returns Prepared if it's worth it to attempt to start Orbot VPN.
     *
     * Returns a detailed error message if we are able to know that Orbot VPN
     * can't be started (because we can detect under some circumstances the existence
     * of an Always-on VPN)
     *
     * Otherwise returns the start Intent. When this Intent is started for
     * Activity Result, we can know if Orbot VPN is truly startable, or at the
     * least display a more generic error.
     *
     * Invoking VpnService.prepare() can result in 3 out comes:
     * 1. IllegalStateException - There is a legacy VPN profile marked as always on
     * 2. Intent
     *     - A: Can-prepare - Create Vpn profile or Always-on-VPN is not detected in case of Android 11+
     *     - B: Always-on-VPN - Another Vpn Profile is marked as always on (Only available up to Android
     *       11 or where testOnly is set, e.g builds from Android Studio)
     * 3. null - The app has the VPN permission
     *
     * In case 1 and 2b, you don't know if you have a VPN profile or not.
     *
     * In the case of a Legacy VPN is set to "Always-on, or
     * if on androids before API S we can give them a detailed message
     * about why we can't start the VPN. If The call to prepare
     * returns null, that means we are good to go. If it returns an Intent,
     * we'll have to attempt to start it for an activity result and possibly
     * display a more generic error about Always-on VPNs...
     */
    fun orbotVpnServicePreparedState(context: Context): Result {
        val startIntent: Intent? = try {
            VpnService.prepare(context)
        } catch (_: IllegalStateException) {
            // we only catch this specific exception type to handle the legacy always-on VPN
            return Result.CantPrepare(context.getString(R.string.unable_to_start_legacy_vpn_error_msg))
        }
        if (startIntent == null) // can start Orbot
            return Result.Prepared
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // On Androids below S we can see which app has always on VPN
            // if there's one, return a better error message
            getAlwaysOnVpnNameLegacy(context)?.let {
                return Result.CantPrepare(
                    context.getString(
                        R.string.unable_to_start_other_vpn_app_error_msg,
                        it
                    )
                )
            }
        }
        return Result.ShouldAttempt(startIntent.putNotSystem())
    }


    /** Returns the app name of an Always On VPN, if that
     * app isn't Orbot. unfortunately, this breaks in Android S
     * and higher. Returns null if Android S+, or if Orbot is the
     * Always-on VPN, or if there is no always-on VPN.
     */
    @DeprecatedSinceApi(Build.VERSION_CODES.S)
    private fun getAlwaysOnVpnNameLegacy(context: Context): String? {
        val currentAlwaysOnPackageName =
            try {
                Settings.Secure.getString(context.contentResolver, "always_on_vpn_app")
            } catch (_: SecurityException) {
                return null
            }
        return if (context.packageName == currentAlwaysOnPackageName) return null
        else {
            context.packageManager
                .getInstalledPackagesList(0)
                .firstOrNull { it.packageName == currentAlwaysOnPackageName }
                ?.applicationInfo
                ?.loadLabel(context.packageManager)
                ?.toString()
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun PackageManager.getInstalledPackagesList(flags: Int = 0): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") getInstalledPackages(flags)
        }

    fun openVpnSystemSettings(fragment: Fragment) {
        fragment.startActivity(
            Intent("android.net.vpn.SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}