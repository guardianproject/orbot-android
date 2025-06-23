package org.torproject.android.service;

public class TProxyService {
    public static native void TProxyStartService(String config_path, int fd);
    public static native void TProxyStopService();
    public static native long[] TProxyGetStats();

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }
}
