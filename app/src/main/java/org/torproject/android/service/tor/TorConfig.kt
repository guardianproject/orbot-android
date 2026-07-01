package org.torproject.android.service.tor

import android.content.ContextWrapper
import android.util.Log
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.db.OnionServiceColumns
import org.torproject.android.service.db.V3ClientAuthColumns
import org.torproject.android.util.NetworkUtils
import org.torproject.android.util.Prefs
import java.io.File

object TorConfig {

    @JvmStatic
    fun build(context: ContextWrapper, geoIpFile: File, geoIp6File: File): String {
        val conf = mutableListOf(
            "RunAsDaemon 1",
            "AvoidDiskWrites 1"
        )

        val socksPortPref = getPort(Prefs.proxySocksPort ?: OrbotConstants.SOCKS_PROXY_PORT_DEFAULT)
        val httpPortPref = getPort(Prefs.proxyHttpPort ?: OrbotConstants.HTTP_PROXY_PORT_DEFAULT)

        val isolate = getIsolation()
        val ipv6Pref = getIpv6()

        if (Prefs.openProxyOnAllInterfaces()) {
            conf.add("SOCKSPort 0.0.0.0:$socksPortPref $ipv6Pref $isolate")
            conf.add("SocksPolicy accept *:*")
        } else {
            conf.add("SOCKSPort auto $ipv6Pref $isolate")
        }

        conf.add("SafeSocks 0")
        conf.add("TestSocks 0")
        conf.add("HTTPTunnelPort auto $isolate")

        if (Prefs.connectionPadding) {
            conf.add("ConnectionPadding 1")
        }

        if (Prefs.reducedConnectionPadding) {
            conf.add("ReducedConnectionPadding 1")
        }

        conf.add("CircuitPadding ${if (Prefs.circuitPadding) "1" else "0"}")

        if (Prefs.reducedCircuitPadding) {
            conf.add("ReducedCircuitPadding 1")
        }

        val transPort = Prefs.torTransPort ?: OrbotConstants.TOR_TRANSPROXY_PORT_DEFAULT.toString()
        val dnsPort = Prefs.torDnsPort ?: OrbotConstants.TOR_DNS_PORT_DEFAULT.toString()

        conf.add("TransPort auto $isolate")
        conf.add("DNSPort auto $isolate")
        conf.add("VirtualAddrNetwork 10.192.0.0/10")
        conf.add("AutomapHostsOnResolve 1")
        conf.add("DormantClientTimeout 10 minutes")
        conf.add("DormantCanceledByStartup 1")
        conf.add("DisableNetwork 0")

        if (Prefs.useDebugLogging()) {
            conf.add("Log debug syslog")
            conf.add("SafeLogging 0")
        }

        val transport = Prefs.transport

        conf.addAll(transport.getTorConfig(context))

        if (geoIpFile.exists()) { // only apply geoip if it exists
            conf.add("GeoIPFile ${geoIpFile.canonicalPath}")
        }

        if (geoIp6File.exists()) {
            conf.add("GeoIPv6File ${geoIp6File.canonicalPath}")
        }

        // Entry nodes can only be defined, when no PT is used.
        val entryNodes = Prefs.entryNodes
        if (!entryNodes.isNullOrEmpty() && transport == Transport.NONE) conf.add("EntryNodes $entryNodes")

        val exitNodes = Prefs.exitNodes
        if (!exitNodes.isNullOrEmpty()) conf.add("ExitNodes $exitNodes")

        val excludeNodes = Prefs.excludeNodes
        if (!excludeNodes.isNullOrEmpty()) conf.add("ExcludeNodes $excludeNodes")

        conf.add("StrictNodes ${if (Prefs.strictNodes) "1" else "0"}")

        if (Prefs.reachableAddresses) {
            val reachableAddressesPorts = Prefs.reachableAddressesPorts ?: "*:80,*:443"
            conf.add("ReachableAddresses $reachableAddressesPorts")
        }

        // Always add client authorization config if any entries exist.
        val clientAuthLines = StringBuffer()
        V3ClientAuthColumns.addClientAuthToTorrc(
            clientAuthLines, context,
            V3ClientAuthColumns.createV3AuthDir(context)
        )
        conf.addAll(clientAuthLines.split("\n"))

        val extraLines = StringBuffer()
        OnionServiceColumns.addV3OnionServicesToTorrc(
            extraLines, context,
            OnionServiceColumns.createV3OnionDir(context)
        )
        conf.addAll(extraLines.split("\n"))

        val custom = Prefs.customTorRc
        if (!custom.isNullOrEmpty()) conf.add(custom)
        Log.wtf("bim", conf.joinToString("\n"))
        return conf.joinToString("\n")
    }


    private fun getPort(port: String): String {
        var port = port
        if (port.indexOf(':') != -1) port = port.split(":").toTypedArray()[1]

        return NetworkUtils.checkPortOrAuto(port)
    }

    private fun getIsolation(): String {
        val isolate = mutableListOf<String>()

        if (Prefs.isolateDest) {
            isolate.add("IsolateDestAddr")
        }
        if (Prefs.isolatePort) {
            isolate.add("IsolateDestPort")
        }
        if (Prefs.isolateProtocol) {
            isolate.add("IsolateClientProtocol")
        }
        if (Prefs.isolateKeepAlive) {
            isolate.add("KeepAliveIsolateSOCKSAuth")
        }

        return isolate.joinToString(" ")
    }

    private fun getIpv6(): String {
        val ipv6Pref = mutableSetOf<String>()

        if (Prefs.preferIpv6) {
            ipv6Pref.add("IPv6Traffic")
            ipv6Pref.add("PreferIPv6")
        }

        if (Prefs.disableIpv4) {
            ipv6Pref.add("IPv6Traffic")
            ipv6Pref.add("NoIPv4Traffic")
        }

        return ipv6Pref.joinToString(" ")
    }
}
