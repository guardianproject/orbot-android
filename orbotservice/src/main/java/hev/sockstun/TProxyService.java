package hev.sockstun;

public class TProxyService {

    private static native void TProxyStartService(String config_path, int fd);
    private static native void TProxyStopService();
    private static native long[] TProxyGetStats();
}
