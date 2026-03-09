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
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import org.torproject.android.OrbotApp
import org.torproject.android.R
import org.torproject.android.localization.Languages
import org.torproject.android.service.OrbotConstants
import org.torproject.android.util.Prefs
import org.torproject.android.util.sendIntentToService

class SettingsPreferenceFragment : AbstractPreferenceFragment() {
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

        bindNumericaPrefs(numericalPortPrefs, 5)
        bindPasswordPrefs(passwordPrefs)
        bindInputType(
            listOf("pref_proxy_host"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        bindInputType(
            listOf("pref_custom_torrc"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // if defined in XML, disable the persistent notification preference on Oreo+
            findPreference<Preference>("pref_persistent_notifications")?.let {
                it.parent?.removePreference(it)
            }
        }
    }
}
