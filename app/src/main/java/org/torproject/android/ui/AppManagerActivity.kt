package org.torproject.android.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.torproject.android.R
import org.torproject.android.core.ui.BaseActivity
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.vpn.TorifiedApp
import org.torproject.android.service.vpn.TorifiedAppWrapper

class AppManagerActivity : BaseActivity(), View.OnClickListener {
    private var searchQuery: String = ""
    private var searchJob: Job? = null
    private var pMgr: PackageManager? = null
    private var mPrefs: SharedPreferences? = null
    private var adapterAppsAll: RecyclerView.Adapter<*>? = null
    private var suggestedPackages: List<String>? = null
    private var allApps: List<TorifiedApp>? = null
    private var suggestedApps: List<TorifiedApp>? = null
    private var uiList: MutableList<TorifiedAppWrapper> = ArrayList()
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchBar: TextInputEditText

    // JSON serializer configuration
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pMgr = packageManager
        this.setContentView(R.layout.layout_apps)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        recyclerView = findViewById(R.id.applistview)
        progressBar = findViewById(R.id.progressBar)
        searchBar = findViewById(R.id.searchBar)

        // Need a better way to manage this list
        suggestedPackages = OrbotConstants.VPN_SUGGESTED_APPS

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(150) // debounce delay
                    searchQuery = s.toString()
                    filterApps(searchQuery)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val searchBarLayout = findViewById<TextInputLayout>(R.id.searchBarLayout)
        searchBarLayout.setEndIconOnClickListener {
            searchBar.text?.clear()
            searchBar.clearFocus()
            hideKeyboard()
            reloadApps()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        mPrefs = Prefs.getSharedPrefs(applicationContext)
        reloadApps()
    }

    override fun onClick(v: View) {
        val cbox = when (v) {
            is CheckBox -> v
            else -> v.tag as? CheckBox
        }

        cbox?.let {
            val app = it.tag as TorifiedApp
            app.isTorified = !app.isTorified
            it.isChecked = app.isTorified
        }
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

    private fun fetchAndSortApps() {
        allApps = TorifiedApp.getAppsFiltered(this, mPrefs!!, null, suggestedPackages)
        TorifiedApp.sortAppsForTorifiedAndAbc(allApps)
        suggestedApps = TorifiedApp.getAppsFiltered(this, mPrefs!!, suggestedPackages, null)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
    }

    private fun calculateAppListHash(apps: List<TorifiedApp>?): Int {
        return apps?.sumOf { it.packageName.hashCode() } ?: -1
    }

    private fun decodeApps(data: String?): MutableList<TorifiedApp>? {
        return data?.let { json.decodeFromString<List<TorifiedApp>>(it) }?.toMutableList()
    }

    private fun reloadApps() {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val cachedAppListHash = sharedPreferences.getString("cachedAppListHash", null)?.toIntOrNull()

        lifecycleScope.launch {
            val currentAppListHash = calculateAppListHash(
                TorifiedApp.getAppsFiltered(this@AppManagerActivity, mPrefs!!, null, suggestedPackages)
            )

            if (currentAppListHash != cachedAppListHash) {
                progressBar.visibility = View.VISIBLE
                loadAppsAsync(fromCache = false)
                sharedPreferences.edit {
                    putString("cachedAppListHash", currentAppListHash.toString())
                }
                progressBar.visibility = View.GONE
            } else {
                loadAppsAsync(fromCache = true)
            }
            recyclerView.adapter = adapterAppsAll

            // Reapply filter if there's a search query
            if (searchQuery.isNotEmpty()) {
                filterApps(searchQuery)
            }
        }
    }

    private suspend fun loadAppsAsync(fromCache: Boolean) {
        withContext(Dispatchers.Default) {
            if (fromCache) {
                val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val allAppsJson = sharedPreferences.getString("allApps", null)
                val suggestedAppsJson = sharedPreferences.getString("suggestedApps", null)

                try {
                    allApps = decodeApps(allAppsJson)
                    suggestedApps = decodeApps(suggestedAppsJson)
                } catch (e: Exception) {
                    e.printStackTrace()
                    fetchAndSortApps()
                    saveAppsToPrefs(allApps, suggestedApps)
                }
            } else {
                fetchAndSortApps()
                saveAppsToPrefs(allApps, suggestedApps)
            }

            // Refresh torified flags from prefs
            val tordApps = mPrefs
                ?.getString(OrbotConstants.PREFS_KEY_TORIFIED, "")
                ?.split("|")
                ?.sorted()
                ?: emptyList()
            val torifiedSet = tordApps.toSet()
            allApps?.forEach { it.isTorified = it.packageName in torifiedSet }
            suggestedApps?.forEach { it.isTorified = it.packageName in torifiedSet }

            populateUiList()
            val adapter = AppManagerAdapter(uiList.toMutableList(), pMgr!!, this@AppManagerActivity)

            withContext(Dispatchers.Main) {
                adapterAppsAll = adapter
                recyclerView.adapter = adapterAppsAll
            }
        }
    }

    private fun saveAppsToPrefs(allApps: List<TorifiedApp>?, suggestedApps: List<TorifiedApp>?) {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        sharedPreferences.edit {
            putString("allApps", allApps?.let { json.encodeToString(it) })
            putString("suggestedApps", suggestedApps?.let { json.encodeToString(it) })
        }
    }

    private fun populateUiList() {
        uiList.clear()

        if (!suggestedApps.isNullOrEmpty()) {
            uiList.add(TorifiedAppWrapper().apply {
                header = getString(R.string.apps_suggested_title)
            })
            uiList.add(TorifiedAppWrapper().apply {
                subheader = getString(R.string.app_suggested_subtitle)
            })

            val (torifiedSuggested, nonTorifiedSuggested) = suggestedApps!!.partition { it.isTorified }
            (torifiedSuggested + nonTorifiedSuggested).mapTo(uiList) {
                TorifiedAppWrapper().apply { app = it }
            }

            uiList.add(TorifiedAppWrapper().apply {
                header = getString(R.string.apps_other_apps)
            })
        }

        val (torifiedAll, nonTorifiedAll) = allApps.orEmpty().partition { it.isTorified }
        (torifiedAll + nonTorifiedAll).mapTo(uiList) {
            TorifiedAppWrapper().apply { app = it }
        }
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: ViewGroup = view.findViewById(R.id.appContainer)
        val icon: ImageView = view.findViewById(R.id.itemicon)
        val box: CheckBox = view.findViewById(R.id.itemcheck)
        val text: TextView = view.findViewById(R.id.itemtext)
        val header: TextView = view.findViewById(R.id.tvHeader)
        val subheader: TextView = view.findViewById(R.id.tvSubheader)
    }

    private fun filterApps(query: String) {
        val filteredList = uiList.filter {
            it.app?.name?.contains(query, ignoreCase = true) == true || query.isEmpty()
        }
        (adapterAppsAll as? AppManagerAdapter)?.setData(filteredList)
    }

    private fun saveAppSettings() {
        val tordApps = StringBuilder()
        val response = Intent()

        val saveTorifiedApps: (List<TorifiedApp>?) -> Unit = { apps ->
            apps?.filter { it.isTorified }?.forEach { tApp ->
                tordApps.append(tApp.packageName).append("|")
                response.putExtra(tApp.packageName, true)
            }
        }

        saveTorifiedApps(allApps)
        saveTorifiedApps(suggestedApps)

        mPrefs?.edit {
            putString(OrbotConstants.PREFS_KEY_TORIFIED, tordApps.toString().trimEnd('|'))
        }

        setResult(RESULT_OK, response)
    }
}
