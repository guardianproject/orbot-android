package org.torproject.android.service.circumvention

import IPtProxy.Controller
import IPtProxy.IPtProxy
import IPtProxy.OnTransportEvents
import android.content.Context
import android.util.Log
import org.torproject.android.util.Prefs

enum class Transport(val id: String) {

    /**
     * Represents a direct connection to Tor with no bridges.
     */
    NONE("direct"),

    MEEK_AZURE("meek"),
    OBFS4("obfs4"),

    /**
     * Tor connection with Snowflake, settable from ConfigConnectionBottomSheet.
     */
    SNOWFLAKE("snowflake"),

    /**
     * Tor connection with Snowflake using AMP brokers, settable from ConfigConnectionBottomSheet.
     */
    SNOWFLAKE_AMP("snowflake_amp"),

    /**
     * Use AMP brokers and start Snowflake with SQS rendezvous. Currently no way to
     * set SQS setting in app, if you force it, a runtime exception is thrown in
     * {@link org.torproject.android.service.circumvention.SnowflakeClient#startWithSqsRendezvous(Controller)}
     */
    SNOWFLAKE_SQS("snowflake_sqs"),

    WEBTUNNEL("webtunnel"),

    DNSTT("dnstt"),

    /**
     * Start lyrebird with obfs4 bridges stored in @{link {@link #getBridgesList()}}
     * This can be set in manually via the CustomBridgeBottomSheet.
     */
    CUSTOM("custom");

    companion object {
        @JvmStatic
        var stateLocation = ""

        const val TAG = "Transport"

        val controller: Controller by lazy {
            Controller(stateLocation, true, false, "INFO", statusCollector)
        }

        fun fromId(id: String): Transport {
            return when (id) {
                MEEK_AZURE.id -> MEEK_AZURE
                OBFS4.id -> OBFS4
                SNOWFLAKE.id -> SNOWFLAKE
                SNOWFLAKE_AMP.id -> SNOWFLAKE_AMP
                SNOWFLAKE_SQS.id -> SNOWFLAKE_SQS
                WEBTUNNEL.id -> WEBTUNNEL
                DNSTT.id -> DNSTT
                CUSTOM.id -> CUSTOM
                else -> NONE
            }
        }

        private val statusCollector = object : OnTransportEvents {
            override fun connected(name: String?) {
                if (name == null) return

                Log.d(TAG, "$name connected")
            }

            override fun error(name: String?, error: Exception?) {
                if (name == null) return

                Log.e(TAG, "$name error: $error")
            }

            override fun stopped(name: String?, error: Exception?) {
                if (name == null) return

                if (error != null) {
                    Log.e(
                        TAG,
                        "$name stopped: ${error.localizedMessage}"
                    )
                } else {
                    Log.d(TAG, "$name stopped.")
                }
            }

        }

        /**
         * Seems more reliable in certain countries than the currently advertised one.
         */
        private val addFronts = listOf("github.githubassets.com")

        private const val AMP_BROKER = "https://snowflake-broker.torproject.net/"
        private val ampFronts = listOf("www.google.com")
        private const val AMP_CACHE = "https://cdn.ampproject.org/"
        private const val SQS_QUEUE =
            "https://sqs.us-east-1.amazonaws.com/893902434899/snowflake-broker"
        private const val SQS_CREDENTIALS =
            "eyJhd3MtYWNjZXNzLWtleS1pZCI6IkFLSUE1QUlGNFdKSlhTN1lIRUczIiwiYXdzLXNlY3JldC1rZXkiOiI3U0RNc0pBNHM1RitXZWJ1L3pMOHZrMFFXV0lsa1c2Y1dOZlVsQ0tRIn0="

        private val DNSTT_BRIDGES = listOf(
            "dnstt 192.0.2.30:80 F6B3CCA08E3C4026783FA14DBB14A3ADBCD0D27D doh=https://dns.google/dns-query pubkey=488dd8eeab891e2df1b0fc0e5d8da28da23ea057a81934994d150105c2024048 domain=r.f14.1e-100.net",
            "dnstt 192.0.2.31:80 F6B3CCA08E3C4026783FA14DBB14A3ADBCD0D27D udp=2.189.44.44:53 pubkey=488dd8eeab891e2df1b0fc0e5d8da28da23ea057a81934994d150105c2024048 domain=r.f14.1e-100.net",
            "dnstt 192.0.2.32:80 A998F319ADB60EE344540EC4B21524CC484F96BE doh=https://dns.google/dns-query pubkey=241169008830694749fe96bb070c4855c5bb5b9c47b3833ed7d88521ba30a43f domain=t.ruhnama.net",
        )
    }

