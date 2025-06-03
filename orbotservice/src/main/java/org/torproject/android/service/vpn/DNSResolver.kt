package org.torproject.android.service.vpn

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.util.Log

class DNSResolver(private val mPort: Int) {
    private var mLocalhost: InetAddress? = null

    @Throws(IOException::class)
    fun processDNS(payload: ByteArray): ByteArray? {
        if (mLocalhost == null) mLocalhost = InetAddress.getLocalHost()

        var packet = DatagramPacket(payload, payload.size, mLocalhost, mPort)
        val datagramSocket = DatagramSocket()
        Log.d("bim", "sending DNS packet:$payload")
        datagramSocket.send(packet)

        // Await response from DNS server
        val buf = ByteArray(1024)
        packet = DatagramPacket(buf, buf.size)
        datagramSocket.receive(packet)
        Log.d("bim", "got DNS resp $buf")

        datagramSocket.close()

        return packet.data
    }
}
