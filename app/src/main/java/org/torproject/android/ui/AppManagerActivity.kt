/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package org.torproject.android.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var pMgr: PackageManager? = null
    private var mPrefs: SharedPreferences? = null
    private var adapterAppsAll: RecyclerView.Adapter<*>? = null
    private var suggestedPackages: List<String>? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchBar: TextInputEditText

    private var allApps: List<TorifiedApp>? = null
    private var suggestedApps: List<TorifiedApp>? = null
    private var uiList: MutableList<TorifiedAppWrapper> = ArrayList()

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

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
                filterApps(s.toString())
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

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
    }

    private fun calculateAppListHash(apps: List<TorifiedApp>?): Int {
        return apps?.sumOf { it.packageName.hashCode() } ?: -1
    }

    private fun reloadApps() {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val cachedAppListHash = sharedPreferences.getString("cachedAppListHash", null)?.toIntOrNull()

        scope.launch {
            val currentAppListHash = calculateAppListHash(TorifiedApp.getApps(this@AppManagerActivity, mPrefs!!))

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
        }
    }

    private suspend fun loadAppsAsync(fromCache: Boolean) {
        withContext(Dispatchers.Default) {
            if (fromCache) {
                val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val allAppsJson = sharedPreferences.getString("allApps", null)
                val suggestedAppsJson = sharedPreferences.getString("suggestedApps", null)

                try {
                    allApps = allAppsJson?.let { json.decodeFromString<List<TorifiedApp>>(it) }?.toMutableList()
                    suggestedApps = suggestedAppsJson?.let { json.decodeFromString<List<TorifiedApp>>(it) }?.toMutableList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    allApps = TorifiedApp.getApps(this@AppManagerActivity, mPrefs!!)
                    TorifiedApp.sortAppsForTorifiedAndAbc(allApps)
                    suggestedApps = allApps?.filter { it.packageName in suggestedPackages.orEmpty() }
                    saveAppsToPrefs(allApps, suggestedApps)
                }
            } else {
                allApps = TorifiedApp.getApps(this@AppManagerActivity, mPrefs!!)
                TorifiedApp.sortAppsForTorifiedAndAbc(allApps)
                suggestedApps = allApps?.filter { it.packageName in suggestedPackages.orEmpty() }
                saveAppsToPrefs(allApps, suggestedApps)
            }

            // Refresh torified flags from prefs
            val tordApps = mPrefs?.getString(OrbotConstants.PREFS_KEY_TORIFIED, "")?.split("|")?.sorted() ?: emptyList()
            allApps?.forEach { it.isTorified = tordApps.contains(it.packageName) }
            suggestedApps?.forEach { it.isTorified = tordApps.contains(it.packageName) }

            populateUiList()
            adapterAppsAll = createAdapter(uiList)
        }
    }

    private fun saveAppsToPrefs(allApps: List<TorifiedApp>?, suggestedApps: List<TorifiedApp>?) {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putString("allApps", allApps?.let { json.encodeToString(it) })
            putString("suggestedApps", suggestedApps?.let { json.encodeToString(it) })
        }
    }

    private fun populateUiList() {
        uiList.clear()
        if (!suggestedApps.isNullOrEmpty()) {
            val headerSuggested = TorifiedAppWrapper()
            headerSuggested.header = getString(R.string.apps_suggested_title)
            uiList.add(headerSuggested)

            val subheaderSuggested = TorifiedAppWrapper()
            subheaderSuggested.subheader = getString(R.string.app_suggested_subtitle)
            uiList.add(subheaderSuggested)

            suggestedApps?.mapTo(uiList) { TorifiedAppWrapper().apply { app = it } }

            val headerAllApps = TorifiedAppWrapper()
            headerAllApps.header = getString(R.string.apps_other_apps)
            uiList.add(headerAllApps)
        }
        allApps?.mapTo(uiList) { TorifiedAppWrapper().apply { app = it } }
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: ViewGroup = view.findViewById(R.id.appContainer)
        val icon: ImageView = view.findViewById(R.id.itemicon)
        val box: CheckBox = view.findViewById(R.id.itemcheck)
        val text: TextView = view.findViewById(R.id.itemtext)
        val header: TextView = view.findViewById(R.id.tvHeader)
        val subheader: TextView = view.findViewById(R.id.tvSubheader)
    }

    class AppAdapter(
        private val list: List<TorifiedAppWrapper>,
        private val pMgr: PackageManager,
        private val onClickListener: View.OnClickListener,
    ) : RecyclerView.Adapter<AppViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_apps_item, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val taw = list[position]
            if (taw.header != null) {
                holder.header.text = taw.header
                holder.header.visibility = View.VISIBLE
                holder.subheader.visibility = View.GONE
                holder.container.visibility = View.GONE
            } else if (taw.subheader != null) {
                holder.subheader.visibility = View.VISIBLE
                holder.subheader.text = taw.subheader
                holder.container.visibility = View.GONE
                holder.header.visibility = View.GONE
            } else {
                val app = taw.app
                holder.header.visibility = View.GONE
                holder.subheader.visibility = View.GONE
                holder.container.visibility = View.VISIBLE
                try {
                    holder.icon.setImageDrawable(pMgr.getApplicationIcon(app!!.packageName))
                    holder.icon.tag = holder.box
                    holder.icon.setOnClickListener(onClickListener)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                holder.text.text = app!!.name
                holder.text.tag = holder.box
                holder.text.setOnClickListener(onClickListener)
                holder.box.isChecked = app.isTorified
                holder.box.tag = app
                holder.box.setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount(): Int = list.size
    }

    private fun createAdapter(list: List<TorifiedAppWrapper>): RecyclerView.Adapter<AppViewHolder> {
        return AppAdapter(list, pMgr!!, this)
    }

    private fun filterApps(query: String) {
        val filteredList = uiList.filter {
            it.app?.name?.contains(query, ignoreCase = true) == true
        }
        adapterAppsAll = createAdapter(filteredList)
        recyclerView.adapter = adapterAppsAll
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

        mPrefs?.edit()?.apply {
            putString(OrbotConstants.PREFS_KEY_TORIFIED, tordApps.toString().trimEnd('|'))
            apply()
        }

        setResult(RESULT_OK, response)
    }
}
