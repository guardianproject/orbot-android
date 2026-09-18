package org.torproject.android.ui.kindness

import IPtProxy.SnowflakeClientEvents
import IPtProxy.SnowflakeProxy
import android.content.Context
import android.os.Handler
import android.util.Log
import com.netzarchitekten.upnp.UPnP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.torproject.android.R
import org.torproject.android.service.circumvention.BuiltInBridges
import org.torproject.android.util.NetworkUtils
import org.torproject.android.util.Prefs
import org.torproject.android.util.Settings
import org.torproject.android.util.showToast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages configuration, state and static methods of Snowflake Proxy via ipt-proxy
 */
class SnowflakeProxyWrapper(private val service: SnowflakeProxyService) {

    init {
        releaseStalePortsIfNecessary()
        KindnessWatchdogJob.schedulePeriodicKindnessWatchDog(service)
    }

    private var proxy: SnowflakeProxy? = null

    private var mappedPorts = mutableListOf<Int>()

    // Bumped under lock by every enableProxy() and stopProxy() call. The slow  part of a start
    // runs on IO with no lock held, so by the time it is ready to bring the proxy up, a stop
    // (or a newer start) may have won; comparing  generations via shouldCommitStart() is how we
    // finds out if we need to back off, saving us from leaving an orphaned proxy running that
    // see #1183 and #1807
    private var currentGeneration = 0

    @Synchronized
    fun enableProxy() {
        if (proxy != null) return
        val generationAtLaunch = ++currentGeneration
        CoroutineScope(Dispatchers.IO).launch {
            val ports = mapPorts()
            val stunServers =
                BuiltInBridges.getInstance(service)?.snowflake?.firstOrNull()?.ice?.split(",".toRegex())
                    ?.dropLastWhile { it.isEmpty() }?.toTypedArray() ?: emptyArray()
            val stunUrl = stunServers[SecureRandom().nextInt(stunServers.size)]

            val built = SnowflakeProxy()
            val fronts = loadLocalFrontsFromAssets(service)
            with(built) {

                proxyTypeIdentifier = SNOWFLAKE_PROXY_IDENTIFIER
                brokerUrl = fronts[FRONTS_FILE_KEY_BROKER_URL]
                capacity = SNOWFLAKE_PROXY_MAX_CLIENT_CAPACITY
                pollInterval = SNOWFLAKE_PROXY_BROKER_POLL_INTERVAL_SECONDS
                stunServer = stunUrl
                relayUrl = fronts[FRONTS_FILE_KEY_RELAY_URL]
                natProbeUrl = fronts[FRONTS_FILE_KEY_NAT_PROBE]

                clientEvents = object : SnowflakeClientEvents {
                    override fun connected() = onSnowflakeProxyConnectionEstablished()
                    override fun connectionFailed() {}
                    override fun disconnected(country: String?) {}
                    override fun stats(
                        connectionCount: Long,
                        failedConnectionCount: Long,
                        inboundBytes: Long,
                        outboundBytes: Long,
                        inboundUnit: String?,
                        outboundUnit: String?,
                        summaryInterval: Long
                    ) {
                    }

                    override fun natTypeUpdated(natType: String) {
                        Prefs.lastSnowflakeNatType = natType
                    }
                }

                // Setting these to 0 is equivalent to not setting them at all.
                ephemeralMinPort = (ports.firstOrNull() ?: 0).toLong()
                ephemeralMaxPort = (ports.lastOrNull() ?: 0).toLong()
            }

            synchronized(this@SnowflakeProxyWrapper) {
                if (!shouldActuallyStartSnowflakeProxyInIPtProxy(
                        generationAtLaunch = generationAtLaunch,
                        currentGeneration = currentGeneration,
                        proxyPresent = proxy != null
                    )
                ) {
                    // A stop or a newer start arrived while ports were being
                    // mapped. This start lost; it cleans up its own mappings
                    // and never calls start().
                    closePorts(ports)
                    return@launch
                }

                // OK WE ARE GOOD to start snowflake proxy in IPtProxy !

                mappedPorts = ports.toMutableList()
                if (ports.isNotEmpty()) { // see: #1795
                    // We write the set of UPnP ports to Prefs here *before* starting snowflake
                    // proxy. the ports are clenaed up when we call stopProxy(), but if snowflake
                    // dies before that code is launched, we use this value later to release them
                    // staritng *before* starting up a new instance of snowflake proxy
                    encodeUPnPPortsToPrefs(ports)
                }
                proxy = built
                built.start()
                Prefs.snowflakeProxyRunning = true
            }
            service.refreshNotification()
        }
    }

