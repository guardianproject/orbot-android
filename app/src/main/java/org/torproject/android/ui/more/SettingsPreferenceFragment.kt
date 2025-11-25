/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package org.torproject.android.ui.more

import android.os.Build
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

    override fun initPrefs() {
        val prefLocale = findPreference<ListPreference>("pref_default_locale")
        val languages = Languages[requireActivity()]
        prefLocale?.entries = languages?.allNames
        prefLocale?.entryValues = languages?.supportedLocales
        prefLocale?.value = Prefs.defaultLocale
        prefLocale?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                val language = newValue as String?
                Prefs.defaultLocale = newValue ?: ""
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val newLocale = LocaleListCompat.forLanguageTags(language)
                    AppCompatDelegate.setApplicationLocales(newLocale)
                    toolbar?.title = requireContext().getString(rootTitleId())
                } else {
                    requireActivity().sendIntentToService(OrbotConstants.ACTION_LOCAL_LOCALE_SET)
                    (requireActivity().application as OrbotApp).setLocale()
                    requireActivity().finish()
                }
                false
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // if defined in XML, disable the persistent notification preference on Oreo+
            findPreference<Preference>("pref_persistent_notifications")?.let {
                it.parent?.removePreference(it)
            }
        }
    }
}
