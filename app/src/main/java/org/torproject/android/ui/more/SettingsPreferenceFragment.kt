/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package org.torproject.android.ui.more

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.commit
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import org.torproject.android.R
import org.torproject.android.localization.Languages
import org.torproject.android.ui.core.BaseActivity
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.more.camo.CamoFragment

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private var prefLocale: ListPreference? = null

    private fun initPrefs() {
        setNoPersonalizedLearningOnEditTextPreferences()

        prefLocale = findPreference("pref_default_locale")
        val languages = Languages[requireActivity()]
        prefLocale?.entries = languages?.allNames
        prefLocale?.entryValues = languages?.supportedLocales
        prefLocale?.value = Prefs.defaultLocale
        prefLocale?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                val language = newValue as String?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Prefs.defaultLocale = newValue ?: ""
                    val newLocale = LocaleListCompat.forLanguageTags(language)
                    AppCompatDelegate.setApplicationLocales(newLocale)
                } else {
                    val intentResult = Intent()
                    intentResult.putExtra("locale", language)
                    requireActivity().setResult(RESULT_OK, intentResult)
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

        val prefFlagSecure = findPreference<CheckBoxPreference>("pref_flag_secure")
        prefFlagSecure?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _: Preference?, newValue: Any? ->

                Prefs.isSecureWindow = newValue as Boolean
                (activity as BaseActivity).resetSecureFlags()

                true
            }

        val prefCamoDialog = findPreference<Preference>("pref_key_camo_dialog")
        prefCamoDialog?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            activity?.supportFragmentManager?.commit {
                addToBackStack(SettingsActivity.FRAGMENT_TAG)
                replace(R.id.settings_container, CamoFragment())
            }
            true
        }

        val prefOrbotAuthentication = findPreference<CheckBoxPreference>("pref_require_password")
        val prefPasswordNoBiometrics = findPreference<CheckBoxPreference>("pref_auth_no_biometrics")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            prefPasswordNoBiometrics?.isVisible = false
        } else {
            prefPasswordNoBiometrics?.isEnabled = prefOrbotAuthentication?.isChecked == true
            prefOrbotAuthentication?.onPreferenceChangeListener =
                OnPreferenceChangeListener { preference, newValue ->
                    prefPasswordNoBiometrics?.isEnabled = newValue as Boolean
                    true
                }
        }
    }

    override fun onResume() {
        super.onResume()

        val screenTitle = preferenceScreen?.title
        if (!screenTitle.isNullOrEmpty()) {
            activity?.title = screenTitle
        } else {
            activity?.setTitle(R.string.menu_settings)
        }
    }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        val fragment = SettingsPreferenceFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PREFERENCE_ROOT, preferenceScreen.key)
            }
        }

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        initPrefs()
    }

    private fun setNoPersonalizedLearningOnEditTextPreferences() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return
        val preferenceScreen = preferenceScreen
        val categoryCount = preferenceScreen.preferenceCount
        for (i in 0 until categoryCount) {
            var p = preferenceScreen.getPreference(i)
            if (p is PreferenceCategory) {
                val pc = p
                val preferenceCount = pc.preferenceCount
                for (j in 0 until preferenceCount) {
                    p = pc.getPreference(j)
                    if (p is EditTextPreference) {
                        p.setOnBindEditTextListener {
                            it.imeOptions =
                                it.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }
                    }
                }
            }
        }
    }
}
