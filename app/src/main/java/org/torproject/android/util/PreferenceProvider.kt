package org.torproject.android.util

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import org.torproject.android.BuildConfig

class PreferenceProvider : ContentProvider() {

    companion object {
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.preferences"
        const val TAG = "PreferenceProvider"

        val CONTENT_URI = "content://$AUTHORITY".toUri()

        const val ROW_TYPE = "type"
        const val ROW_VALUE = "value"
    }

    private val prefs: SharedPreferences?
        get() = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }

    override fun onCreate(): Boolean {
        val context = context

        if (context != null) {
            Settings.init(context)
        }

        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (update(uri, values, null, null) > 0) {
            return uri
        }

        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?
    ): Cursor? {
        val key = uri.lastPathSegment ?: return null

        val value = try {
            Settings.get(key)
        } catch (_: Throwable) {
            null
        }

        if (value != null) {
            return MatrixCursor(arrayOf(ROW_VALUE)).apply {
                addRow(arrayOf(value))
            }
        }

        if (!(prefs?.contains(key) ?: false)) return null

        return MatrixCursor(arrayOf(ROW_VALUE)).apply {
            addRow(arrayOf(prefs?.all[key]))
        }
    }

    @SuppressLint("UseKtx")
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int {
        if (values == null) return 0
        val key = uri.lastPathSegment ?: return 0

        try {
            // Currently only string type is supported here.
            Settings.set(key, values.getAsString(ROW_VALUE))

            context?.contentResolver?.notifyChange(uri, null)

            return 1
        } catch (_: Throwable) {
            // Not in `Settings` yet. Write to shared preferences instead.
        }

        val editor = prefs?.edit() ?: return 0

        when (values.getAsString(ROW_TYPE)) {
            String::class.toString() -> editor.putString(key, values.getAsString(ROW_VALUE))
            Boolean::class.toString() -> editor.putBoolean(key, values.getAsBoolean(ROW_VALUE))
            Int::class.toString() -> editor.putInt(key, values.getAsInteger(ROW_VALUE))
            Long::class.toString() -> editor.putLong(key, values.getAsLong(ROW_VALUE))
            Float::class.toString() -> editor.putFloat(key, values.getAsFloat(ROW_VALUE))
            else -> return 0
        }

        editor.apply()

        context?.contentResolver?.notifyChange(uri, null)

        return 1
    }

    @SuppressLint("UseKtx")
    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int {
        val key = uri.lastPathSegment ?: return 0

        try {
            Settings.set(key, null)

            context?.contentResolver?.notifyChange(uri, null)

            return 1
        } catch (_: Throwable) {
            // Not in `Settings` yet. Write to shared preferences instead.
        }

        if (!(prefs?.contains(key) ?: false)) return 0
        val editor = prefs?.edit() ?: return 0

        editor.remove(key).apply()

        context?.contentResolver?.notifyChange(uri, null)

        return 1
    }

    override fun getType(uri: Uri): String? {
        return null
    }
}

internal fun <T> preferenceProviderCall(defaultValue: T, action: () -> T): T =
    try {
        action()
    } catch (_: IllegalArgumentException) {
        defaultValue
    }

private fun <T> ContentResolver.getPref(key: String, converter: (Cursor, Int) -> T?): T? {
    return preferenceProviderCall(null) {
        val cursor = query(
            Uri.withAppendedPath(PreferenceProvider.CONTENT_URI, key),
            null, null, null, null
        )
        if (cursor == null) {
            return@preferenceProviderCall null
        }

        cursor.use {
            if (it.moveToFirst()) {
                return@preferenceProviderCall converter(
                    it, it.getColumnIndex(PreferenceProvider.ROW_VALUE)
                )
            }
        }

        null
    }

}

fun ContentResolver.getPrefString(key: String, default: String? = null): String? {
    return getPref(key) { c, i -> c.getStringOrNull(i) } ?: default
}

fun ContentResolver.getPrefBoolean(key: String, default: Boolean = false): Boolean {
    val v = getPref(key) { c, i -> c.getStringOrNull(i) } ?: return default

    return v == "true"
}

fun ContentResolver.getPrefInt(key: String, default: Int? = null): Int? {
    return getPref(key) { c, i -> c.getIntOrNull(i) } ?: default
}

fun ContentResolver.getPrefLong(key: String, default: Long? = null): Long? {
    return getPref(key) { c, i -> c.getLongOrNull(i) } ?: default
}

private fun ContentResolver.putPref(key: String, values: ContentValues) {
    val result: Unit? = preferenceProviderCall(null) {
        update(
            Uri.withAppendedPath(PreferenceProvider.CONTENT_URI, key),
            values, null, null
        )
        Unit
    }
    if (result == null) {
        Log.e(PreferenceProvider.TAG, "Couldn't update preference $key")
    }
}

fun ContentResolver.putPref(key: String, value: String?) {
    putPref(key, ContentValues().apply {
        put(PreferenceProvider.ROW_TYPE, String::class.toString())
        put(PreferenceProvider.ROW_VALUE, value)
    })
}

fun ContentResolver.putPref(key: String, value: Boolean) {
    putPref(key, ContentValues().apply {
        put(PreferenceProvider.ROW_TYPE, Boolean::class.toString())
        put(PreferenceProvider.ROW_VALUE, value)
    })
}

fun ContentResolver.putPref(key: String, value: Int) {
    putPref(key, ContentValues().apply {
        put(PreferenceProvider.ROW_TYPE, Int::class.toString())
        put(PreferenceProvider.ROW_VALUE, value)
    })
}

fun ContentResolver.putPref(key: String, value: Long) {
    putPref(key, ContentValues().apply {
        put(PreferenceProvider.ROW_TYPE, Long::class.toString())
        put(PreferenceProvider.ROW_VALUE, value)
    })
}
