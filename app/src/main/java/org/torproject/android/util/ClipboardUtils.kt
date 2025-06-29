package org.torproject.android.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardUtils {
    @JvmStatic
    fun copyToClipboard(label: String, value: String, successMsg: String, context: Context): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
        return true
    }
}