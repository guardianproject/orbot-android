package org.torproject.android.service.vpn

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

import org.torproject.android.service.OrbotConstants

import java.text.Normalizer

@Serializable
class TorifiedApp : Comparable<TorifiedApp> {
    @Serializable
    var isEnabled: Boolean = false

    @Serializable
    var uid: Int = 0

    @Serializable
    var username: String? = null

    @Serializable
    var procname: String? = null

    @Serializable
    var name: String? = null

    // Drawable is not serializable
    @Transient
    var icon: Drawable? = null

    @Serializable
    var packageName: String = ""

    @Serializable
    var isTorified: Boolean = false

    @Serializable
    var usesInternet: Boolean = false

    override fun compareTo(other: TorifiedApp): Int {
        return (name ?: "").compareTo(other.name ?: "", ignoreCase = true)
    }

    override fun toString(): String = name ?: ""

    companion object {
        fun getApps(context: Context, prefs: SharedPreferences): ArrayList<TorifiedApp> {
            val torifiedPackages = prefs
                .getString(OrbotConstants.PREFS_KEY_TORIFIED, "")
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?.sorted()
                ?: emptyList()

            val pMgr = context.packageManager
            val lAppInfo = pMgr.getInstalledApplications(0)
            val apps = ArrayList<TorifiedApp>()

            for (aInfo in lAppInfo) {
                val app = TorifiedApp()
                try {
                    val pInfo = pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS)
                    if (OrbotConstants.BYPASS_VPN_PACKAGES.contains(aInfo.packageName)) {
                        app.usesInternet = false
                    } else if (pInfo?.requestedPermissions != null) {
                        for (permInfo in pInfo.requestedPermissions!!) {
                            if (permInfo == Manifest.permission.INTERNET) {
                                app.usesInternet = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if ((aInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 1) {
                    // System app
                    app.usesInternet = true
                }

                if (!app.usesInternet) {
                    continue
                } else {
                    apps.add(app)
                }

                app.isEnabled = aInfo.enabled
                app.uid = aInfo.uid
                app.username = pMgr.getNameForUid(app.uid)
                app.procname = aInfo.processName
                app.packageName = aInfo.packageName

                try {
                    app.name = pMgr.getApplicationLabel(aInfo).toString()
                } catch (e: Exception) {
                    app.name = aInfo.packageName
                }

                // Check if this application is allowed
                app.isTorified = torifiedPackages.binarySearch(app.packageName) >= 0
            }

            apps.sort()
            return apps
        }

        fun sortAppsForTorifiedAndAbc(apps: List<TorifiedApp>?) {
            apps?.sortedWith(compareBy<TorifiedApp> { !it.isTorified }.thenBy {
                Normalizer.normalize(it.name ?: "", Normalizer.Form.NFD)
            })
        }
    }
}
