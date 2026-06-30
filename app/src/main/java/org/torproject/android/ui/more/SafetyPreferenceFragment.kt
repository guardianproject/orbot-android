package org.torproject.android.ui.more

import android.os.Build
import androidx.navigation.fragment.findNavController
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import org.torproject.android.R
import org.torproject.android.ui.core.BaseActivity
import org.torproject.android.util.Prefs

class SafetyPreferenceFragment : AbstractPreferenceFragment() {
    override fun prefId(): Int = R.xml.safety_prefs
    override fun rootTitleId(): Int = R.string.title_safety

    override fun initPrefs() {
        super.initPrefs()
        val prefFlagSecure = findPreference<CheckBoxPreference>(Prefs.PREF_SECURE_WINDOW_FLAG)

        val passwordCheckbox = findPreference<CheckBoxPreference>(Prefs.PREF_REQUIRE_PASSWORD)
        val biometricCheckbox = findPreference<CheckBoxPreference>(Prefs.PREF_DISALLOW_BIOMETRIC_AUTH)

        prefFlagSecure?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _: Preference?, newValue: Any? ->

                Prefs.isSecureWindow = newValue as Boolean
                (activity as BaseActivity).resetSecureFlags()

                true
            }

        // on Androids lower than API "R" you can't turn off biometric auth so hide it
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            biometricCheckbox?.isVisible = false
        } else {
            biometricCheckbox?.isVisible = passwordCheckbox?.isChecked == true
            passwordCheckbox?.onPreferenceChangeListener =
                OnPreferenceChangeListener { _, newValue ->
                    biometricCheckbox?.isVisible = newValue as Boolean
                    true
                }

        }
        val prefCamoDialog = findPreference<Preference>("pref_key_camo_dialog")
        prefCamoDialog?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            onBackPressedCallback.isEnabled = false
            findNavController().navigate(R.id.open_camo)
            true
        }

    }
}