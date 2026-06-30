/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package org.torproject.android.ui.more

import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.os.LocaleListCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import org.torproject.android.OrbotApp
import org.torproject.android.R
import org.torproject.android.localization.Languages
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.tor.ShadowSocks
import org.torproject.android.util.Prefs
import org.torproject.android.util.removeEntry
import org.torproject.android.util.sendIntentToService

class SettingsPreferenceFragment : AbstractPreferenceFragment(), OnPreferenceChangeListener {
    private var toolbar: Toolbar? = null
    override fun prefId(): Int = R.xml.preferences
    override fun rootTitleId(): Int = R.string.menu_settings

    // If these EditTextPrefs exist, use a numerical keyboard
    val numericalPortPrefs = listOf(
        Prefs.PREF_SOCKS,
        Prefs.PREF_HTTP,
        Prefs.PREF_OUTBOUND_PROXY_PORT
    )

    // render these EditTextPreferences, if they exist, as passwords
    val passwordPrefs = listOf(Prefs.PREF_PROXY_PASSWORD)

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

        val prefLocale = findPreference<ListPreference>(Prefs.PREF_DEFAULT_LOCALE)
        val languages = Languages[requireActivity()]
        prefLocale?.entries = languages?.allNames
        prefLocale?.entryValues = languages?.supportedLocales
        prefLocale?.value = Prefs.defaultLocale
        prefLocale?.onPreferenceChangeListener =
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

        bindNumericalPrefs(numericalPortPrefs, 5)
        bindPasswordPrefs(passwordPrefs)
        bindInputType(
            listOf(Prefs.PREF_PROXY_HOST),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        bindInputType(
            listOf(Prefs.PREF_CUSTOM_TORRC),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )

        bindInputType(
            listOf(Prefs.PREF_PROXY_SHADOWSOCKS),
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

        val proxyType = findPreference<ListPreference>(Prefs.PREF_PROXY_TYPE)
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

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val common = listOf(
            Prefs.PREF_PROXY_HOST,
            Prefs.PREF_OUTBOUND_PROXY_PORT,
            Prefs.PREF_PROXY_USERNAME,
            Prefs.PREF_PROXY_PASSWORD
        ).mapNotNull {
            findPreference<EditTextPreference>(it)
        }

        val ssConfig = findPreference<EditTextPreference>(Prefs.PREF_PROXY_SHADOWSOCKS)

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
