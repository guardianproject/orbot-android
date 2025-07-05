package org.torproject.android.service.circumvention

import IPtProxy.SnowflakeClientConnected
import IPtProxy.SnowflakeProxy
import android.content.Context
import android.os.Handler
import org.torproject.android.service.OrbotConstants.ONION_EMOJI
import org.torproject.android.service.OrbotService
import org.torproject.android.service.R
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.util.Utils.showToast
import java.security.SecureRandom

class SnowflakeProxyWrapper(private val context: Context) {
    private var proxy: SnowflakeProxy? = null

    @Synchronized
    fun enableProxy(
        hasWifi: Boolean,
        hasPower: Boolean
    ) {
        if (proxy != null) return
        if (Prefs.limitSnowflakeProxyingWifi() && !hasWifi) return
        if (Prefs.limitSnowflakeProxyingCharging() && !hasPower) return
        proxy = SnowflakeProxy()
        val stunServers = BuiltInBridges.getInstance(context)?.snowflake?.firstOrNull()?.ice
            ?.split(",".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray() ?: emptyArray()
        val stunUrl = stunServers[SecureRandom().nextInt(stunServers.size)]

        proxy = SnowflakeProxy()
        with(proxy!!) {
            brokerUrl = OrbotService.getCdnFront("snowflake-target-direct")
            capacity = 1L
            pollInterval = 120L
            stunServer = stunUrl
            relayUrl = OrbotService.getCdnFront("snowflake-relay-url")
            natProbeUrl = OrbotService.getCdnFront("snowflake-nat-probe")
            clientConnected = SnowflakeClientConnected { onConnected() }
            start()
        }

        if (Prefs.showSnowflakeProxyMessage()) {
            val message = context.getString(R.string.log_notice_snowflake_proxy_enabled)
            Handler(context.mainLooper).post {
                context.applicationContext.showToast(message)
            }
        }
    }

    @Synchronized
    fun stopProxy() {
        if (proxy == null) return
        proxy!!.stop()
        proxy = null

        if (Prefs.showSnowflakeProxyMessage()) {
            val message = context.getString(R.string.log_notice_snowflake_proxy_disabled)
            Handler(context.mainLooper).post {
                context.applicationContext.showToast(message)
            }
        }
    }

    private fun onConnected() {
        Prefs.addSnowflakeServed()
        if (!Prefs.showSnowflakeProxyMessage()) return
        val message: String = String.format(
            context.getString(R.string.snowflake_proxy_client_connected_msg),
            ONION_EMOJI,
            ONION_EMOJI
        )
        Handler(context.mainLooper).post {
            context.applicationContext.showToast(message)
        }

    }
}