    val transportNames: Set<String>
        get() {
            return when (this) {
                NONE -> emptySet()
                MEEK_AZURE -> setOf(IPtProxy.MeekLite)
                OBFS4 -> setOf(IPtProxy.Obfs4)
                WEBTUNNEL -> setOf(IPtProxy.Webtunnel)
                DNSTT -> setOf(IPtProxy.Dnstt)
                CUSTOM -> {
                    Prefs.bridgesList
                        .mapNotNull { Bridge(it).transport }
                        .filter { it.isNotBlank() }
                        .toSet()
                }

                else -> setOf(IPtProxy.Snowflake)
            }
        }

    val port: Long
        get() {
            val transport = transportNames.firstOrNull() ?: return 0

            return controller.port(transport)
        }

    fun getTorConfig(context: Context): List<String> {
        val result = mutableListOf<String>()
        result.add("UseBridges ${if (this == NONE) "0" else "1"}")

        for (transport in transportNames) {

            //sometimes there is a 0 for the port, which is invalid
            if (controller.port(transport) > 0)
                result.add(
                    "ClientTransportPlugin $transport socks5 127.0.0.1:${
                        controller.port(
                            transport
                        )
                    }"
                )
        }

        when (this) {
            NONE -> {
                val proxy = Prefs.outboundProxy.first
                if (proxy != null) {
                    var hostPort = proxy.host
                    if (proxy.port in 1..<65536) hostPort += ":${proxy.port}"

                    // Modern tor only supports https, socks4 and socks5. *No* http!
                    when (proxy.scheme) {
                        "https" -> {
                            result.add("HTTPSProxy $hostPort")

                            if (!proxy.userInfo.isNullOrBlank()) {
                                result.add("HTTPSProxyAuthenticator ${proxy.userInfo}")
                            }
                        }

                        "socks4" -> {
                            result.add("Socks4Proxy $hostPort")
                        }

                        "socks5" -> {
                            result.add("Socks5Proxy $hostPort")

                            val userInfo = proxy.userInfo?.split(":")

                            if (!userInfo?.getOrNull(0).isNullOrEmpty()) {
                                result.add("Socks5ProxyUsername ${userInfo[0]}")

                                var password = userInfo.getOrNull(1) ?: " "
                                if (password.isEmpty()) password = " "

                                result.add("Socks5ProxyPassword $password")
                            }
                        }
                    }
                }
            }

            MEEK_AZURE -> {
                BuiltInBridges.getInstance(context)?.meekAzure?.forEach {
                    result.add("Bridge ${it.raw}")
                }
            }

            OBFS4 -> {
                BuiltInBridges.getInstance(context)?.obfs4?.forEach {
                    result.add("Bridge ${it.raw}")
                }
            }

            SNOWFLAKE -> {
                BuiltInBridges.getInstance(context)?.snowflake?.forEach {
                    val builder = Bridge.Builder(it)
                    builder.fronts.addAll(addFronts)

                    result.add("Bridge ${builder.build().raw}")
                }
            }

            SNOWFLAKE_AMP -> {
                BuiltInBridges.getInstance(context)?.snowflake?.forEachIndexed { idx, it ->
                    val builder = Bridge.Builder(it)
                    builder.ip = "192.0.2.${5 + idx}"
                    builder.url = AMP_BROKER
                    builder.fronts = ampFronts.toMutableSet()

                    result.add("Bridge ${builder.build().raw}")
                }
            }

            SNOWFLAKE_SQS -> {
                BuiltInBridges.getInstance(context)?.snowflake?.forEachIndexed { idx, it ->
                    val builder = Bridge.Builder(it)
                    builder.ip = "192.0.2.${5 + idx}"
                    builder.url = null
                    builder.fronts.clear()

                    result.add("Bridge ${builder.build().raw}")
                }
            }

            WEBTUNNEL -> {
                BuiltInBridges.getInstance(context)?.webtunnel?.forEach {
                    result.add("Bridge ${it.raw}")
                }
            }

            DNSTT -> {
                DNSTT_BRIDGES.forEach {
                    result.add("Bridge $it")
                }
            }

            CUSTOM -> {
                Prefs.bridgesList.forEach {
                    result.add("Bridge $it")
                }
            }
        }

