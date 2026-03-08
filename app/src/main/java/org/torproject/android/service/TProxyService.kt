@file:Suppress("FunctionName", "FunctionName")

package org.torproject.android.service

@Suppress("LocalVariableName")
object TProxyService {
    @JvmStatic
    external fun TProxyStartService(config_path: String?, fd: Int)
    @JvmStatic
    external fun TProxyStopService()
    external fun TProxyGetStats(): LongArray?

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    const val VIRTUAL_GATEWAY_IPV4: String = "198.18.0.1"
    const val VIRTUAL_GATEWAY_IPV6: String = "fc00::1"
    const val FAKE_DNS: String = "198.18.0.2"

    const val TASK_SIZE: Int = 81920
    const val TUNNEL_MTU: Int = 8500
}
