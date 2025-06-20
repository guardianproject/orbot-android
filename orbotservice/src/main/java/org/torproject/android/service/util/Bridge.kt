package org.torproject.android.service.util

/**
 * Parser for bridge lines.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class Bridge(var raw: String) {

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
