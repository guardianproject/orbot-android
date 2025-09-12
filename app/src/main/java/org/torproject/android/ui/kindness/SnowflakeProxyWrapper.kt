package org.torproject.android.ui.kindness

import IPtProxy.SnowflakeClientConnected
import IPtProxy.SnowflakeProxy
import android.os.Handler
import com.netzarchitekten.upnp.UPnP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotConstants.ONION_EMOJI
import org.torproject.android.service.R
import org.torproject.android.service.circumvention.BuiltInBridges
import org.torproject.android.service.circumvention.ContentDeliveryNetworkFronts
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.util.showToast
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

            val stunServers = BuiltInBridges.Companion.getInstance(service)?.snowflake?.firstOrNull()?.ice
                ?.split(",".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray() ?: emptyArray()
            val stunUrl = stunServers[SecureRandom().nextInt(stunServers.size)]

            proxy = SnowflakeProxy()
            service.refreshNotification()
            val fronts = ContentDeliveryNetworkFronts.localFronts(service)
            with(proxy!!) {
                brokerUrl = fronts["snowflake-target-direct"]
                capacity = 1L
                pollInterval = 120L
                stunServer = stunUrl
                relayUrl = fronts["snowflake-relay-url"]
                natProbeUrl = fronts["snowflake-nat-probe"]
                clientConnected = SnowflakeClientConnected { onConnected() }

                // Setting these to 0 is equivalent to not setting them at all.
                ephemeralMinPort = (mappedPorts.firstOrNull() ?: 0).toLong()
                ephemeralMaxPort = (mappedPorts.lastOrNull() ?: 0).toLong()

                start()
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

    fun isProxyRunning() : Boolean = proxy != null


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
}