    @Synchronized
    fun stopProxy() {
        currentGeneration++
        val p = proxy ?: return

        p.stop()
        proxy = null
        Prefs.snowflakeProxyRunning = false
        releaseMappedPorts()

        // IPtProxy's start() (called above with the line built.start()...) flips its own isRunning
        // flag inside  a goroutine of its own, such that any subsequent call to IPtProxy's stop()
        // that happens to outrun that this goroutine is effectively a silent no-op and the proxy
        // comes up anyway - rendering it unstoppable once this reference is dropped (#1183)
        //
        // By watching IPtProxy's isRunning for a moment in the coroutine below, we can catch any
        // possible late arrivals and properly puts down the proxy with a call to .stop()
        //
        // IPtProxy's start() returns right after spawning that goroutine, which is what makes
        // calling it under this lock safe.
        CoroutineScope(Dispatchers.IO).launch {
            repeat(10) {
                delay(200.milliseconds)
                if (p.isRunning) {
                    p.stop()
                    return@launch
                }
            }
        }
    }

    fun isProxyRunning(): Boolean = proxy != null

    // Ports a previous run mapped but never released, most likely because the  process died before
    // stopProxy() could run (#1795). The record is cleared synchronously so a start racing this
    // cleanup cannot have its own fresh mappings closed out from under it.
    private fun releaseStalePortsIfNecessary() {
        val stale = decodeUPnPPorts()
        if (stale.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            closePorts(stale)
            Settings.snowflakeUpnpPorts = ""
        }
    }

    internal fun onSnowflakeProxyConnectionEstablished() {
        CoroutineScope(Dispatchers.IO).launch {
            Settings.addSnowflakeServed()
        }

        service.refreshNotification()
        if (Prefs.showSnowflakeProxyToast()) {
            Handler(service.mainLooper).post {
                service.applicationContext.showToast(
                    String.format(
                        service.getString(R.string.snowflake_proxy_client_connected_msg),
                        ONION_EMOJI,
                        ONION_EMOJI
                    )
                )
            }
        }
    }

    private fun mapPorts(): List<Int> {
        if (NetworkUtils.needsAccessLocalNetworkPermission(service) == true) return emptyList()
        val start = Random.nextInt(49152, 65536 - 2)
        val ports = mutableListOf<Int>()
        for (port in (start..start + 2)) {
            if (UPnP.openPortUDP(port, TAG)) {
                ports.add(port)
            }
        }

        // Snowflake Proxy needs Capacity * 2 + 1 = 3 consecutive ports mapped for unrestricted mode.
        // If we can't get all of these, remove the ones we have and
        // rather have Snowflake Proxy run in restricted mode.
        if (ports.size < 3) {
            closePorts(ports)
            return emptyList()
        }
        return ports
    }

    private fun closePorts(ports: List<Int>) {
        for (port in ports) {
            UPnP.closePortUDP(port)
        }
    }

    private fun releaseMappedPorts() {
        closePorts(mappedPorts)
        mappedPorts = mutableListOf()
        Settings.snowflakeUpnpPorts = ""
    }

    @Synchronized
    private fun loadLocalFrontsFromAssets(context: Context): HashMap<String, String> {
        val map = HashMap<String, String>()
        try {
            val reader = BufferedReader(
                InputStreamReader(
                    context.assets.open(SNOWFLAKE_PROXY_FRONTS__FILENAME)
                )
            )
            reader.forEachLine {
                val kv = it.split(FRONTS_FILE_DELIMITER)
                map[kv[0]] = kv[1]
            }
            reader.close()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "error loading snowflake fronts from $SNOWFLAKE_PROXY_FRONTS__FILENAME in assets $e"
            )
        }
        return map
    }

    companion object {

        private const val TAG = "SnowflakeProxyWrapper"
        private const val SNOWFLAKE_PROXY_FRONTS__FILENAME = "fronts"
        private const val FRONTS_FILE_DELIMITER = ' '
        private const val FRONTS_FILE_KEY_BROKER_URL = "snowflake-target-direct"
        private const val FRONTS_FILE_KEY_RELAY_URL = "snowflake-relay-url"
        private const val FRONTS_FILE_KEY_NAT_PROBE = "snowflake-nat-probe"

        // don't change the proxy identifier without getting permission from torproject
        private const val SNOWFLAKE_PROXY_IDENTIFIER = "orbot-android"
        private const val SNOWFLAKE_PROXY_MAX_CLIENT_CAPACITY = 1L
        private const val SNOWFLAKE_PROXY_BROKER_POLL_INTERVAL_SECONDS = 120L


        private const val ONION_EMOJI: String = "\uD83E\uDDC5"

        // The one rule that decides whether a start that did its slow work on
        // IO still owns the right to bring the proxy up (#1183)
        // returns true - if the two generation int args are equal AND IF there's no proxy present
        // see: https://github.com/guardianproject/orbot-android/pull/1807
        fun shouldActuallyStartSnowflakeProxyInIPtProxy(
            generationAtLaunch: Int,
            currentGeneration: Int,
            proxyPresent: Boolean
        ): Boolean {
            return generationAtLaunch == currentGeneration && !proxyPresent
        }

        fun encodeUPnPPortsToPrefs(ports: List<Int>): String {
            val strPorts = ports.joinToString(",")
            Settings.snowflakeUpnpPorts = strPorts
            return strPorts
        }

        fun decodeUPnPPorts(value: String = Settings.snowflakeUpnpPorts): List<Int> =
            value.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..65535 }
    }
}
