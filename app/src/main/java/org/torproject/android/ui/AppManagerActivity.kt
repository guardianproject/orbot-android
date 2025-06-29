/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package org.torproject.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.GridView
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.ProgressBar
import android.widget.TextView

import androidx.core.content.ContextCompat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.torproject.android.BuildConfig
import org.torproject.android.R
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.vpn.TorifiedApp

import java.util.Arrays
import java.util.StringTokenizer
import androidx.core.content.edit
import org.torproject.android.ui.components.BaseActivity

class AppManagerActivity : BaseActivity(), View.OnClickListener {
    inner class TorifiedAppWrapper(
        var header: String? = null,
        var subheader: String? = null,
        var app: TorifiedApp? = null)

    private var pMgr: PackageManager? = null
    private var mPrefs: SharedPreferences? = null
    private var listAppsAll: GridView? = null
    private var adapterAppsAll: ListAdapter? = null
    private var progressBar: ProgressBar? = null
    private var alSuggested: List<String>? = null

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pMgr = packageManager
        this.setContentView(R.layout.activity_app_manager)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        listAppsAll = findViewById(R.id.applistview)
        progressBar = findViewById(R.id.progressBar)

        // Need a better way to manage this list
        alSuggested = OrbotConstants.VPN_SUGGESTED_APPS

