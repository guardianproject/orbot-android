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
import java.util.ArrayList
import java.util.Arrays

@Serializable
class TorifiedApp : Comparable<Any> {
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

    // Drawable is not serializable, so we mark it as @Transient
    @Transient
    var icon: Drawable? = null

    @Serializable
    var packageName: String = ""

    @Serializable
    var isTorified: Boolean = false

    @Serializable
    private var usesInternet: Boolean = false

    fun usesInternet(): Boolean {
        return usesInternet
    }

    fun setUsesInternet(usesInternet: Boolean) {
        this.usesInternet = usesInternet
    }

    override fun compareTo(other: Any): Int {
        return this.toString().compareTo(other.toString(), ignoreCase = true)
    }

    override fun toString(): String {
        return name ?: ""
    }

    companion object {
        fun getApps(context: Context, prefs: SharedPreferences): ArrayList<TorifiedApp> {
            val tordAppString = prefs.getString(OrbotConstants.PREFS_KEY_TORIFIED, "") ?: ""
            val tordApps: Array<String>

            val st = tordAppString.split("|").filter { it.isNotEmpty() }
            tordApps = st.toTypedArray()

            Arrays.sort(tordApps)

            // Load the apps
            val pMgr = context.packageManager
            val lAppInfo = pMgr.getInstalledApplications(0)
            val apps = ArrayList<TorifiedApp>()

            for (aInfo in lAppInfo) {
                val app = TorifiedApp()
                try {
                    val pInfo = pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS)
                    if (OrbotConstants.BYPASS_VPN_PACKAGES.contains(aInfo.packageName)) {
                        app.setUsesInternet(false)
                    } else if (pInfo?.requestedPermissions != null) {
                        for (permInfo in pInfo.requestedPermissions!!) {
                            if (permInfo == Manifest.permission.INTERNET) {
                                app.setUsesInternet(true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if ((aInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 1) {
                    // System app
                    app.setUsesInternet(true)
                }

                if (!app.usesInternet()) continue
                else {
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
                app.isTorified = Arrays.binarySearch(tordApps, app.packageName) >= 0
            }

            apps.sort()
            return apps
        }

        fun sortAppsForTorifiedAndAbc(apps: List<TorifiedApp>?) {
            apps?.let {
                it.sortedWith { o1, o2 ->
                    /* Some apps start with lowercase letters and without the sorting being case
                       insensitive they'd appear at the end of the grid of apps, a position where users
                       would likely not expect to find them.
                     */
                    if (o1.isTorified == o2.isTorified) {
                        Normalizer.normalize(o1.name ?: "", Normalizer.Form.NFD)
                            .compareTo(Normalizer.normalize(o2.name ?: "", Normalizer.Form.NFD), ignoreCase = true)
                    } else {
                        if (o1.isTorified) -1 else 1
                    }
                }
            }
        }
    }
}
