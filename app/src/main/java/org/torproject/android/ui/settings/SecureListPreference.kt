package org.torproject.android.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.torproject.android.util.Settings

class SecureListPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    override fun persistString(value: String?): Boolean {
        Settings.set(key, value ?: "")

        return true
    }

    override fun getValue(): String {
        return Settings.get(key)
    }
}