package org.torproject.android.service.circumvention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeTest {

    @Test
    fun transportBridgeWithoutFingerprint() {
        val bridge = Bridge("obfs4 192.0.2.5:443")

        assertEquals("obfs4", bridge.transport)
        assertEquals("192.0.2.5:443", bridge.address)
        assertEquals("192.0.2.5", bridge.ip)
        assertEquals(443, bridge.port)
        assertNull(bridge.fingerprint1)
    }

    @Test
    fun transportBridgeWithFingerprint() {
        val bridge = Bridge(
            "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D " +
                    "cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg iat-mode=0"
        )

        assertEquals("obfs4", bridge.transport)
        assertEquals("37.218.245.14:38224", bridge.address)
        assertEquals("37.218.245.14", bridge.ip)
        assertEquals(38224, bridge.port)
        assertEquals("D9A82D2F9C2F65A18407B1D2B764F130847F8B5D", bridge.fingerprint1)
        assertEquals("bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg", bridge.cert)
        assertEquals("0", bridge.iatMode)
    }

    @Test
    fun vanillaBridgeWithoutFingerprint() {
        val bridge = Bridge("192.0.2.5:443")

        assertNull(bridge.transport)
        assertEquals("192.0.2.5:443", bridge.address)
        assertEquals("192.0.2.5", bridge.ip)
        assertEquals(443, bridge.port)
        assertNull(bridge.fingerprint1)
    }

    @Test
    fun vanillaBridgeWithFingerprint() {
        val bridge = Bridge("192.0.2.5:443 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D")

        assertNull(bridge.transport)
        assertEquals("192.0.2.5:443", bridge.address)
        assertEquals("D9A82D2F9C2F65A18407B1D2B764F130847F8B5D", bridge.fingerprint1)
    }

    @Test
    fun vanillaBridgeWithIpv6Address() {
        val bridge = Bridge("[2001:db8::1]:443")

        assertNull(bridge.transport)
        assertEquals("[2001:db8::1]:443", bridge.address)
        assertEquals("[2001:db8::1]", bridge.ip)
        assertEquals(443, bridge.port)
    }

    @Test
    fun transportBridgeWithIpv6AddressAndFingerprint() {
        val bridge = Bridge("obfs4 [2001:db8::1]:443 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D")

        assertEquals("obfs4", bridge.transport)
        assertEquals("[2001:db8::1]", bridge.ip)
        assertEquals(443, bridge.port)
        assertEquals("D9A82D2F9C2F65A18407B1D2B764F130847F8B5D", bridge.fingerprint1)
    }

    @Test
    fun snowflakeBridgeParsesFingerprintAndFronts() {
        val bridge = Bridge(
            "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 " +
                    "fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 " +
                    "url=https://1098762253.rsc.cdn77.org/ " +
                    "fronts=app.datapacket.com,www.datapacket.com"
        )

        assertEquals("snowflake", bridge.transport)
        assertEquals("2B280B23E1107BB62ABFC40DDCC8824814F80A72", bridge.fingerprint1)
        assertEquals("2B280B23E1107BB62ABFC40DDCC8824814F80A72", bridge.fingerprint2)
        assertEquals("https://1098762253.rsc.cdn77.org/", bridge.url)
        assertEquals(listOf("app.datapacket.com", "www.datapacket.com"), bridge.fronts)
    }

    @Test
    fun meekLiteBridgeParsesUrlAndFront() {
        val bridge = Bridge(
            "meek_lite 192.0.2.20:80 url=https://1603026938.rsc.cdn77.org " +
                    "front=www.phpmyadmin.net utls=HelloRandomizedALPN"
        )

        assertEquals("meek_lite", bridge.transport)
        assertNull(bridge.fingerprint1)
        assertEquals("https://1603026938.rsc.cdn77.org", bridge.url)
        assertEquals("www.phpmyadmin.net", bridge.front)
        assertEquals("HelloRandomizedALPN", bridge.utls)
    }

    @Test
    fun webtunnelBridgeParsesUrlAndVersion() {
        val bridge = Bridge(
            "webtunnel 192.0.2.8:443 A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2 " +
                    "url=https://example.com/tunnelpath ver=0.0.1"
        )

        assertEquals("webtunnel", bridge.transport)
        assertEquals("A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2", bridge.fingerprint1)
        assertEquals("https://example.com/tunnelpath", bridge.url)
        assertEquals("0.0.1", bridge.ver)
    }

    @Test
    fun dnsttBridgeParsesDohPubkeyAndDomain() {
        val bridge = Bridge(
            "dnstt 192.0.2.4:1 A998F319ADB60EE344540EC4B21524CC484F96BE " +
                    "doh=https://dns.google/dns-query " +
                    "pubkey=241169008830694749fe96bb070c4855c5bb5b9c47b3833ed7d88521ba30a43f " +
                    "domain=t.ruhnama.net"
        )

        assertEquals("dnstt", bridge.transport)
        assertEquals("A998F319ADB60EE344540EC4B21524CC484F96BE", bridge.fingerprint1)
        assertEquals("https://dns.google/dns-query", bridge.doh)
        assertEquals("241169008830694749fe96bb070c4855c5bb5b9c47b3833ed7d88521ba30a43f", bridge.pubkey)
        assertEquals("t.ruhnama.net", bridge.domain)
    }

    @Test
    fun malformedFingerprintIsIgnored() {
        val bridge = Bridge("obfs4 192.0.2.5:443 not-a-valid-fingerprint")

        assertEquals("obfs4", bridge.transport)
        assertEquals("192.0.2.5:443", bridge.address)
        assertNull(bridge.fingerprint1)
    }

    @Test
    fun tooShortFingerprintIsIgnored() {
        val bridge = Bridge("obfs4 192.0.2.5:443 D9A82D2F9C2F65A18407B1D2B764F130847F8B5")

        assertNull(bridge.fingerprint1)
    }

    @Test
    fun emptyRawBridgeHasNoFields() {
        val bridge = Bridge("")

        assertNull(bridge.transport)
        assertNull(bridge.address)
        assertNull(bridge.ip)
        assertNull(bridge.port)
    }

    @Test(expected = NumberFormatException::class)
    fun vanillaAddressWithoutPortThrowsOnPort() {
        Bridge("192.0.2.5").port
    }
}
