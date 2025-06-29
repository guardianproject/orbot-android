package org.torproject.android.ui.components

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText

/**
   Tells the keyboard to be in an incognito mode on Android API 26+
   Keyboards supporting this feature are supposed to not add to their
   auto-correct dictionaries here, but there's no guarantee that a
   keyboard implements this flag or if it does that it does so properly
 */
class NoPersonalizedLearningEditText(context: Context, attrs: AttributeSet?) : AppCompatEditText(context, attrs) {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
    }
}