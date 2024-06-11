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

    override fun compareTo(other: TorifiedApp): Int =
         (name ?: "").compareTo(other.name ?: "", ignoreCase = true)

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
            lAppInfo.forEach {
                val app = TorifiedApp()
                try {
                    val pInfo = pMgr.getPackageInfo(it.packageName, PackageManager.GET_PERMISSIONS)
                    if (OrbotConstants.BYPASS_VPN_PACKAGES.contains(it.packageName)) {
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

                if ((it.flags and ApplicationInfo.FLAG_SYSTEM) == 1)
                    app.usesInternet = true // System app

                if (!app.usesInternet) return@forEach
                else apps.add(app)

                app.apply {
                    isEnabled = it.enabled
                    uid = it.uid
                    username = pMgr.getNameForUid(it.uid)
                    procname = it.processName
                    packageName = it.packageName
                }

                try {
                    app.name = pMgr.getApplicationLabel(it).toString()
                } catch (e: Exception) {
                    app.name = it.packageName
                }

                // Check if this application is allowed
                app.isTorified = torifiedPackages.binarySearch(app.packageName) >= 0
            }

            apps.sort()
            return apps
        }

        fun getAppsFiltered(
            context: Context,
            prefs: SharedPreferences,
            filterInclude: List<String>? = null,
            filterRemove: List<String>? = null
        ): ArrayList<TorifiedApp> {
            val pMgr = context.packageManager
            val tordApps = prefs.getString(OrbotConstants.PREFS_KEY_TORIFIED, "")
                ?.split("|")
                ?.sorted()
                ?: emptyList()

            val allPackages = pMgr.getInstalledApplications(0)
            val result = ArrayList<TorifiedApp>()

            for (aInfo in allPackages) {
                if (!aInfo.enabled) continue
                if (OrbotConstants.BYPASS_VPN_PACKAGES.contains(aInfo.packageName)) continue
                if ("org.torproject.android" in aInfo.packageName) continue
                if (filterInclude != null && aInfo.packageName !in filterInclude) continue
                if (filterRemove != null && aInfo.packageName in filterRemove) continue

                val app = TorifiedApp()

                try {
                    val pInfo = pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS)
                    app.usesInternet = pInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (!app.usesInternet) continue

                try {
                    app.name = pMgr.getApplicationLabel(aInfo).toString()
                } catch (_: Exception) {
                    continue // Skip apps without a name
                }

                app.apply {
                    isEnabled = aInfo.enabled
                    uid = aInfo.uid
                    username = pMgr.getNameForUid(app.uid)
                    procname = aInfo.processName
                    packageName = aInfo.packageName
                    isTorified = tordApps.binarySearch(app.packageName) >= 0
                }

                result.add(app)
            }

            result.sort()
            return result
        }

        fun sortAppsForTorifiedAndAbc(apps: List<TorifiedApp>?) {
            apps?.sortedWith(compareBy<TorifiedApp> { !it.isTorified }.thenBy {
                Normalizer.normalize(it.name ?: "", Normalizer.Form.NFD)
            })
        }
    }
}
