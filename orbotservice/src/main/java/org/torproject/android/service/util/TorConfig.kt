package org.torproject.android.service.util

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.torproject.android.service.OrbotConstants.HTTP_PROXY_PORT_DEFAULT
import org.torproject.android.service.OrbotConstants.PREF_CIRCUIT_PADDING
import org.torproject.android.service.OrbotConstants.PREF_CONNECTION_PADDING
import org.torproject.android.service.OrbotConstants.PREF_DISABLE_IPV4
import org.torproject.android.service.OrbotConstants.PREF_DNSPORT
import org.torproject.android.service.OrbotConstants.PREF_HTTP
import org.torproject.android.service.OrbotConstants.PREF_ISOLATE_DEST
import org.torproject.android.service.OrbotConstants.PREF_ISOLATE_KEEP_ALIVE
import org.torproject.android.service.OrbotConstants.PREF_ISOLATE_PORT
import org.torproject.android.service.OrbotConstants.PREF_ISOLATE_PROTOCOL
import org.torproject.android.service.OrbotConstants.PREF_PREFER_IPV6
import org.torproject.android.service.OrbotConstants.PREF_REACHABLE_ADDRESSES
import org.torproject.android.service.OrbotConstants.PREF_REACHABLE_ADDRESSES_PORTS
import org.torproject.android.service.OrbotConstants.PREF_REDUCED_CIRCUIT_PADDING
import org.torproject.android.service.OrbotConstants.PREF_REDUCED_CONNECTION_PADDING
import org.torproject.android.service.OrbotConstants.PREF_SOCKS
import org.torproject.android.service.OrbotConstants.PREF_TRANSPORT
import org.torproject.android.service.OrbotConstants.SOCKS_PROXY_PORT_DEFAULT
import org.torproject.android.service.OrbotConstants.TOR_DNS_PORT_DEFAULT
import org.torproject.android.service.OrbotConstants.TOR_TRANSPROXY_PORT_DEFAULT
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.db.OnionServiceColumns
import org.torproject.android.service.db.V3ClientAuthColumns
import java.io.File

object TorConfig {

    @JvmStatic
    @Suppress("NullableBooleanElvis")
    fun build(context: ContextWrapper, geoIpFile: File, geoIp6File: File): String {
        val conf = mutableListOf(
            "RunAsDaemon 0",
            "AvoidDiskWrites 1")

        val prefs = Prefs.getSharedPrefs(context)

        var socksPortPref = getPort(prefs?.getString(PREF_SOCKS, null) ?: SOCKS_PROXY_PORT_DEFAULT)
        var httpPortPref = getPort(prefs?.getString(PREF_HTTP, null) ?: HTTP_PROXY_PORT_DEFAULT)

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

        if (prefs?.getBoolean(PREF_CONNECTION_PADDING, false) ?: false) {
            conf.add("ConnectionPadding 1")
        }

        if (prefs?.getBoolean(PREF_REDUCED_CONNECTION_PADDING, true) ?: true) {
            conf.add("ReducedConnectionPadding 1")
        }

        val circuitPadding = prefs?.getBoolean(PREF_CIRCUIT_PADDING, true) ?: true
        conf.add("CircuitPadding ${if (circuitPadding) "1" else "0"}")

        if (prefs?.getBoolean(PREF_REDUCED_CIRCUIT_PADDING, true) ?: true) {
            conf.add("ReducedCircuitPadding 1")
        }

        val transPort = prefs?.getString(PREF_TRANSPORT, null) ?: TOR_TRANSPROXY_PORT_DEFAULT.toString()
        val dnsPort = prefs?.getString(PREF_DNSPORT, null) ?: TOR_DNS_PORT_DEFAULT.toString()

        conf.add("TransPort ${Utils.checkPortOrAuto(transPort)} $isolate")
        conf.add("DNSPort ${Utils.checkPortOrAuto(dnsPort)} $isolate")
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

        if (transport == Transport.NONE) {
            conf.add("UseBridges 0")

            if (!Prefs.useVpn()) {
                // Set the proxy here if we aren't using a bridge.
                val proxyType = prefs?.getString("pref_proxy_type", null)

                if (!proxyType.isNullOrEmpty()) {
                    val proxyHost = prefs.getString("pref_proxy_host", null)
                    val proxyPort = prefs.getString("pref_proxy_port", null)
                    val proxyUser = prefs.getString("pref_proxy_username", null)
                    val proxyPass = prefs.getString("pref_proxy_password", null)

                    if (!proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()) {
                        conf.add("${proxyType}Proxy $proxyHost:$proxyPort")

                        if (proxyUser != null && proxyPass != null) {
                            if (proxyType.equals("socks5", ignoreCase = true)) {
                                conf.add("Socks5ProxyUsername $proxyUser")
                                conf.add("Socks5ProxyPassword $proxyPass")
                            } else {
                                conf.add("${proxyType}ProxyAuthenticator $proxyUser:$proxyPort")
                            }
                        }
                    }
                }
            }
        }
        else {
            conf.add("UseBridges 1")
            conf.addAll(transport.getTorConfig(context))
        }

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

        if (prefs?.getBoolean(PREF_REACHABLE_ADDRESSES, false) ?: false) {
            val reachableAddressesPorts = prefs.getString(PREF_REACHABLE_ADDRESSES_PORTS, null) ?: "*:80,*:443"
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

        return Utils.checkPortOrAuto(port)
    }

    @Suppress("NullableBooleanElvis")
    private fun getIsolation(prefs: SharedPreferences?): String {
        val isolate = mutableListOf<String>()

        if (prefs?.getBoolean(PREF_ISOLATE_DEST, false) ?: false) {
            isolate.add("IsolateDestAddr")
        }
        if (prefs?.getBoolean(PREF_ISOLATE_PORT, false) ?: false) {
            isolate.add("IsolateDestPort")
        }
        if (prefs?.getBoolean(PREF_ISOLATE_PROTOCOL, false) ?: false) {
            isolate.add("IsolateClientProtocol")
        }
        if (prefs?.getBoolean(PREF_ISOLATE_KEEP_ALIVE, false) ?: false) {
            isolate.add("KeepAliveIsolateSOCKSAuth")
        }

        return isolate.joinToString(" ")
    }

    @Suppress("NullableBooleanElvis")
    private fun getIpv6(prefs: SharedPreferences?): String {
        val ipv6Pref = mutableSetOf<String>()

        if (prefs?.getBoolean(PREF_PREFER_IPV6, true) ?: true) {
            ipv6Pref.add("IPv6Traffic")
            ipv6Pref.add("PreferIPv6")
        }

        if (prefs?.getBoolean(PREF_DISABLE_IPV4, false) ?: false) {
            ipv6Pref.add("IPv6Traffic")
            ipv6Pref.add("NoIPv4Traffic")
        }

        return ipv6Pref.joinToString(" ")
    }
}