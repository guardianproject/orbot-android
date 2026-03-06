package org.torproject.android.service.circumvention

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Parser for bridge lines.
 */
@Serializable(with = BridgeAsStringSerializer::class)
@Suppress("MemberVisibilityCanBePrivate", "unused")
class Bridge(var raw: String) {

    class Builder(
        var transport: String? = null,
        var ip: String,
        var port: Int,
        var fingerprint1: String? = null
    ) {

        var fingerprint2: String? = null
        var url: String? = null
        var front: String? = null
        var fronts = mutableSetOf<String>()
        var cert: String? = null
        var iatMode: String? = null
        var ice: String? = null
        var utls: String? = null
        var utlsImitate: String? = null
        var ver: String? = null

        // DNSTT
        var udp: String? = null
        var doh: String? = null
        var dot: String? = null
        var pubkey: String? = null
        var domain: String? = null

        constructor(bridge: Bridge) : this(bridge.transport, bridge.ip ?: "", bridge.port ?: 0, bridge.fingerprint1) {
            if (ip.isEmpty() || port < 1)
            {
                throw RuntimeException("Tried to create Bridge.Builder with invalid bridge!")
            }

            fingerprint2 = bridge.fingerprint2

            url = bridge.url
            front = bridge.front

            bridge.fronts?.let { fronts.addAll(it) }

            cert = bridge.cert
            iatMode = bridge.iatMode
            ice = bridge.ice
            utls = bridge.utls
            utlsImitate = bridge.utlsImitate
            ver = bridge.ver
            udp = bridge.udp
            doh = bridge.doh
            dot = bridge.dot
            pubkey = bridge.pubkey
            domain = bridge.domain
        }

        fun build(): Bridge {
            val params = mutableListOf<String>()

            transport?.let {
                if (it.isNotEmpty()) params.add(it)
            }

            params.add("$ip:$port")

            fingerprint1?.let {
                if (it.isNotEmpty()) params.add(it)
            }

            append(fingerprint2, params, "fingerprint")
            append(url, params, "url")
            append(front, params, "front")
            append(fronts.filter { it.isNotEmpty() }.joinToString(","), params, "fronts")
            append(cert, params, "cert")
            append(iatMode, params, "iat-mode")
            append(ice, params, "ice")
            append(utls, params, "utls")
            append(utlsImitate, params, "utls-imitate")
            append(ver, params, "ver")
            append(udp, params, "udp")
            append(doh, params, "doh")
            append(dot, params, "dot")
            append(pubkey, params, "pubkey")
            append(domain, params, "domain")

            return Bridge(params.joinToString(" "))
        }

        private fun append(value: String?, list: MutableList<String>, name: String) {
            value?.let {
                if (it.isNotEmpty()) list.add("$name=$it")
            }
        }
    }

    val rawPieces: List<String>
        get() {
            val pieces = raw.split(" ").toMutableList()

            // "Vanilla" bridges (conventional relays without obfuscation) don't have a transport.
            // Add an empty one, so parsing works.
            if (pieces.size < 3) {
                pieces.add(0, "")

                return pieces
            }

            return pieces
        }

    val transport: String?
        get() {
            val transport = rawPieces.firstOrNull()

            return if (transport.isNullOrEmpty()) null else transport
        }

    val address
        get() = rawPieces.getOrNull(1)

    val ip: String?
        get() {
            var pieces = address?.split(":")
            if (pieces.isNullOrEmpty()) return null

            // Remove port.
            pieces = pieces.dropLast(1)

            // Join IPv6 again.
            return pieces.joinToString(":")
        }

    val port
        get() = address?.split(":")?.lastOrNull()?.toInt()

    val fingerprint1: String?
        get() {
            val fingerprint1 = rawPieces.getOrNull(2) ?: return null

            return if (fingerprintRegex.matches(fingerprint1)) fingerprint1 else null
        }

    val fingerprint2
        get() = getPiece("fingerprint")

    val url
        get() = getPiece("url")

    val front
        get() = getPiece("front")

    val fronts
        get() = getPiece("fronts")?.split(",")?.filter { it.isNotEmpty() }

    val cert
        get() = getPiece("cert")

    val iatMode
        get() = getPiece("iat-mode")

    val ice
        get() = getPiece("ice")

    val utls
        get() = getPiece("utls")

    val utlsImitate
        get() = getPiece("utls-imitate")

    val ver
        get() = getPiece("ver")

    val udp
        get() = getPiece("udp")

    val doh
        get() = getPiece("dot")

    val dot
        get() = getPiece("dot")

    val pubkey
        get() = getPiece("pubkey")

    val domain
        get() = getPiece("domain")


    fun buildUpon(): Builder? {
        return try {
            Builder(this)
        }
        catch (_: Throwable) {
            null
        }
    }

    private fun getPiece(name: String): String? {
        return rawPieces.firstOrNull { it.startsWith("$name=") }
            ?.split("=")?.lastOrNull()
    }

    override fun toString(): String {
        return raw
    }

    companion object {

        val fingerprintRegex = Regex("^[a-f0-9]{40}$", RegexOption.IGNORE_CASE)

        @JvmStatic
        fun parseBridges(bridges: String): List<Bridge> {
            return bridges
                .split("\n")
                .mapNotNull {
                    val b = it.trim()
                    if (b.isNotEmpty()) Bridge(b) else null
                }
        }

        @JvmStatic
        fun getTransports(bridges: List<Bridge>): Set<String> {
            return bridges.mapNotNull { it.transport }.toSet()
        }
    }
}

object BridgeAsStringSerializer : KSerializer<Bridge> {
    override val descriptor = PrimitiveSerialDescriptor(Bridge::class.java.canonicalName!!,
        PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Bridge) {
        encoder.encodeString(value.raw)
    }

    override fun deserialize(decoder: Decoder): Bridge {
        return Bridge(decoder.decodeString())
    }
}