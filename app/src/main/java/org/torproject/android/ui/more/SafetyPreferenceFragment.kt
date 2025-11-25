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
        val prefFlagSecure = findPreference<CheckBoxPreference>("pref_flag_secure")

        val passwordCheckbox = findPreference<CheckBoxPreference>("pref_require_password")
        val biometricCheckbox = findPreference<CheckBoxPreference>("pref_auth_no_biometrics")

        prefFlagSecure?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _: Preference?, newValue: Any? ->

                Prefs.isSecureWindow = newValue as Boolean
                (activity as BaseActivity).resetSecureFlags()

                true
            }

        // on Android < R you can't turn off biometric auth
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            biometricCheckbox?.isVisible = false
        } else {
            biometricCheckbox?.isEnabled = passwordCheckbox?.isChecked == true
            passwordCheckbox?.onPreferenceChangeListener =
                OnPreferenceChangeListener { _, newValue ->
                    biometricCheckbox?.isEnabled = newValue as Boolean
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