package org.torproject.android.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.tink.AeadSerializer
import androidx.preference.PreferenceManager
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Implement an encrypted data store. (With a non-encrypted fallback, if encryption is unavailable,
 * e.g. because of a missing Secure Element.)
 *
 * **ATTENTION**: Only [init] this once, preferably in [PreferenceProvider]!
 *
 * Reference:
 * - [Datastore Architecture](https://developer.android.com/topic/libraries/architecture/datastore)
 * - [Encrypted Datastore](https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0-alpha07)
 */
object Settings {

    private const val KEYSET_NAME = "settings_keyset"
    private const val PREFERENCE_FILE = "settings_keyset_preference"
    private const val MASTER_KEY_URI = "android-keystore://_androidx_security_master_key_"
    private const val SETTINGS_FILE_NAME = "settings"
    private const val CURRENT_MIGRATION_STEP = 1

    private lateinit var dataStore: DataStore<SettingsStore>

    /**
     * Initialize Settings.
     *
     * Depending on the value of [CURRENT_MIGRATION_STEP], there will be a migration from old-school
     * shared preferences. See [migrate] for details.
     *
     * @param context - Application context. Reference will not be stored.
     */
    fun init(context: Context) {
        val settingsFile = context.preferencesDataStoreFile(SETTINGS_FILE_NAME)

        try {
            AeadConfig.register()

            val aead = AndroidKeysetManager.Builder()
                .withSharedPref(context.applicationContext, KEYSET_NAME, PREFERENCE_FILE)
                .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES128_GCM))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)

            val serializer = AeadSerializer(
                aead,
                SettingsStoreSerializer,
                SETTINGS_FILE_NAME.encodeToByteArray()
            )

            dataStore = DataStoreFactory.create(
                serializer,
                ReplaceFileCorruptionHandler { SettingsStoreSerializer.defaultValue },
                produceFile = { settingsFile })

        } catch (t: Throwable) {
            Log.e(Settings::class.simpleName, "Couldn't use AheadSerializer, failing back to unencrypted Settings: $t")

            dataStore = DataStoreFactory.create(
                SettingsStoreSerializer,
                ReplaceFileCorruptionHandler { SettingsStoreSerializer.defaultValue },
                produceFile = { settingsFile })
        }

        runBlocking {
            if (dataStore.data.first().migrated < CURRENT_MIGRATION_STEP) migrate(context)
        }
    }

    /**
     * Sets the value of a string preference with the given key.
     *
     * **ATTENTION**: This shall only ever be used in [PreferenceProvider],
     * [org.torproject.android.ui.settings.SecureEditTextPreference] and
     * [org.torproject.android.ui.settings.SecureListPreference] and similar classes to ensure
     * preferences are written to the *encrypted* data store.
     *
     * **Never** call this from anywhere else. Use the accessors in the [Prefs] class instead,
     * that go through the [PreferenceProvider] which is IPC-safe!
     *
     * @param key - The preference key.
     * @param value - The string value of that preference.
     * @throws RuntimeException - if the key is unknown.
     */
    fun set(key: String, value: String?) {
        runBlocking {
            dataStore.updateData {
                when (key) {
                    Prefs.PREF_PROXY_TYPE -> it.copy(proxyType = value)
                    Prefs.PREF_PROXY_HOST -> it.copy(proxyHost = value)
                    Prefs.PREF_PROXY_PORT -> it.copy(proxyPort = value)
                    Prefs.PREF_PROXY_USERNAME -> it.copy(proxyUsername = value)
                    Prefs.PREF_PROXY_PASSWORD -> it.copy(proxyPassword = value)
                    Prefs.PREF_SHADOW_SOCKS_PROXY -> it.copy(proxySs = value)
                    else -> throw RuntimeException("Couldn't set preference unknown key $key")
                }
            }
        }
    }

    /**
     * Gets the value of a string preference with the given key.
     *
     * **ATTENTION**: This shall only ever be used in [PreferenceProvider],
     * [org.torproject.android.ui.settings.SecureEditTextPreference] and
     * [org.torproject.android.ui.settings.SecureListPreference] and similar classes to ensure
     * preferences are read from the *encrypted* data store.
     *
     * **Never** call this from anywhere else. Use the accessors in the [Prefs] class instead,
     * that go through the [PreferenceProvider] which is IPC-safe!
     *
     * @param key - The preference key.
     * @return The string value of that preference.
     */
    fun get(key: String): String? {
        return runBlocking {
            when (key) {
                Prefs.PREF_PROXY_TYPE -> dataStore.data.first().proxyType
                Prefs.PREF_PROXY_HOST -> dataStore.data.first().proxyHost
                Prefs.PREF_PROXY_PORT -> dataStore.data.first().proxyPort
                Prefs.PREF_PROXY_USERNAME -> dataStore.data.first().proxyUsername
                Prefs.PREF_PROXY_PASSWORD -> dataStore.data.first().proxyPassword
                Prefs.PREF_SHADOW_SOCKS_PROXY -> dataStore.data.first().proxySs
                else -> throw RuntimeException("Couldn't get preference, unknown key $key")
            }
        }
    }

    /**
     * Migrate data from old-school shared preferences.
     *
     * Tries to read all old preferences which are already migrated from [Prefs] to encrypted [Settings].
     *
     * When done, removes all old preferences from the shared preferences XML file.
     *
     * When you move more items from shared preferences to encrypted [Settings], increase [CURRENT_MIGRATION_STEP]
     * by one, so another migration will be done on next launch.
     *
     * @param context - Application context. Reference will not be stored. Needed to fetch shared preferences.
     */
    private suspend fun migrate(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        dataStore.updateData { store ->
            store.copy(
                migrated = CURRENT_MIGRATION_STEP,
                proxyType = prefs.getString(Prefs.PREF_PROXY_TYPE, null) ?: store.proxyType,
                proxyHost = prefs.getString(Prefs.PREF_PROXY_HOST, null) ?: store.proxyHost,
                proxyPort = prefs.getString(Prefs.PREF_PROXY_PORT, null) ?: store.proxyPort,
                proxyUsername = prefs.getString(Prefs.PREF_PROXY_USERNAME, null) ?: store.proxyUsername,
                proxyPassword = prefs.getString(Prefs.PREF_PROXY_PASSWORD, null) ?: store.proxyPassword,
                proxySs = prefs.getString(Prefs.PREF_SHADOW_SOCKS_PROXY, null) ?: store.proxySs,
            )
        }

        prefs.edit {
            remove(Prefs.PREF_PROXY_TYPE)
            remove(Prefs.PREF_PROXY_HOST)
            remove(Prefs.PREF_PROXY_PORT)
            remove(Prefs.PREF_PROXY_USERNAME)
            remove(Prefs.PREF_PROXY_PASSWORD)
            remove(Prefs.PREF_SHADOW_SOCKS_PROXY)
        }
    }

    @Serializable
    private data class SettingsStore(
        val migrated: Int = 0,
        val proxyType: String? = null,
        val proxyHost: String? = null,
        val proxyPort: String? = null,
        val proxyUsername: String? = null,
        val proxyPassword: String? = null,
        val proxySs: String? = null,
    )

    private object SettingsStoreSerializer : Serializer<SettingsStore> {

        override val defaultValue = SettingsStore()

        private val json = Json { ignoreUnknownKeys = true }

        override suspend fun readFrom(input: InputStream): SettingsStore =
            withContext(Dispatchers.IO) {
                try {
                    json.decodeFromString<SettingsStore>(input.readBytes().decodeToString())
                } catch (t: Throwable) {
                    throw CorruptionException("Unable to read SettingsStore", t)
                }
            }

        override suspend fun writeTo(t: SettingsStore, output: OutputStream) =
            withContext(Dispatchers.IO) {
                output.write(json.encodeToString(t).encodeToByteArray())
            }
    }
}
