/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package org.torproject.android.ui.more

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import org.torproject.android.OrbotApp
import org.torproject.android.R
import org.torproject.android.localization.Languages
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.tor.ShadowSocks
import org.torproject.android.util.NetworkUtils
import org.torproject.android.util.Prefs
import org.torproject.android.util.openSystemSettings
import org.torproject.android.util.removeEntry
import org.torproject.android.util.sendIntentToService

class SettingsPreferenceFragment : AbstractPreferenceFragment(), OnPreferenceChangeListener {
    private var toolbar: Toolbar? = null
    override fun prefId(): Int = R.xml.preferences
    override fun rootTitleId(): Int = R.string.menu_settings

    // If these EditTextPrefs exist, use a numerical keyboard
    val numericalPortPrefs = listOf(
        "pref_socks", "pref_http", "pref_proxy_port"
    )

    // render these EditTextPreferences, if they exist, as passwords
    val passwordPrefs = listOf(
        "pref_proxy_password"
    )

    private val requestLocalNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            findPreference<CheckBoxPreference>(Prefs.PREF_OPEN_PROXY_ON_ALL_INTERFACES)?.isChecked =
                granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            Prefs.resetOpenProxyOnAllInterfacesIfPermissionRevoked(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        (context as AppCompatActivity).setSupportActionBar(toolbar)
        toolbar?.setNavigationOnClickListener {
            // do something when click navigation
            onBackPressedCallback.handleOnBackPressed()
        }
        toolbar?.title = requireContext().getString(R.string.menu_settings)
    }

    override fun initPrefs() {
        super.initPrefs()

        val prefLocale = findPreference<ListPreference>("pref_default_locale")
        val languages = Languages[requireActivity()]
        prefLocale?.apply {
            entries = languages?.allNames
            entryValues = languages?.supportedLocales
            value = Prefs.defaultLocale
            onPreferenceChangeListener =
                OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                    val language = newValue as String
                    Prefs.defaultLocale = newValue
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val split = language.split("_")
                        val lang = split[0]
                        var region = ""
                        if (split.size > 1) region = split[1]
                        val newLocale = Languages.buildLocaleForLanguage(lang, region)
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(newLocale))
                        toolbar?.title = requireContext().getString(rootTitleId())
                    } else {
                        requireActivity().sendIntentToService(OrbotConstants.ACTION_LOCAL_LOCALE_SET)
                        (requireActivity().application as OrbotApp).setLocale()
                        requireActivity().finish()
                    }
                    false
                }
        }

        bindNumericalPrefs(numericalPortPrefs, 5)
        bindPasswordPrefs(passwordPrefs)
        bindInputType(
            listOf("pref_proxy_host"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        bindInputType(
            listOf("pref_custom_torrc"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )

        bindInputType(
            listOf("pref_proxy_ss"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // if defined in XML, disable the persistent notification preference on Oreo+
            findPreference<Preference>("pref_persistent_notifications")?.let {
                it.parent?.removePreference(it)
            }
        }

        findPreference<Preference>("pref_open_battery_opt")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                BatteryOptimizationsSettingDialog().show(
                    requireActivity().supportFragmentManager,
                    BatteryOptimizationsSettingDialog.TAG
                )
                true
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            setListenersForLocalNetworkPreferences()
        }


        val proxyType = findPreference<ListPreference>("pref_proxy_type")
        if (!ShadowSocks.isShadowSocksSupported()) {
            proxyType?.removeEntry(ShadowSocks.SCHEME)

            if (proxyType?.value == ShadowSocks.SCHEME) {
                proxyType.value = ""
            }
        }

        if (proxyType != null) {
            proxyType.onPreferenceChangeListener = this

            onPreferenceChange(proxyType, proxyType.value)
        }
    }


    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun setListenersForLocalNetworkPreferences() {
        findPreference<CheckBoxPreference>(Prefs.PREF_OPEN_PROXY_ON_ALL_INTERFACES)?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, newValue ->
                if (newValue == true && NetworkUtils.needsAccessLocalNetworkPermission(
                        requireContext()
                    ) == true
                ) {
                    requestLocalNetworkPermission()
                    // don't let the CheckBoxPreference commit yet, it gets set once the
                    // permission result comes back
                    false
                } else {
                    true
                }
            }

    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun requestLocalNetworkPermission() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pref_open_proxy_on_all_interfaces_title)
            .setMessage(R.string.open_proxy_needs_local_network_permission)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                val repeatedlyDenied = ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    Manifest.permission.ACCESS_LOCAL_NETWORK
                )
                if (repeatedlyDenied) {
                    openSystemSettings()
                } else {
                    requestLocalNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val common = listOf(
            "pref_proxy_host",
            "pref_proxy_port",
            "pref_proxy_username",
            "pref_proxy_password"
        ).mapNotNull {
            findPreference<EditTextPreference>(it)
        }

        val ssConfig = findPreference<EditTextPreference>("pref_proxy_ss")

        when (newValue) {
            "" -> {
                common.forEach { it.isVisible = false }
                ssConfig?.isVisible = false
            }

            ShadowSocks.SCHEME -> {
                common.forEach { it.isVisible = false }
                ssConfig?.isVisible = true
            }

            else -> {
                common.forEach { it.isVisible = true }
                ssConfig?.isVisible = false
            }
        }

        return true
    }
}