        // TODO https://github.com/guardianproject/orbot-android/issues/1344
        lockActivityOrientation()

    }

    override fun onResume() {
        super.onResume()
        mPrefs = Prefs.getSharedPrefs(applicationContext)
        reloadApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.app_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save_apps) {
            saveAppSettings()
            finish()
        } else if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun reloadApps() {
        scope.launch {
            progressBar?.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                loadApps()
            }
            listAppsAll?.adapter = adapterAppsAll
            progressBar?.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private var allApps: List<TorifiedApp>? = null
    private var suggestedApps: List<TorifiedApp>? = null
    var uiList: MutableList<TorifiedAppWrapper> = ArrayList()

    private fun loadApps() {
        if (allApps == null) allApps = getApps(this@AppManagerActivity, mPrefs, null, alSuggested)
        TorifiedApp.sortAppsForTorifiedAndAbc(allApps)
        if (suggestedApps == null) suggestedApps =
            getApps(this@AppManagerActivity, mPrefs, alSuggested, null)
        val inflater = layoutInflater

        // only show suggested apps, text, etc and other apps header if there are any suggested apps installed...
        if (!suggestedApps.isNullOrEmpty()) {
            val headerSuggested = TorifiedAppWrapper()
            headerSuggested.header = getString(R.string.apps_suggested_title)
            uiList.add(headerSuggested)
            val subheaderSuggested = TorifiedAppWrapper()
            subheaderSuggested.subheader = getString(R.string.app_suggested_subtitle)
            uiList.add(subheaderSuggested)

            uiList.addAll(suggestedApps?.map { TorifiedAppWrapper(app = it) } ?: emptyList())

            val headerAllApps = TorifiedAppWrapper()
            headerAllApps.header = getString(R.string.apps_other_apps)
            uiList.add(headerAllApps)
        }

        uiList.addAll(allApps?.map { TorifiedAppWrapper(app = it) } ?: emptyList())

        adapterAppsAll = object : ArrayAdapter<TorifiedAppWrapper?>(
            this,
            R.layout.layout_apps_item,
            R.id.itemtext,
            uiList as List<TorifiedAppWrapper?>
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var cv = convertView
                var entry: ListEntry? = null

                if (cv == null) {
                    cv = inflater.inflate(R.layout.layout_apps_item, parent, false)
                }
                else {
                    entry = cv.tag as ListEntry
                }

                if (entry == null) {
                    // Inflate a new view
                    entry = ListEntry()
                    entry.container = cv?.findViewById(R.id.appContainer)
                    entry.icon = cv?.findViewById(R.id.itemicon)
                    entry.box = cv?.findViewById(R.id.itemcheck)
                    entry.text = cv?.findViewById(R.id.itemtext)
                    entry.header = cv?.findViewById(R.id.tvHeader)
                    entry.subheader = cv?.findViewById(R.id.tvSubheader)
                    cv?.tag = entry
                }

                val taw = uiList[position]

                if (taw.header != null) {
                    entry.header?.text = taw.header
                    entry.header?.visibility = View.VISIBLE
                    entry.subheader?.visibility = View.GONE
                    entry.container?.visibility = View.GONE
                }
                else if (taw.subheader != null) {
                    entry.subheader?.visibility = View.VISIBLE
                    entry.subheader?.text = taw.subheader
                    entry.container?.visibility = View.GONE
                    entry.header?.visibility = View.GONE
                }
                else {
                    val app = taw.app
                    entry.header?.visibility = View.GONE
                    entry.subheader?.visibility = View.GONE
                    entry.container?.visibility = View.VISIBLE

                    val packageName = app?.packageName
                    if (entry.icon != null && packageName != null) {
                        try {
                            entry.icon?.setImageDrawable(pMgr?.getApplicationIcon(packageName))
                            entry.icon?.tag = entry.box
                            entry.icon?.setOnClickListener(this@AppManagerActivity)
                        }
                        catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    entry.text?.text = app?.name
                    entry.text?.tag = entry.box
                    entry.text?.setOnClickListener(this@AppManagerActivity)

                    entry.box?.isChecked = app?.isTorified ?: false
                    entry.box?.tag = app
                    entry.box?.setOnClickListener(this@AppManagerActivity)
                }

                cv?.onFocusChangeListener =
                    OnFocusChangeListener { v: View, hasFocus: Boolean ->
                        if (hasFocus) v.setBackgroundColor(
                            ContextCompat.getColor(
                                context, R.color.dark_purple
                            )
                        ) else {
                            v.setBackgroundColor(
                                ContextCompat.getColor(
                                    context,
                                    android.R.color.transparent
                                )
                            )
                        }
                    }

                return cv ?: View(context)
            }
        }
    }

    private fun saveAppSettings() {
        val allApps = allApps ?: return
        val suggestedApps = suggestedApps ?: return

        val tordApps = StringBuilder()
        val response = Intent()

        for (tApp in allApps) {
            if (tApp.isTorified) {
                tordApps.append(tApp.packageName)
                tordApps.append("|")
                response.putExtra(tApp.packageName, true)
            }
        }

        for (tApp in suggestedApps) {
            if (tApp.isTorified) {
                tordApps.append(tApp.packageName)
                tordApps.append("|")
                response.putExtra(tApp.packageName, true)
            }
        }

        mPrefs?.edit {
            putString(OrbotConstants.PREFS_KEY_TORIFIED, tordApps.toString())
        }

        setResult(RESULT_OK, response)
    }

    override fun onClick(v: View) {
        // todo make whatever is clicked (box, icon, text) set selected=true on the TextView
        // to make the text scroll, for now only selecting the text itself makes it scroll
        var cbox: CheckBox? = null
        if (v is CheckBox) cbox = v
        else if (v.tag is CheckBox) cbox = v.tag as CheckBox
        else if (v.tag is ListEntry) cbox = (v.tag as ListEntry).box
        if (cbox != null) {
            val app = cbox.tag as TorifiedApp
            app.isTorified = !app.isTorified
            cbox.isChecked = app.isTorified
        }
    }

    private class ListEntry {
        var box: CheckBox? = null
        var text: TextView? = null // app name
        var icon: ImageView? = null
        var container: View? = null
        var header: TextView? = null
        var subheader: TextView? = null
    }

    companion object {
        /**
         * @return true if the app is "enabled", not Orbot, and not in
         * [.BYPASS_VPN_PACKAGES]
         */
        private fun includeAppInUi(applicationInfo: ApplicationInfo): Boolean {
            if (!applicationInfo.enabled) return false
            return if (OrbotConstants.BYPASS_VPN_PACKAGES.contains(applicationInfo.packageName)) false else BuildConfig.APPLICATION_ID != applicationInfo.packageName
        }

        fun getApps(
            context: Context,
            prefs: SharedPreferences?,
            filterInclude: List<String>?,
            filterRemove: List<String>?
        ): ArrayList<TorifiedApp> {
            val pMgr = context.packageManager
            val tordAppString = prefs?.getString(OrbotConstants.PREFS_KEY_TORIFIED, "")
            val tordApps: Array<String?>
            val st = StringTokenizer(tordAppString, "|")
            tordApps = arrayOfNulls(st.countTokens())
            var tordIdx = 0
            while (st.hasMoreTokens()) {
                tordApps[tordIdx++] = st.nextToken()
            }
            Arrays.sort(tordApps)
            val lAppInfo = pMgr.getInstalledApplications(0)
            val itAppInfo: Iterator<ApplicationInfo> = lAppInfo.iterator()
            val apps = ArrayList<TorifiedApp>()
            while (itAppInfo.hasNext()) {
                val aInfo = itAppInfo.next()
                if (!includeAppInUi(aInfo)) continue
                if (filterInclude != null) {
                    var wasFound = false
                    for (filterId in filterInclude) if (filterId == aInfo.packageName) {
                        wasFound = true
                        break
                    }
                    if (!wasFound) continue
                }
                if (filterRemove != null) {
                    var wasFound = false
                    for (filterId in filterRemove) if (filterId == aInfo.packageName) {
                        wasFound = true
                        break
                    }
                    if (wasFound) continue
                }
                val app = TorifiedApp()
                try {
                    val pInfo = pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS)

                    for (permInfo in pInfo.requestedPermissions ?: emptyArray()) {
                        if (permInfo == Manifest.permission.INTERNET) {
                            app.usesInternet = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    app.name = pMgr.getApplicationLabel(aInfo).toString()
                } catch (e: Exception) {
                    // No name, we only show apps with names
                    continue
                }

                if (!app.usesInternet) continue else {
                    apps.add(app)
                }

                app.isEnabled = aInfo.enabled
                app.uid = aInfo.uid
                app.username = pMgr.getNameForUid(app.uid)
                app.procname = aInfo.processName
                app.packageName = aInfo.packageName

                // Check if this application is allowed
                app.isTorified = Arrays.binarySearch(tordApps, app.packageName) >= 0
            }
            apps.sort()

            return apps
        }
    }
}
