package org.torproject.android.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
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
import org.torproject.android.databinding.FragmentAppManagerBinding
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.vpn.TorifiedApp
import org.torproject.android.service.vpn.TorifiedAppWrapper
import org.torproject.android.util.Prefs
import org.torproject.android.util.haveIBeenDetached
import org.torproject.android.util.normalize
import org.torproject.android.util.sendIntentToService
import java.util.Arrays
import java.util.StringTokenizer
import kotlin.time.Duration.Companion.milliseconds

class AppManagerFragment : Fragment() {

    private lateinit var adapterAppsAll: AppManagerAdapter
    private val suggestedPackages = OrbotConstants.VPN_SUGGESTED_APPS
    private val searchQuery = MutableStateFlow("")

    private val job = Job()
    private var retainedCheckedPackages: Set<String> = emptySet()

    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var binding: FragmentAppManagerBinding

    @kotlinx.coroutines.FlowPreview
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentAppManagerBinding.inflate(layoutInflater)
        retainedCheckedPackages =
            savedInstanceState?.getStringArray(BUNDLE_KEY_CHECKED_PACKAGES)?.toSet() ?: emptySet()
        val restoredQuery = savedInstanceState?.getString(BUNDLE_KEY_SEARCH_QUERY).orEmpty()
        appSelectionChanged = appSelectionChanged || savedInstanceState?.getBoolean(
            BUNDLE_KEY_APPS_CHANGED, false
        ) == true
        if (restoredQuery.isNotEmpty()) {
            binding.searchBar.setText(restoredQuery)
            searchQuery.value = restoredQuery
        }

        adapterAppsAll = AppManagerAdapter { wrapper ->
            val app = wrapper.app ?: return@AppManagerAdapter

            app.isTorified = !app.isTorified
            appSelectionChanged = true

            adapterAppsAll.currentList.indexOf(wrapper)
                .takeIf { it >= 0 }
                ?.let { adapterAppsAll.notifyItemChanged(it) }
        }

        binding.applistview.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapterAppsAll
        }

        searchQuery
            .debounce(250.milliseconds)
            .distinctUntilChanged()
            .onEach { filterApps(it) }
            .launchIn(scope)

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery.value = s?.toString().orEmpty()
                if (s?.isEmpty() == true) {
                    binding.searchBarLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
                    binding.searchBarLayout.endIconDrawable = ResourcesCompat.getDrawable(
                        resources, R.drawable.ic_search, null
                    )
                } else {
                    binding.searchBarLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                    binding.searchBarLayout.endIconDrawable = ResourcesCompat.getDrawable(
                        resources, R.drawable.ic_close, null
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        with(binding.toolbar) {
            (context as AppCompatActivity).setSupportActionBar(this)
            setNavigationOnClickListener {
                // do something when click navigation
                (context as AppCompatActivity).supportFragmentManager.popBackStack()
            }

            title =
                requireContext().getString(R.string.btn_choose_apps)
                    .split(" ")
                    .joinToString(separator = "") {
                        it.replaceFirstChar { c -> if (c.isLowerCase()) c.uppercase() else c.toString() }
                    }
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(BUNDLE_KEY_SEARCH_QUERY, searchQuery.value)
        outState.putBoolean(BUNDLE_KEY_APPS_CHANGED, appSelectionChanged)
        val checkedPackages = (allApps.orEmpty() + suggestedApps.orEmpty()).filter { it.isTorified }
            .map { it.packageName }.toTypedArray()
        outState.putStringArray(BUNDLE_KEY_CHECKED_PACKAGES, checkedPackages)
    }

    override fun onPause() {
        super.onPause()
        saveAppSettings()
    }

    private fun reloadApps() {

        scope.launch {
            binding.progressBar.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                val context = activity ?: return@withContext

                allApps = allApps ?: getApps(context, null, suggestedPackages, retainedCheckedPackages)
                suggestedApps = suggestedApps ?: getApps(context, suggestedPackages, null, retainedCheckedPackages)

                TorifiedApp.sortAppsForTorifiedAndAbc(allApps)

                // https://github.com/guardianproject/orbot-android/issues/1564
                if (haveIBeenDetached()) return@withContext

                allUnfilteredUiItems.clear()

                suggestedApps?.takeIf { it.isNotEmpty() }?.let { apps ->
                    allUnfilteredUiItems += TorifiedAppWrapper().apply { header = getString(R.string.apps_suggested_title) }
                    allUnfilteredUiItems += TorifiedAppWrapper().apply { subheader = getString(R.string.app_suggested_subtitle) }
                    allUnfilteredUiItems += apps.map { TorifiedAppWrapper(app = it) }
                    allUnfilteredUiItems += TorifiedAppWrapper().apply { header = getString(R.string.apps_other_apps) }
                }

                allUnfilteredUiItems += allApps?.map { TorifiedAppWrapper(app = it) }.orEmpty()
            }
            binding.progressBar.visibility = View.GONE

            filterApps(searchQuery.value)
        }
    }

    private suspend fun filterApps(query: String?) {
        val lower = query?.lowercase()?.trim().orEmpty()
        val results = withContext(Dispatchers.Default) {
            if (lower.isEmpty()) {
                allUnfilteredUiItems.toList()
            } else {
                allUnfilteredUiItems.filter {
                    it.app?.name?.lowercase()?.normalize()?.contains(lower) == true
                }
            }
        }

        adapterAppsAll.submitList(results)
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
    private var allUnfilteredUiItems: MutableList<TorifiedAppWrapper> = ArrayList()

    private var appSelectionChanged = false

    private fun saveAppSettings() {
        val allApps = allApps ?: return
        val suggestedApps = suggestedApps ?: return

        val tordApps = StringBuilder()

        for (tApp in allApps) {
            if (tApp.isTorified) {
                tordApps.append(tApp.packageName)
                tordApps.append("|")
            }
        }

        for (tApp in suggestedApps) {
            if (tApp.isTorified) {
                tordApps.append(tApp.packageName)
                tordApps.append("|")
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

    companion object {
        private const val BUNDLE_KEY_SEARCH_QUERY = "search_query"
        private const val BUNDLE_KEY_APPS_CHANGED = "apps_changed"
        private const val BUNDLE_KEY_CHECKED_PACKAGES = "checked_packages"

        /**
         * @return true if the app is "enabled", not Orbot, and not in
         * [.BYPASS_VPN_PACKAGES]
         */
        private fun includeAppInUi(applicationInfo: ApplicationInfo): Boolean {
            return applicationInfo.enabled && applicationInfo.packageName != BuildConfig.APPLICATION_ID && !OrbotConstants.BYPASS_VPN_PACKAGES.contains(
                applicationInfo.packageName
            )
        }

        @SuppressLint("QueryPermissionsNeeded")
        fun getApps(
            context: Context,
            filterInclude: List<String>?,
            filterRemove: List<String>?,
            retainedCheckedPackages: Set<String>
        ): ArrayList<TorifiedApp> {
            val pMgr = context.packageManager
            val tordAppString = Prefs.torifiedApps
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