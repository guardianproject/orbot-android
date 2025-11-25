package org.torproject.android.ui.more

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.EditTextPreference
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