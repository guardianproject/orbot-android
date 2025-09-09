package org.torproject.android.service;

public class TProxyService {
    public static native void TProxyStartService(String config_path, int fd);
    public static native void TProxyStopService();
    public static native long[] TProxyGetStats();

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public static final String IPV4 = "192.168.0.1";
    public static final int TASK_SIZE = 81920;
    public static final int TUNNEL_MTU = 8500;
}
