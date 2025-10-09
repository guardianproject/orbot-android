package org.torproject.android.service.tor

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.db.OnionServiceColumns
import org.torproject.android.service.db.V3ClientAuthColumns
import org.torproject.android.service.util.NetworkUtils
import org.torproject.android.service.util.Prefs
import java.io.File

object TorConfig {

    @JvmStatic
    @Suppress("NullableBooleanElvis")
    fun build(context: ContextWrapper, geoIpFile: File, geoIp6File: File): String {
        val conf = mutableListOf(
            "RunAsDaemon 0",
            "AvoidDiskWrites 1")

        val prefs = Prefs.getSharedPrefs(context)

        val socksPortPref = getPort(prefs?.getString(OrbotConstants.PREF_SOCKS, null) ?: OrbotConstants.SOCKS_PROXY_PORT_DEFAULT)
        val httpPortPref = getPort(prefs?.getString(OrbotConstants.PREF_HTTP, null) ?: OrbotConstants.HTTP_PROXY_PORT_DEFAULT)

        val isolate = getIsolation(prefs)
        val ipv6Pref = getIpv6(prefs)

        if (Prefs.openProxyOnAllInterfaces()) {
            conf.add("SOCKSPort 0.0.0.0:$socksPortPref $ipv6Pref $isolate")
            conf.add("SocksPolicy accept *:*")
        } else {
            conf.add("SOCKSPort $socksPortPref $ipv6Pref $isolate")
        }

        conf.add("SafeSocks 0")
        conf.add("TestSocks 0")
        conf.add("HTTPTunnelPort $httpPortPref $isolate")

        if (prefs?.getBoolean(OrbotConstants.PREF_CONNECTION_PADDING, false) ?: false) {
            conf.add("ConnectionPadding 1")
        }

        if (prefs?.getBoolean(OrbotConstants.PREF_REDUCED_CONNECTION_PADDING, true) ?: true) {
            conf.add("ReducedConnectionPadding 1")
        }

        val circuitPadding = prefs?.getBoolean(OrbotConstants.PREF_CIRCUIT_PADDING, true) ?: true
        conf.add("CircuitPadding ${if (circuitPadding) "1" else "0"}")

        if (prefs?.getBoolean(OrbotConstants.PREF_REDUCED_CIRCUIT_PADDING, true) ?: true) {
            conf.add("ReducedCircuitPadding 1")
        }

        val transPort = prefs?.getString(OrbotConstants.PREF_TRANSPORT, null) ?: OrbotConstants.TOR_TRANSPROXY_PORT_DEFAULT.toString()
        val dnsPort = prefs?.getString(OrbotConstants.PREF_DNSPORT, null) ?: OrbotConstants.TOR_DNS_PORT_DEFAULT.toString()

        conf.add("TransPort ${NetworkUtils.checkPortOrAuto(transPort)} $isolate")
        conf.add("DNSPort ${NetworkUtils.checkPortOrAuto(dnsPort)} $isolate")
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

        val entryNodes = prefs?.getString("pref_entrance_nodes", null)
        if (!entryNodes.isNullOrEmpty()) conf.add("EntryNodes $entryNodes")

        val exitNodes = prefs?.getString("pref_exit_nodes", null)
        if (!exitNodes.isNullOrEmpty()) conf.add("ExitNodes $exitNodes")

        val excludeNodes = prefs?.getString("pref_exclude_nodes", null)
        if (!excludeNodes.isNullOrEmpty()) conf.add("ExcludeNodes $excludeNodes")

        val enableStrictNodes = prefs?.getBoolean("pref_strict_nodes", false) ?: false
        conf.add("StrictNodes ${if (enableStrictNodes) "1" else "0"}")

        if (prefs?.getBoolean(OrbotConstants.PREF_REACHABLE_ADDRESSES, false) ?: false) {
            val reachableAddressesPorts = prefs.getString(OrbotConstants.PREF_REACHABLE_ADDRESSES_PORTS, null) ?: "*:80,*:443"
            conf.add("ReachableAddresses $reachableAddressesPorts")
        }

        if (Prefs.hostOnionServicesEnabled()) {
            val extraLines = StringBuffer()

            // Add any needed client authorization and hosted onion service config lines to torrc.
            V3ClientAuthColumns.addClientAuthToTorrc(extraLines, context,
                V3ClientAuthColumns.createV3AuthDir(context))
            OnionServiceColumns.addV3OnionServicesToTorrc(extraLines, context,
                OnionServiceColumns.createV3OnionDir(context))

            conf.addAll(extraLines.split("\n"))
        }

        val custom = prefs?.getString("pref_custom_torrc", null)
        if (!custom.isNullOrEmpty()) conf.add(custom)

        return conf.joinToString("\n")
    }


    private fun getPort(port: String): String {
        var port = port

        if (port.indexOf(':') != -1) port = port.split(":").toTypedArray()[1]

        return NetworkUtils.checkPortOrAuto(port)
    }

    @Suppress("NullableBooleanElvis")
    private fun getIsolation(prefs: SharedPreferences?): String {
        val isolate = mutableListOf<String>()

        if (prefs?.getBoolean(OrbotConstants.PREF_ISOLATE_DEST, false) ?: false) {
            isolate.add("IsolateDestAddr")
        }
        if (prefs?.getBoolean(OrbotConstants.PREF_ISOLATE_PORT, false) ?: false) {
            isolate.add("IsolateDestPort")
        }
        if (prefs?.getBoolean(OrbotConstants.PREF_ISOLATE_PROTOCOL, false) ?: false) {
            isolate.add("IsolateClientProtocol")
        }
        if (prefs?.getBoolean(OrbotConstants.PREF_ISOLATE_KEEP_ALIVE, false) ?: false) {
            isolate.add("KeepAliveIsolateSOCKSAuth")
        }

        return isolate.joinToString(" ")
    }

    @Suppress("NullableBooleanElvis")
    private fun getIpv6(prefs: SharedPreferences?): String {
        val ipv6Pref = mutableSetOf<String>()

        if (prefs?.getBoolean(OrbotConstants.PREF_PREFER_IPV6, true) ?: true) {
            ipv6Pref.add("IPv6Traffic")
            ipv6Pref.add("PreferIPv6")
        }

        if (prefs?.getBoolean(OrbotConstants.PREF_DISABLE_IPV4, false) ?: false) {
            ipv6Pref.add("IPv6Traffic")
            ipv6Pref.add("NoIPv4Traffic")
        }

        return ipv6Pref.joinToString(" ")
    }
}
