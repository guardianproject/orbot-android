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
import org.torproject.android.service.tor.ShadowSocks
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration.Companion.milliseconds

/**
 * https://developer.android.com/topic/libraries/architecture/datastore
 * https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0-alpha07
 */
object Settings {

    private const val KEYSET_NAME = "settings_keyset"
    private const val PREFERENCE_FILE = "settings_keyset_preference"
    private const val MASTER_KEY_URI = "android-keystore://_androidx_security_master_key_"
    private const val SETTINGS_FILE_NAME = "settings"
    private const val CURRENT_MIGRATION_STEP = 4

    const val PREF_PROXY_TYPE = "pref_proxy_type"
    const val PREF_PROXY_HOST = "pref_proxy_host"
    const val PREF_PROXY_PORT = "pref_proxy_port"
    const val PREF_PROXY_USERNAME = "pref_proxy_username"
    const val PREF_PROXY_PASSWORD = "pref_proxy_password"
    const val PREF_PROXY_SS = "pref_proxy_ss"


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
                ReplaceFileCorruptionHandler { SettingsStoreSerializer.defaultValue },
                produceFile = { settingsFile })

        } catch(_: Throwable) {
            // Ignored. If we really need to, we fall back to unencrypted.

            dataStore = DataStoreFactory.create(
                SettingsStoreSerializer,
                ReplaceFileCorruptionHandler { SettingsStoreSerializer.defaultValue },
                produceFile = { settingsFile })
        }

        if (dataStore.data.first().migrated < CURRENT_MIGRATION_STEP) migrate(context)
    }

    fun set(key: String, value: String) {
        when (key) {
            PREF_PROXY_TYPE -> proxyType = value
            PREF_PROXY_HOST -> proxyHost = value
            PREF_PROXY_PORT -> proxyPort = value
            PREF_PROXY_USERNAME -> proxyUsername = value
            PREF_PROXY_PASSWORD -> proxyPassword = value
            PREF_PROXY_SS -> proxySs = value
        }
    }

    fun get(key: String): String {
        return when (key) {
            PREF_PROXY_TYPE -> proxyType
            PREF_PROXY_HOST -> proxyHost
            PREF_PROXY_PORT -> proxyPort
            PREF_PROXY_USERNAME -> proxyUsername
            PREF_PROXY_PASSWORD -> proxyPassword
            PREF_PROXY_SS -> proxySs
            else -> ""
        }
    }

    suspend fun set(
        smartConnect: Boolean? = null,
        smartConnectTimeout: Int? = null,
        transport: Transport? = null,
        bridgesList: List<String>? = null,
        useVpn: Boolean? = null,
        lastSnowflakeQualityCheck: Long? = null,
        beSnowflakeProxy: Boolean? = null,
        snowflakeUpnpPorts: String? = null,
        currentVersionForUpdate: Int? = null,
        isGeoIpReinstallNeeded: Boolean? = null,
        camoAppDisplayName: String? = null,
        camoAppAltIconIndex: Int? = null,
        stopShowingPowerUserBatteryOptDialog: Boolean? = null,
        proxyType: String? = null,
        proxyHost: String? = null,
        proxyPort: String? = null,
        proxyUsername: String? = null,
        proxyPassword: String? = null,
        proxySs: String? = null,
    ) {
        dataStore.updateData { it.copy(
            smartConnect = smartConnect ?: it.smartConnect,
            smartConnectTimeout = smartConnectTimeout ?: it.smartConnectTimeout,
            transport = transport?.id ?: it.transport,
            bridgesList = bridgesList ?: it.bridgesList,
            useVpn = useVpn ?: it.useVpn,
            lastSnowflakeQualityCheck = lastSnowflakeQualityCheck ?: it.lastSnowflakeQualityCheck,
            beSnowflakeProxy = beSnowflakeProxy ?: it.beSnowflakeProxy,
            snowflakeUpnpPorts = snowflakeUpnpPorts ?: it.snowflakeUpnpPorts,
            currentVersionForUpdate = currentVersionForUpdate ?: it.currentVersionForUpdate,
            isGeoIpReinstallNeeded = isGeoIpReinstallNeeded ?: it.isGeoIpReinstallNeeded,
            camoAppDisplayName = camoAppDisplayName ?: it.camoAppDisplayName,
            camoAppAltIconIndex = camoAppAltIconIndex ?: it.camoAppAltIconIndex,
            stopShowingPowerUserBatteryOptDialog = stopShowingPowerUserBatteryOptDialog ?: it.stopShowingPowerUserBatteryOptDialog,
            proxyType = proxyType ?: it.proxyType,
            proxyHost = proxyHost ?: it.proxyHost,
            proxyPort = proxyPort ?: it.proxyPort,
            proxyUsername = proxyUsername ?: it.proxyUsername,
            proxyPassword = proxyPassword ?: it.proxyPassword,
            proxySs = proxySs ?: it.proxySs,
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

    @JvmStatic
    var useVpn
        get() = runBlocking { dataStore.data.first().useVpn }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(useVpn = value) }
        }

    var snowflakeNeedsQualityCheck
        get() = runBlocking {
            val last = dataStore.data.first().lastSnowflakeQualityCheck

            // A new quality check should be done every 24 hours.
            last <= System.currentTimeMillis() - 24 * 60 * 60 * 1000
        }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(lastSnowflakeQualityCheck = if (value) 0 else System.currentTimeMillis()) }
        }

    var beSnowflakeProxy
        get() = runBlocking { dataStore.data.first().beSnowflakeProxy }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(beSnowflakeProxy = value) }
        }

    // see https://github.com/guardianproject/orbot-android/issues/1795
    var snowflakeUpnpPorts
        get() = runBlocking { dataStore.data.first().snowflakeUpnpPorts }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(snowflakeUpnpPorts = value) }
        }

    val snowflakesServed
        get() = runBlocking { dataStore.data.first().snowflakesServed }

    val snowflakesServedWeekly: Int
        get() = runBlocking {
            refreshWeeklyServedIfNeeded()

            dataStore.data.first().snowflakesServedWeekly
        }

    suspend fun addSnowflakeServed() {
        refreshWeeklyServedIfNeeded()

        dataStore.updateData { it.copy(
            snowflakesServed = it.snowflakesServed + 1,
            snowflakesServedWeekly = it.snowflakesServedWeekly + 1,
        ) }
    }

    private suspend fun refreshWeeklyServedIfNeeded(clearAllWeeklyOverride: Boolean = false) {
        val week = System.currentTimeMillis().milliseconds.inWholeDays.div(7)

        if (clearAllWeeklyOverride || dataStore.data.first().snowflakesServedWeekTimestamp != week) {
            dataStore.updateData { it.copy(
                snowflakesServedWeekly = 0,
                snowflakesServedWeekTimestamp = week
            ) }
        }
    }

    var currentVersionForUpdate
        get() = runBlocking { dataStore.data.first().currentVersionForUpdate }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(currentVersionForUpdate = value) }
        }

    @JvmStatic
    var isGeoIpReinstallNeeded
        get() = runBlocking { dataStore.data.first().isGeoIpReinstallNeeded }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(isGeoIpReinstallNeeded = value) }
        }

    var camoAppDisplayName
        get() = runBlocking { dataStore.data.first().camoAppDisplayName }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(camoAppDisplayName = value) }
        }

    var camoAppAltIconIndex
        get() = runBlocking { dataStore.data.first().camoAppAltIconIndex }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(camoAppAltIconIndex = value) }
        }

    var stopShowingPowerUserBatteryOptDialog
        get() = runBlocking { dataStore.data.first().stopShowingPowerUserBatteryOptDialog }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(stopShowingPowerUserBatteryOptDialog = value) }
        }

    var torifiedApps
        get() = runBlocking { dataStore.data.first().torifiedApps }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(torifiedApps = value) }
        }

    @JvmStatic
    var torDnsPortResolved
        get() = runBlocking { dataStore.data.first().torDnsPortResolved }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(torDnsPortResolved = value) }
        }

    var proxyType
        get() = runBlocking { dataStore.data.first().proxyType }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(proxyType = value) }
        }

    var proxyHost
        get() = runBlocking { dataStore.data.first().proxyHost }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(proxyHost = value) }
        }

    var proxyPort
        get() = runBlocking { dataStore.data.first().proxyPort }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(proxyPort = value) }
        }

    var proxyUsername
        get() = runBlocking { dataStore.data.first().proxyUsername }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(proxyUsername = value) }
        }

    var proxyPassword
        get() = runBlocking { dataStore.data.first().proxyPassword }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(proxyPassword = value) }
        }

    var proxySs
        get() = runBlocking { dataStore.data.first().proxySs }
        set(value) = runBlocking {
            dataStore.updateData { it.copy(proxySs = value) }
        }

    /**
     * If the URI is well-formed, the first item will be filled.
     * If the URI is malformed, the second item will be filled with the original string.
     */
    val outboundProxy: Pair<URI?, String?>
        get() = runBlocking {
            val data = dataStore.data.first()

            val scheme = data.proxyType.lowercase().trim()
            if (scheme.isEmpty()) return@runBlocking Pair(null, null)

            if (scheme == ShadowSocks.SCHEME) {
                val config = data.proxySs.trim()
                if (config.isEmpty()) return@runBlocking Pair(null, null)

                try {
                    Pair(URI(config), null)
                } catch (_: URISyntaxException) {
                    Pair(null, config)
                }
            }

            val host = data.proxyHost.trim()
            if (host.isEmpty()) return@runBlocking Pair(null, null)

            val url = StringBuilder(scheme)
            url.append("://")

            var needsAt = false
            val username = data.proxyUsername
            if (username.isNotEmpty()) {
                url.append(username)
                needsAt = true
            }

            val password = data.proxyPassword
            if (password.isNotEmpty()) {
                url.append(":")
                url.append(password)
                needsAt = true
            }

            if (needsAt) url.append("@")

            url.append(host)

            val port = try {
                data.proxyPort.trim().toInt()
            } catch (_: Throwable) {
                0
            }

            if (port in 1..<65536) {
                url.append(":")
                url.append(port)
            }

            url.append("/")

            try {
                Pair(URI(url.toString()), null)
            } catch (_: URISyntaxException) {
                // Can happen when you, say, put a space in the hostname:
                // https://github.com/guardianproject/orbot-android/issues/1563
                // https://www.rfc-editor.org/rfc/inline-errata/rfc3986.html
                Pair(null, url.toString())
            }
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
                useVpn = prefs.getBoolean("pref_vpn", store.useVpn),
                lastSnowflakeQualityCheck = prefs.getLong("pref_last_snowflake_quality_check", store.lastSnowflakeQualityCheck),
                beSnowflakeProxy = prefs.getBoolean("pref_be_a_snowflake", store.beSnowflakeProxy),
                snowflakeUpnpPorts = prefs.getString("pref_snowflake_upnp_ports", null) ?: store.snowflakeUpnpPorts,
                snowflakesServed = prefs.getInt("pref_snowflakes_served", store.snowflakesServed),
                snowflakesServedWeekly = prefs.getInt("pref_snowflakes_served_weekly", store.snowflakesServedWeekly),
                snowflakesServedWeekTimestamp = prefs.getLong("pref_snowflakes_served_week", store.snowflakesServedWeekTimestamp),
                currentVersionForUpdate = prefs.getInt("pref_current_version", store.currentVersionForUpdate),
                isGeoIpReinstallNeeded = prefs.getBoolean("pref_geoip", store.isGeoIpReinstallNeeded),
                camoAppDisplayName = prefs.getString("pref_key_camo_app_display_name", null) ?: store.camoAppDisplayName,
                camoAppAltIconIndex = prefs.getInt("pref_key_camo_alticon", store.camoAppAltIconIndex),
                stopShowingPowerUserBatteryOptDialog = prefs.getBoolean("hide_battery_opt_dialog", store.stopShowingPowerUserBatteryOptDialog),
                torifiedApps = prefs.getString("PrefTord", null) ?: store.torifiedApps,
                torDnsPortResolved = prefs.getInt("PREFS_DNS_PORT", 0),
                proxyType = prefs.getString(PREF_PROXY_TYPE, null) ?: store.proxyType,
                proxyHost = prefs.getString(PREF_PROXY_HOST, null) ?: store.proxyHost,
                proxyPort = prefs.getString(PREF_PROXY_PORT, null) ?: store.proxyPort,
                proxyUsername = prefs.getString(PREF_PROXY_USERNAME, null) ?: store.proxyUsername,
                proxyPassword = prefs.getString(PREF_PROXY_PASSWORD, null) ?: store.proxyPassword,
                proxySs = prefs.getString(PREF_PROXY_SS, null) ?: store.proxySs,
            )
        }

        prefs.edit {
            remove("pref_use_smart_connect")
            remove("pref_smart_connect_timeout")
            remove("pref_connection_pathway")
            remove("pref_bridges_list")
            remove("pref_vpn")
            remove("pref_last_snowflake_quality_check")
            remove("pref_be_a_snowflake")
            remove("pref_snowflake_upnp_ports")
            remove("pref_snowflakes_served")
            remove("pref_snowflakes_served_weekly")
            remove("pref_snowflakes_served_week")
            remove("pref_current_version")
            remove("pref_geoip")
            remove("pref_key_camo_app_display_name")
            remove("pref_key_camo_alticon")
            remove("hide_battery_opt_dialog")
            remove("PrefTord")
            remove("PREFS_DNS_PORT")
            remove(PREF_PROXY_TYPE)
            remove(PREF_PROXY_HOST)
            remove(PREF_PROXY_PORT)
            remove(PREF_PROXY_USERNAME)
            remove(PREF_PROXY_PASSWORD)
            remove(PREF_PROXY_SS)
        }
    }

    @Serializable
    private data class SettingsStore(
        val migrated: Int = 0,
        val smartConnect: Boolean = false,
        val smartConnectTimeout: Int = 30,
        val transport: String = Transport.NONE.id,
        val bridgesList: List<String> = emptyList(),
        val useVpn: Boolean = false,
        val lastSnowflakeQualityCheck: Long = 0,
        val beSnowflakeProxy: Boolean = false,
        val snowflakeUpnpPorts: String = "",
        val snowflakesServed: Int = 0,
        val snowflakesServedWeekly: Int = 0,
        val snowflakesServedWeekTimestamp: Long = 0,
        val currentVersionForUpdate: Int = 0,
        val isGeoIpReinstallNeeded: Boolean = true,
        val camoAppDisplayName: String = "Android",
        val camoAppAltIconIndex: Int = -1,
        val stopShowingPowerUserBatteryOptDialog: Boolean = false,
        val torifiedApps: String = "",
        val torDnsPortResolved: Int = 0,
        val proxyType: String = "",
        val proxyHost: String = "",
        val proxyPort: String = "",
        val proxyUsername: String = "",
        val proxyPassword: String = "",
        val proxySs: String = "",
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
