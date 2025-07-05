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
        var transport: String,
        var ip: String,
        var port: Int,
        var fingerprint1: String
    ) {

        var fingerprint2: String? = null
        var url: String? = null
        var fronts = mutableSetOf<String>()
        var cert: String? = null
        var iatMode: String? = null
        var ice: String? = null
        var utls: String? = null
        var utlsImitate: String? = null
        var ver: String? = null


        constructor(bridge: Bridge) : this(bridge.transport ?: "", bridge.ip ?: "", bridge.port ?: 0, bridge.fingerprint1 ?: "") {
            if (transport.isEmpty() || ip.isEmpty() || port < 1 ||fingerprint1.isEmpty())
            {
                throw RuntimeException("Tried to create Bridge.Builder with invalid bridge!")
            }

            fingerprint2 = bridge.fingerprint2

            url = bridge.url

            bridge.front?.let { fronts.add(it) }
            bridge.fronts?.let { fronts.addAll(it) }

            cert = bridge.cert
            iatMode = bridge.iatMode
            ice = bridge.ice
            utls = bridge.utls
            utlsImitate = bridge.utlsImitate
            ver = bridge.ver
        }

        fun build(): Bridge {
            var params = mutableListOf(transport, "$ip:$port", fingerprint1)

            fingerprint2?.let {
                if (it.isNotEmpty()) params.add("fingerprint=$it")
            }

            url?.let {
                if (it.isNotEmpty()) params.add("url=$it")
            }

            fronts.filter { it.isNotEmpty() }.let {
                if (it.isNotEmpty()) params.add("fronts=${it.joinToString(",")}")
            }

            cert?.let {
                if (it.isNotEmpty()) params.add("cert=$it")
            }

            iatMode?.let {
                if (it.isNotEmpty()) params.add("iat-mode=$it")
            }

            ice?.let {
                if (it.isNotEmpty()) params.add("ice=$it")
            }

            utls?.let {
                if (it.isNotEmpty()) params.add("utls=$it")
            }

            utlsImitate?.let {
                if (it.isNotEmpty()) params.add("utls-imitate=$it")
            }

            ver?.let {
                if (it.isNotEmpty()) params.add("ver=$it")
            }

            return Bridge(params.joinToString(" "))
        }
    }

    val rawPieces
        get() = raw.split(" ")

    val transport
        get() = rawPieces.firstOrNull()

    val address
        get() = rawPieces.getOrNull(1)

    val ip
        get() = address?.split(":")?.firstOrNull()

    val port
        get() = address?.split(":")?.lastOrNull()?.toInt()

    val fingerprint1
        get() = rawPieces.getOrNull(2)

    val fingerprint2
        get() = rawPieces.firstOrNull { it.startsWith("fingerprint=") }
            ?.split("=")?.lastOrNull()

    val url
        get() = rawPieces.firstOrNull { it.startsWith("url=") }
            ?.split("=")?.lastOrNull()

    val front
        get() = rawPieces.firstOrNull { it.startsWith("front=") }
            ?.split("=")?.lastOrNull()

    val fronts
        get() = rawPieces.firstOrNull { it.startsWith("fronts=") }
            ?.split("=")?.lastOrNull()?.split(",")?.filter { it.isNotEmpty() }

    val cert
        get() = rawPieces.firstOrNull { it.startsWith("cert=") }
            ?.split("=")?.lastOrNull()

    val iatMode
        get() = rawPieces.firstOrNull { it.startsWith("iat-mode=") }
            ?.split("=")?.lastOrNull()

    val ice
        get() = rawPieces.firstOrNull { it.startsWith("ice=") }
            ?.split("=")?.lastOrNull()

    val utls
        get() = rawPieces.firstOrNull { it.startsWith("utls=") }
            ?.split("=")?.lastOrNull()

    val utlsImitate
        get() = rawPieces.firstOrNull { it.startsWith("utls-imitate=") }
            ?.split("=")?.lastOrNull()

    val ver
        get() = rawPieces.firstOrNull { it.startsWith("ver=") }
            ?.split("=")?.lastOrNull()


    override fun toString(): String {
        return raw
    }

    companion object {

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
    override val descriptor = PrimitiveSerialDescriptor(Bridge.javaClass.canonicalName!!,
        PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Bridge) {
        encoder.encodeString(value.raw)
    }

    override fun deserialize(decoder: Decoder): Bridge {
        return Bridge(decoder.decodeString())
    }
}