package org.torproject.android.service.circumvention

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.Calendar

@Serializable
data class BuiltInBridges(
    var meek: List<Bridge>? = null,
    var obfs4: List<Bridge>? = null,
    var snowflake: List<Bridge>? = null,
    var webtunnel: List<Bridge>? = null,
    var dnstt: List<Bridge>? = null,
) {

    val empty: Boolean
        get() {
            return meek.isNullOrEmpty() && obfs4.isNullOrEmpty() &&
                    snowflake.isNullOrEmpty() && webtunnel.isNullOrEmpty() &&
                    dnstt.isNullOrEmpty()
        }

    fun store(context: Context) {
        getUpdateFile(context).writeText(Json.encodeToString(this))
    }


    companion object {

        const val FILE_NAME = "builtin-bridges.json"

        const val UPDATE_FILE_NAME = "updated-bridges.json"

        val dnsCountries = listOf("ae", "af", "bd", "cn", "co", "id", "ir", "kw", "pk", "qa", "ru", "sy", "tr", "ug", "uz")

        /**
         * https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/trac/-/issues/40001#note_2811603
         *
         * "Use 192.0.2.4:1 for the placeholder bridge IP address. 192.0.2.3:1 is used for Snowflake, and
         *  tor does not handle well the case of different bridges having the same address, even if the
         *  address is not really used. We have been incrementing the last octet of placeholder addresses for
         *  each new transport that uses placeholder addresses: .1 = flashproxy, .2 = meek, .3 = snowflake,
         *  .4 = dnstt. If there are multiple dnstt bridges in the same torrc, increment the port number"
         */
        val dnsttBridges = listOf(
            Bridge("dnstt 192.0.2.4:1 A998F319ADB60EE344540EC4B21524CC484F96BE doh=https://dns.google/dns-query pubkey=241169008830694749fe96bb070c4855c5bb5b9c47b3833ed7d88521ba30a43f domain=t.ruhnama.net"),
            Bridge("dnstt 192.0.2.4:2 80EEFA4F4875ED2B7B5A86DF2D7588AD32E29F15 doh=https://dns.google/dns-query pubkey=a2fb71077eeaa54a02cda7a90be306af5d299ab21822a8b277d4eacbc9168631 domain=t2.bypasscensorship.org"),
        )

        /**
        Tor will max at 32 simultaneous SOCKS connections to PTs.

        Leave a buffer, though, for other connections. Seems Tor wants that.
         */
        const val MAX_DNSTT_BRIDGES_COUNT = 32 - 2

        private var instance: BuiltInBridges? = null


        fun getInstance(context: Context? = null): BuiltInBridges? {
            if (instance == null && context != null) {
                try {
                    instance = read(getUpdateFile(context).readText())
                }
                catch (_: Throwable) {}
            }

            if (instance == null && context != null) {
                try {
                    instance =
                        read(context.assets.open(FILE_NAME).bufferedReader().use { it.readText() })
                }
                catch (_: Throwable) {}
            }

            return instance
        }

        /**
         * We consider stored updated-bridges.json file to be outdated after 2 days.
         */
        fun isOutdated(context: Context): Boolean {
            val lastModified = try {
                getUpdateFile(context).lastModified()
            }
            catch (_: Throwable) {
                0
            }

            return Calendar.getInstance().timeInMillis - lastModified > 2 * 24 * 60 * 60 * 1000
        }

        fun invalidate() {
            instance = null
        }

        fun getUpdateFile(context: Context): File {
            return File(context.cacheDir, UPDATE_FILE_NAME)
        }

        private fun read(json: String): BuiltInBridges? {
            return try {
                MoatApi.json.decodeFromString(json)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Creates a list of DNSTT bridge lines using UDP DNS servers which are known to work in the given country.
     *
     * Since Tor only creates up to ``BuiltInBridges.maxDnsttBridgesCount`` connections simultaneously,
     * if the list of DNS servers times number of DNSTT servers we know is larger than that, a random
     * sample of that size of the actual result is returned.
     *
     * Note: There is a `global` list of UDP DNS servers, which you can theoretically fetch, but since
     * UDP is not encrypted, it's typically a better idea to use DoH or DoT servers instead.
     * (Like our base ``BuiltInBridges.dnsttBridges`` list does).
     *
     * The UDP servers only make sense to use in a heavily censored environment, where public DoH
     * and DoT DNS servers are blocked.
     *
     * That's what these UDP lists are for. Insofar the `global` list is only there for the sake of
     * completeness, not so much because it makes sense to use it.
     *
     *  Here's a list of publicly available DoH servers, in case you're unhappy with our choice:
     *  https://github.com/curl/curl/wiki/DNS-over-HTTPS#publicly-available-servers
     *
     * @param countryCode: The country code for a country as listed in ``BuiltInBridges.dnsCountries`` or `global`.
     * @return: A list of DNSTT bridge lines using UDP DNS servers which are known to work in the given country.
     */
    fun getUdpDnstt(context: Context, countryCode: String?): List<Bridge>? {
        if (countryCode.isNullOrEmpty()) return null
        if (countryCode != "global" && !dnsCountries.contains(countryCode.lowercase())) return null

        val dnsInfo: DnsInfo

        try {
            val data = context.assets.open("dns-${countryCode.lowercase()}.json").bufferedReader()
                .use { it.readText() }

            dnsInfo = MoatApi.json.decodeFromString(data)
        }
        catch (_: Throwable) {
            return null
        }

        var i = dnsttBridges.size

        val bridges = mutableListOf<Bridge>()

        for (server in dnsInfo.servers) {
            val addr = try { URI("scheme://${server.ip}") } catch (_: Throwable) { null }

            for (bridge in dnsttBridges) {
                val builder = bridge.buildUpon() ?: continue

                builder.port = i

                // Don't set a fingerprint, otherwise only the first bridge lines with unique fingerprints will be used.
                builder.fingerprint1 = null

                builder.doh = null
                builder.dot = null
                builder.udp = "${addr?.host ?: server.ip}:${if ((addr?.port ?: -1) < 0) 53 else addr!!.port}"

                i += 1

                bridges.add(builder.build())
            }
        }

        if (bridges.size <= MAX_DNSTT_BRIDGES_COUNT) {
            return bridges
        }

        val selection = mutableSetOf<Bridge>()

        while (selection.size < MAX_DNSTT_BRIDGES_COUNT) {
            selection.add(bridges.random())
        }

        return selection.toList()
    }
}

@Serializable
data class DnsInfo(
    var country: String,
    var countryCode: String,
    var description: String,
    var lastUpdated: String,
    var servers: List<DnsServer>
)
@Serializable
data class DnsServer(
    var name: String,
    var ip: String
)