package org.torproject.android.util

import android.content.Context
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
import org.torproject.android.service.circumvention.Transport
import java.io.InputStream
import java.io.OutputStream

/**
 * https://developer.android.com/topic/libraries/architecture/datastore
 * https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0-alpha07
 */
object Settings {

    private const val KEYSET_NAME = "settings_keyset"
    private const val PREFERENCE_FILE = "settings_keyset_preference"
    private const val MASTER_KEY_URI = "android-keystore://_androidx_security_master_key_"
    private const val SETTINGS_FILE_NAME = "settings"
    private const val CURRENT_MIGRATION_STEP = 1

    private lateinit var dataStore: DataStore<SettingsStore>

    suspend fun init(context: Context) {
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
                SETTINGS_FILE_NAME.encodeToByteArray())

            dataStore = DataStoreFactory.create(
                serializer,
                ReplaceFileCorruptionHandler({ SettingsStoreSerializer.defaultValue }),
                produceFile = { settingsFile })

        } catch(_: Throwable) {
            // Ignored. If we really need to, we fall back to unencrypted.

            dataStore = DataStoreFactory.create(
                SettingsStoreSerializer,
                ReplaceFileCorruptionHandler({ SettingsStoreSerializer.defaultValue }),
                produceFile = { settingsFile })
        }

        if (dataStore.data.first().migrated < CURRENT_MIGRATION_STEP) migrate(context)
    }

    suspend fun set(
        smartConnect: Boolean? = null,
        smartConnectTimeout: Int? = null,
        transport: Transport? = null,
        bridgesList: List<String>? = null
    ) {
        dataStore.updateData { it.copy(
            smartConnect = smartConnect ?: it.smartConnect,
            smartConnectTimeout = smartConnectTimeout ?: it.smartConnectTimeout,
            transport = transport?.id ?: it.transport,
            bridgesList = bridgesList ?: it.bridgesList,
        ) }
    }

    var smartConnect
        get() = runBlocking { dataStore.data.first().smartConnect }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(smartConnect = value) }
        }

    var smartConnectTimeout
        get() = runBlocking { dataStore.data.first().smartConnectTimeout }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(smartConnectTimeout = value) }
        }

    @JvmStatic
    var transport
        get() = runBlocking { Transport.fromId(dataStore.data.first().transport) }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(transport = value.id) }
        }

    var bridgesList
        get() = runBlocking { dataStore.data.first().bridgesList }
        set(value) = runBlocking {
            dataStore.updateData { store -> store.copy(bridgesList = value.map { it.trim() }.filter { it.isNotBlank() }) }
        }

    private suspend fun migrate(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        dataStore.updateData { store ->
            store.copy(
                migrated = CURRENT_MIGRATION_STEP,
                smartConnect = prefs.getBoolean("pref_use_smart_connect", store.smartConnect),
                smartConnectTimeout = prefs.getInt("pref_smart_connect_timeout", store.smartConnectTimeout),
                transport = prefs.getString("pref_connection_pathway", null) ?: store.transport,
                bridgesList = prefs.getString("pref_bridges_list", null)
                    ?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: store.bridgesList,
            )
        }

        prefs.edit {
            remove("pref_use_smart_connect")
            remove("pref_smart_connect_timeout")
            remove("pref_connection_pathway")
            remove("pref_bridges_list")
        }
    }

    @Serializable
    private data class SettingsStore(
        val migrated: Int = 0,
        val smartConnect: Boolean = false,
        val smartConnectTimeout: Int = 30,
        val transport: String = Transport.NONE.id,
        val bridgesList: List<String> = emptyList()
    )

    private object SettingsStoreSerializer: Serializer<SettingsStore> {

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
