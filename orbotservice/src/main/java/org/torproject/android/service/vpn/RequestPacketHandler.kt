package org.torproject.android.service.vpn

import android.util.Log
import org.pcap4j.packet.DnsPacket
import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.UdpPacket
import java.io.DataOutputStream
import java.net.Inet6Address

class RequestPacketHandler(
    private val packet: IpPacket,
    private val interfaceOutputStream: DataOutputStream,
    private val mDnsResolver: DNSResolver
) : Runnable {
    override fun run() {
        try {
            val udpPacket = packet.payload as UdpPacket
            val dnsResp = mDnsResolver.processDNS(udpPacket.payload.rawData)

            if (dnsResp != null) {
                val dnsRequest = udpPacket.payload as DnsPacket
                val dnsResponse = DnsPacket.newPacket(dnsResp, 0, dnsResp.size)

                val dnsBuilder = DnsPacket.Builder().apply {
                    questions(dnsRequest.header.questions)
                    id(dnsResponse.header.id)
                    answers(dnsResponse.header.answers)
                    response(dnsResponse.header.isResponse)
                    additionalInfo(dnsResponse.header.additionalInfo)
                    qdCount(dnsResponse.header.qdCount)
                    anCount(dnsResponse.header.anCount)
                    arCount(dnsResponse.header.arCount)
                    opCode(dnsResponse.header.opCode)
                    rCode(dnsResponse.header.getrCode())
                    authenticData(dnsResponse.header.isAuthenticData)
                    authoritativeAnswer(dnsResponse.header.isAuthoritativeAnswer)
                    authorities(dnsResponse.header.authorities)
                }

                val udpBuilder = UdpPacket.Builder(udpPacket).srcPort(udpPacket.header.dstPort)
                    .dstPort(udpPacket.header.srcPort).srcAddr(packet.header.dstAddr)
                    .dstAddr(packet.header.srcAddr).correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true).payloadBuilder(dnsBuilder)

                var respPacket: IpPacket? = null

                if (packet is IpV4Packet) {
                    respPacket = IpV4Packet.Builder()
                        .version(packet.header.version).protocol(packet.header.protocol)
                        .tos(packet.header.tos).srcAddr(packet.header.dstAddr)
                        .dstAddr(packet.header.srcAddr).correctChecksumAtBuild(true)
                        .correctLengthAtBuild(true).dontFragmentFlag(packet.header.dontFragmentFlag)
                        .reservedFlag(packet.header.reservedFlag)
                        .moreFragmentFlag(packet.header.moreFragmentFlag).ttl(64.toByte())
                        .payloadBuilder(udpBuilder)
                        .build()
                } else if (packet is IpV6Packet) {
                    respPacket = IpV6Packet.Builder(packet)
                        .srcAddr(packet.header.dstAddr as Inet6Address)
                        .dstAddr(packet.header.srcAddr as Inet6Address)
                        .payloadBuilder(udpBuilder).build()
                }

                // only IPv4Packet and IPv6Packet implement IPPacket so this is safe
                val rawResponse = respPacket!!.rawData

                interfaceOutputStream.write(rawResponse)
            }
        } catch (ioe: Exception) {
            Log.e("RequestPacketHandler", "could not parse DNS packet: $ioe")
        }
    }
}