        return result
    }

    /**
     * @throws Exception if the transport cannot be initialized or if it couldn't bind a port for listening.
     */
    @Suppress("KotlinUnreachableCode") // unreachable code is disabled SQS logic
    fun start(context: Context) {
        when (this) {
            SNOWFLAKE -> {
                val snowflake = BuiltInBridges.getInstance(context)?.snowflake?.firstOrNull()

                // Seems more reliable in certain countries than the currently advertised one.
                val fronts = addFronts.toMutableSet()
                snowflake?.front?.let { fronts.add(it) }
                snowflake?.fronts?.let { fronts.addAll(it) }

                controller.snowflakeIceServers = snowflake?.ice ?: ""
                controller.snowflakeBrokerUrl = snowflake?.url ?: ""
                controller.snowflakeFrontDomains = fronts.joinToString(",")
                controller.snowflakeAmpCacheUrl = ""
                controller.snowflakeSqsUrl = ""
                controller.snowflakeSqsCreds = ""
            }

            SNOWFLAKE_AMP -> {
                controller.snowflakeIceServers =
                    BuiltInBridges.getInstance(context)?.snowflake?.firstOrNull()?.ice ?: ""
                controller.snowflakeBrokerUrl = AMP_BROKER
                controller.snowflakeFrontDomains = ampFronts.joinToString(",")
                controller.snowflakeAmpCacheUrl = AMP_CACHE
                controller.snowflakeSqsUrl = ""
                controller.snowflakeSqsCreds = ""
            }

            SNOWFLAKE_SQS -> {
                /* TODO Make sure SQS queue and credentials are up to date in assets/fronts when
                    re-enabling this feature. also remove android:visibility="gone" from the SQS
                    container in app project's layout/config_connection_bottom_sheet.xml
                    */
                throw RuntimeException("Snowflake SQS Not supported right now https://github.com/guardianproject/orbot-android/issues/1320")

                controller.snowflakeIceServers =
                    BuiltInBridges.getInstance(context)?.snowflake?.firstOrNull()?.ice ?: ""
                controller.snowflakeBrokerUrl = ""
                controller.snowflakeFrontDomains = ""
                controller.snowflakeAmpCacheUrl = ""
                controller.snowflakeSqsUrl = SQS_QUEUE
                controller.snowflakeSqsCreds = SQS_CREDENTIALS
            }

            else -> Unit
        }

        val pair = Prefs.outboundProxy
        val proxy: String? = if (pair.first == null) null else pair.first.toString()

        for (transport in transportNames) {
            controller.start(transport, if (transport == IPtProxy.Snowflake) null else proxy)
        }
    }

    fun stop() {
        for (transport in transportNames) {
            controller.stop(transport)
        }
    }
}