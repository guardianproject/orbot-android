package org.torproject.android.ui.kindness

import IPtProxy.SnowflakeClientEvents
import IPtProxy.SnowflakeProxy
import android.content.Context
import android.os.Handler
import android.util.Log
import com.netzarchitekten.upnp.UPnP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.torproject.android.R
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotConstants.ONION_EMOJI
import org.torproject.android.service.circumvention.BuiltInBridges
import org.torproject.android.util.Prefs
import org.torproject.android.util.showToast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom
import kotlin.random.Random

/**
 * Manages configuration, state and static methods of Snowflake Proxy via ipt-proxy
 */
class SnowflakeProxyWrapper(private val service: SnowflakeProxyService) {

    private var proxy: SnowflakeProxy? = null

    private var mappedPorts = mutableListOf<Int>()

    @Synchronized
    fun enableProxy() {
        if (proxy != null) return

        CoroutineScope(Dispatchers.IO).launch {
            val start = Random.nextInt(49152, 65536 - 2)

            for (port in (start..start + 2)) {
                if (UPnP.openPortUDP(port, OrbotConstants.TAG)) {
                    mappedPorts.add(port)
                }
            }

            // Snowflake Proxy needs Capacity * 2 + 1 = 3 consecutive ports mapped for unrestricted mode.
            // If we can't get all of these, remove the ones we have and
            // rather have Snowflake Proxy run in restricted mode.
            if (mappedPorts.size < 3) {
                releaseMappedPorts()
            }

            val stunServers = BuiltInBridges.getInstance(service)?.snowflake?.firstOrNull()?.ice
                ?.split(",".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
                ?: emptyArray()
            val stunUrl = stunServers[SecureRandom().nextInt(stunServers.size)]

            proxy = SnowflakeProxy()
            service.refreshNotification()
            val fronts = localFronts(service)
            with(proxy) {
                this?.brokerUrl = fronts["snowflake-target-direct"]
                this?.capacity = 1L
                this?.pollInterval = 120L
                this?.stunServer = stunUrl
                this?.relayUrl = fronts["snowflake-relay-url"]
                this?.natProbeUrl = fronts["snowflake-nat-probe"]
                this?.clientEvents = object : SnowflakeClientEvents {
                    override fun connected() { onConnected() }
                    override fun disconnected(country: String) {}
                    override fun connectionFailed() {}
                    override fun stats(
                        connectionCount: Long,
                        failedConnectionCount: Long,
                        inboundBytes: Long,
                        outboundBytes: Long,
                        inboundUnit: String,
                        outboundUnit: String,
                        summaryInterval: Long
                    ) {}
                }

                // Setting these to 0 is equivalent to not setting them at all.
                this?.ephemeralMinPort = (mappedPorts.firstOrNull() ?: 0).toLong()
                this?.ephemeralMaxPort = (mappedPorts.lastOrNull() ?: 0).toLong()

                this?.start()
            }
        }
    }

    @Synchronized
    fun stopProxy() {
        if (proxy == null) return
        proxy?.stop()
        proxy = null

        releaseMappedPorts()
    }

    fun isProxyRunning(): Boolean = proxy != null


    private fun onConnected() {
        Prefs.addSnowflakeServed()
        service.refreshNotification()
        if (!Prefs.showSnowflakeProxyToast()) return
        val message: String = String.format(
            service.getString(R.string.snowflake_proxy_client_connected_msg),
            ONION_EMOJI,
            ONION_EMOJI
        )
        Handler(service.mainLooper).post {
            service.applicationContext.showToast(message)
        }
    }

    private fun releaseMappedPorts() {
        for (port in mappedPorts) {
            UPnP.closePortUDP(port)
        }

        mappedPorts = mutableListOf()
    }

    @Synchronized
    private fun localFronts(context: Context): HashMap<String, String> {
        val map = HashMap<String, String>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("fronts")))
            reader.forEachLine {
                val kv = it.split(" ")
                map[kv[0]] = kv[1]
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("CDNFronts", "error loading fronts from assets $e")
        }
        return map
    }
}
