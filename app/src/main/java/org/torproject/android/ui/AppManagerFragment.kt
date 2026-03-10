package org.torproject.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.GridView
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.torproject.android.BuildConfig
import org.torproject.android.R
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.vpn.TorifiedApp
import org.torproject.android.service.vpn.TorifiedAppWrapper
import org.torproject.android.util.Prefs
import org.torproject.android.util.haveIBeenDetached
import org.torproject.android.util.normalizie
import org.torproject.android.util.sendIntentToService
import java.util.Arrays

class AppManagerFragment : Fragment(), View.OnClickListener {

    private var pMgr: PackageManager? = null
    private var listAppsAll: GridView? = null
    private var adapterAppsAll: ListAdapter? = null
    private var progressBar: ProgressBar? = null
    private var alSuggested: List<String>? = null
    private var searchBar: TextView? = null
    private var searchBarLayout: TextInputLayout? = null
    private var filteredList: MutableList<TorifiedAppWrapper> = ArrayList()
    private val searchQuery = MutableStateFlow("")

    private val job = Job()
    private var retainedCheckedPackages: Set<String> = emptySet()

    private var toolbar: Toolbar? = null

    private val scope = CoroutineScope(Dispatchers.Main + job)

    @kotlinx.coroutines.FlowPreview
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_app_manager, container, false)
        listAppsAll = view.findViewById(R.id.applistview)
        progressBar = view.findViewById(R.id.progressBar)
        searchBar = view.findViewById(R.id.searchBar)
        searchBarLayout = view.findViewById(R.id.searchBarLayout)
        retainedCheckedPackages =
            savedInstanceState?.getStringArray("checked_packages")?.toSet() ?: emptySet()
        val restoredQuery = savedInstanceState?.getString("search_query").orEmpty()
        appSelectionChanged =
            appSelectionChanged || savedInstanceState?.getBoolean("apps_changed", false) == true
        if (restoredQuery.isNotEmpty()) {
            searchBar?.text = restoredQuery
            searchQuery.value = restoredQuery
        }

        searchQuery
            .debounce(250)
            .distinctUntilChanged()
            .onEach { filterApps(it) }
            .launchIn(scope)

        searchBar?.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            searchQuery.value = query

            if (query.isEmpty()) {
                searchBarLayout?.endIconMode = TextInputLayout.END_ICON_CUSTOM
                searchBarLayout?.endIconDrawable =
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_search, null)
            } else {
                searchBarLayout?.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                searchBarLayout?.endIconDrawable =
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_close, null)
            }
        }

        alSuggested = OrbotConstants.VPN_SUGGESTED_APPS

        toolbar = view.findViewById(R.id.toolbar)
        (context as AppCompatActivity).setSupportActionBar(toolbar)
        toolbar?.setNavigationOnClickListener {
            // do something when click navigation

            (context as AppCompatActivity).supportFragmentManager.popBackStack()

        }
        toolbar?.title = requireContext().getString(R.string.title_choose_apps)


        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pMgr = requireActivity().packageManager
    }

    override fun onResume() {
        super.onResume()

        reloadApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("search_query", searchQuery.value)
        outState.putBoolean("apps_changed", appSelectionChanged)
        val checkedPackages = (allApps.orEmpty() + suggestedApps.orEmpty())
            .filter { it.isTorified }
            .map { it.packageName }
            .toTypedArray()
        outState.putStringArray("checked_packages", checkedPackages)
    }

    override fun onPause() {
        super.onPause()
        saveAppSettings()
    }

    private fun reloadApps() {

        scope.launch {
            progressBar?.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                loadApps()
            }
            listAppsAll?.adapter = adapterAppsAll
            progressBar?.visibility = View.GONE

            filterApps(searchQuery.value)
        }
    }

    private fun filterApps(query: String?) {
        scope.launch(Dispatchers.Default) {
            val lower = query?.lowercase()?.trim().orEmpty()
            val results = if (lower.isEmpty()) {
                allUnfilteredUiItems
            } else {
                allUnfilteredUiItems.filter {
                    it.app?.name?.lowercase()?.normalizie()?.contains(lower) == true
                }
            }

            withContext(Dispatchers.Main) {
                filteredList.clear()
                filteredList.addAll(results)
                (adapterAppsAll as? ArrayAdapter<*>)?.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        activity?.let {
            if (appSelectionChanged && !it.isChangingConfigurations) {
                requireActivity().sendIntentToService(OrbotConstants.ACTION_RESTART_VPN_IF_RUNNING)
                Toast.makeText(it, R.string.apps_updated_msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private var allApps: List<TorifiedApp>? = null
    private var suggestedApps: List<TorifiedApp>? = null

    // contains apps, but also other things like TextViews for suggested apps
    var allUnfilteredUiItems: MutableList<TorifiedAppWrapper> = ArrayList()

    private fun loadApps() {
        activity?.let {
            if (allApps == null) allApps =
                getApps(it, null, alSuggested, retainedCheckedPackages)
            TorifiedApp.sortAppsForTorifiedAndAbc(allApps)
            if (suggestedApps == null) suggestedApps =
                getApps(it, alSuggested, null, retainedCheckedPackages)

            // https://github.com/guardianproject/orbot-android/issues/1564
            if (haveIBeenDetached()) return

            val inflater = layoutInflater
            if (allUnfilteredUiItems.isEmpty()) {
                // only show suggested apps, text, etc and other apps header if there are any suggested apps installed...
                if (!suggestedApps.isNullOrEmpty()) {
                    val headerSuggested = TorifiedAppWrapper()
                    headerSuggested.header = getString(R.string.apps_suggested_title)
                    allUnfilteredUiItems.add(headerSuggested)
                    val subheaderSuggested = TorifiedAppWrapper()
                    subheaderSuggested.subheader = getString(R.string.app_suggested_subtitle)
                    allUnfilteredUiItems.add(subheaderSuggested)

                    allUnfilteredUiItems.addAll(suggestedApps?.map { item -> TorifiedAppWrapper(app = item) }
                        ?: emptyList())

                    val headerAllApps = TorifiedAppWrapper()
                    headerAllApps.header = getString(R.string.apps_other_apps)
                    allUnfilteredUiItems.add(headerAllApps)
                }

                allUnfilteredUiItems.addAll(allApps?.map { item -> TorifiedAppWrapper(app = item) }
                    ?: emptyList())
            }

            adapterAppsAll = object : ArrayAdapter<TorifiedAppWrapper?>(
                it,
                R.layout.layout_apps_item,
                R.id.itemtext,
                filteredList
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    var cv = convertView
                    var entry: ListEntry? = null

                    if (cv == null) {
                        cv = inflater.inflate(R.layout.layout_apps_item, parent, false)
                    } else {
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

                    val taw = filteredList[position]

                    if (taw.header != null) {
                        entry.header?.text = taw.header
                        entry.header?.visibility = View.VISIBLE
                        entry.subheader?.visibility = View.GONE
                        entry.container?.visibility = View.GONE
                    } else if (taw.subheader != null) {
                        entry.subheader?.visibility = View.VISIBLE
                        entry.subheader?.text = taw.subheader
                        entry.container?.visibility = View.GONE
                        entry.header?.visibility = View.GONE
                    } else {
                        val app = taw.app
                        entry.header?.visibility = View.GONE
                        entry.subheader?.visibility = View.GONE
                        entry.container?.visibility = View.VISIBLE

                        val packageName = app?.packageName
                        if (entry.icon != null && packageName != null) {
                            try {
                                entry.icon?.setImageDrawable(pMgr?.getApplicationIcon(packageName))
                                entry.icon?.tag = entry.box
                                entry.icon?.setOnClickListener(this@AppManagerFragment)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        entry.text?.text = app?.name
                        entry.text?.tag = entry.box
                        entry.text?.setOnClickListener(this@AppManagerFragment)

                        entry.box?.isChecked = app?.isTorified ?: false
                        entry.box?.tag = app
                        entry.box?.setOnClickListener(this@AppManagerFragment)
                    }

                    cv?.setOnFocusChangeListener { v, hasFocus ->
                        val color = if (hasFocus) R.color.dark_purple else android.R.color.transparent
                        v.setBackgroundColor(ContextCompat.getColor(requireContext(), color))
                    }

                    return cv ?: View(context)
                }
            }

            filteredList.clear()
            filteredList.addAll(allUnfilteredUiItems)
        }
    }

    private var appSelectionChanged = false

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
        val appStringOld = Prefs.torifiedApps
        val appStringNew = tordApps.toString()

        var shouldSave = false
        if (appStringOld.contains('|') && appStringNew.contains('|')) {
            val a = appStringOld.split('|')
            val b = appStringNew.split('|')
            shouldSave = if (a.size == b.size) {
                HashSet(a) != HashSet(b)
            } else true
        } else if (appStringNew != appStringOld) {
            shouldSave = true
        }
        if (!shouldSave) return

        Prefs.torifiedApps = tordApps.toString()
        appSelectionChanged = true
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
            return applicationInfo.enabled &&
                    applicationInfo.packageName != BuildConfig.APPLICATION_ID &&
                    applicationInfo.packageName !in OrbotConstants.BYPASS_VPN_PACKAGES
        }

        fun getApps(
            context: Context,
            filterInclude: List<String>?,
            filterRemove: List<String>?,
            retainedCheckedPackages: Set<String>
        ): ArrayList<TorifiedApp> {
            val tordApps = Prefs.torifiedApps
                .split("|")
                .filter { it.isNotBlank() }
                .sorted()
                .toTypedArray()
            val pMgr = context.packageManager
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
                    val pInfo =
                        pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS)

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
                } catch (_: Exception) {
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

                // Preserve rotation-checked state
                app.isTorified =
                    app.isTorified || retainedCheckedPackages.contains(app.packageName) == true
            }
            apps.sort()
            val checked = apps.filter { it.isTorified }
            val unchecked = apps.filter { !it.isTorified }
            val list = ArrayList<TorifiedApp>(checked)
            list.addAll(unchecked)
            return list
        }
    }
}