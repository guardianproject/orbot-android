@file:Suppress("SameParameterValue")

package org.torproject.android.ui.more

import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import org.torproject.android.R


abstract class AbstractPreferenceFragment : PreferenceFragmentCompat() {
    private var toolbar: Toolbar? = null
    protected var isSubscreen: Boolean = false
    protected lateinit var onBackPressedCallback: OnBackPressedCallback

    open fun initPrefs() {
        setNoPersonalizedLearningOnEditTextPreferences()
    }


    protected fun bindNumericaPrefs(prefs: List<String> = emptyList(), maxLength: Int? = null) =
        bindInputType(prefs, InputType.TYPE_CLASS_NUMBER, maxLength)

    protected fun bindPasswordPrefs(prefs: List<String> = emptyList()) {
        prefs.forEach { pref ->
            val pref = findPreference<EditTextPreference?>(pref)
            pref?.let { pref ->
                pref.setOnBindEditTextListener {
                    it.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                // retain "Not set" text when there's no password supplied
                val oldSummaryProvider = pref.summaryProvider
                pref.summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                    if (pref.text.toString().isEmpty()) {
                        oldSummaryProvider?.provideSummary(pref)
                    } else pref.text.toString()
                        .map { _ -> "•" }.joinToString("")
                }
            }
        }
    }

    protected fun bindInputType(
        prefIds: List<String> = emptyList(),
        inputType: Int,
        maxLength: Int? = null
    ) {
        prefIds.forEach { pref ->
            val pref = findPreference<EditTextPreference?>(pref)
            pref?.setOnBindEditTextListener {
                it.inputType = inputType
                if (maxLength != null) {
                    it.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxLength))
                }
            }
        }
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(prefId(), null)
        isSubscreen = rootKey != null
        initPrefs()
    }

    override fun onResume() {
        super.onResume()
        if (!onBackPressedCallback.isEnabled)
            onBackPressedCallback.isEnabled = true
    }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        setPreferencesFromResource(prefId(), preferenceScreen.key)
        initPrefs()
        isSubscreen = true
        toolbar?.title = preferenceScreen.title
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        (context as AppCompatActivity).setSupportActionBar(toolbar)
        toolbar?.setNavigationOnClickListener {
            // do something when click navigation
            onBackPressedCallback.handleOnBackPressed()
        }
        toolbar?.title = requireContext().getString(rootTitleId())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSubscreen) {
                    toolbar?.title = requireContext().getString(R.string.menu_settings)
                    setPreferencesFromResource(prefId(), null)
                    isSubscreen = false
                } else {
                    remove()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }


    abstract fun prefId(): Int
    abstract fun rootTitleId(): Int
}