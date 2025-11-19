package org.torproject.jni;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import net.freehaven.tor.control.RawEventListener;
import net.freehaven.tor.control.TorControlCommands;
import net.freehaven.tor.control.TorControlConnection;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * A {@link Service} that runs Tor.  To control Tor via the {@code ControlPort},
 * first bind to this using {@link #bindService(Intent, android.content.ServiceConnection, int)},
 * then use {@link #getTorControlConnection()} to get an instance of
 * {@link TorControlConnection} from {@code jtorctl}.  If
 * {@link TorControlCommands#EVENT_CIRCUIT_STATUS} is not included in
 * {@link TorControlConnection#setEvents(java.util.List)}, then this service
 * will not be able to function properly since it relies on those events to
 * detect the state of Tor.
 */
@SuppressWarnings("deprecation")
public class TorService extends Service {

    public static final String TAG = "TorService";

    /**
     * Hide BuildConfig symbol from javadoc
     * @hidden
     */
    @SuppressWarnings("unused")
    public static final String VERSION_NAME = BuildConfig.VERSION_NAME;

    /**
     * Request to transparently start Tor services.
     */
    @SuppressWarnings("unused")
    public static final String ACTION_START = "org.torproject.android.intent.action.START";

    /**
     * Internal request to stop Tor services.
     */
    @SuppressWarnings("unused")
    private static final String ACTION_STOP = "org.torproject.android.intent.action.STOP";

    /**
     * {@link Intent} sent by this app with {@code ON/OFF/STARTING/STOPPING} status
     * included as an {@link #EXTRA_STATUS} {@code String}.  Your app should
     * always receive {@code ACTION_STATUS Intent}s since any other app could
     * start Orbot.  Also, user-triggered starts and stops will also cause
     * {@code ACTION_STATUS Intent}s to be broadcast.
     */
    public static final String ACTION_STATUS = "org.torproject.android.intent.action.STATUS";

    public static final String ACTION_ERROR = "org.torproject.android.intent.action.ERROR";

    /**
     * {@code String} that contains a status constant: {@link #STATUS_ON},
     * {@link #STATUS_OFF}, {@link #STATUS_STARTING}, or
     * {@link #STATUS_STOPPING}
     */
    public static final String EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS";

    /**
     * A {@link String} {@code packageName} for {@code TorService} to direct its
     * status reply to, after receiving an {@link #ACTION_START},
     * {@link #ACTION_STOP}, or {@link #ACTION_STATUS} {@link Intent}. This allows
     * {@code TorService} to send redundant replies to that single app, rather than
     * broadcasting to all apps after every request.
     */
    @SuppressWarnings("unused")
    public final static String EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME";

    /**
     * The {@link String} {@code packageName} of the app to which this {@code TorService} belongs.
     * This allows broadcast receivers to distinguish between broadcasts from different apps that
     * use {@code TorService}.
     */
    public final static String EXTRA_SERVICE_PACKAGE_NAME = "org.torproject.android.intent.extra.SERVICE_PACKAGE_NAME";

    /**
     * All tor-related services and daemons are stopped
     */
    public static final String STATUS_OFF = "OFF";
    /**
     * All tor-related services and daemons have completed starting
     */
    public static final String STATUS_ON = "ON";
    public static final String STATUS_STARTING = "STARTING";
    public static final String STATUS_STOPPING = "STOPPING";

    /**
     * @return a {@link File} pointing to the location of the optional
     * {@code torrc} file.
     * @see <a href="https://www.torproject.org/docs/tor-manual.html#_the_configuration_file_format">Tor configuration file format</a>
     */
    public static File getTorrc(Context context) {
        return new File(getAppTorServiceDir(context), "torrc");
    }

    /**
     * @return a {@link File} pointing to the location of the optional
     * {@code torrc-defaults} file.
     * @see <a href="https://www.torproject.org/docs/tor-manual.html#_the_configuration_file_format">Tor configuration file format</a>
     */
    public static File getDefaultsTorrc(Context context) {
        return new File(getAppTorServiceDir(context), "torrc-defaults");
    }

    public static String getBroadcastPackageName(Context context) {
        if (broadcastPackageName.equals(UNINITIALIZED)) {
            broadcastPackageName = context.getPackageName();
        }
        return broadcastPackageName;
    }

    /**
     * Set the Package Name to send the status broadcasts to, or {@code null}
     * to broadcast to all apps.
     *
     * @param packageName The name of the application package to send the
     *                    status broadcasts to, or null to broadcast to all.
     * @see Intent#setPackage(String)
     */
    public static void setBroadcastPackageName(String packageName) {
        TorService.broadcastPackageName = packageName;
    }

    public static File getControlSocket(Context context) {
        if (controlSocket == null) {
            controlSocket = new File(getAppTorServiceDataDir(context), CONTROL_SOCKET_NAME);
        }
        return controlSocket;
    }

    /**
     * Get the directory that {@link TorService} uses for:
     * <ul>
     * <li>writing {@code ControlPort.txt} // TODO
     * <li>reading {@code torrc} and {@code torrc-defaults}
     * <li>{@code DataDirectory} and {@code CacheDirectory}
     * <li>the debug log file
     * </ul>
     */
    private static File getAppTorServiceDir(Context context) {
        if (appTorServiceDir == null) {
            appTorServiceDir = context.getDataDir();//new File(context.getFilesDir(),TorService.class.getSimpleName());
            appTorServiceDir.mkdir();
        }
        return appTorServiceDir;
    }

    /**
     * Tor stores private, internal data in this directory.
     */
    public static File getAppTorServiceDataDir(Context context) {

       File dir = context.getDataDir();

      //  File dir = new File(getAppTorServiceDir(context), "data");
       // dir.mkdir();
        if (!(dir.setReadable(true, true) && dir.setWritable(true, true) && dir.setExecutable(true, true))) {
            throw new IllegalStateException("Cannot create " + dir);
        }
        return dir;
    }

    static {
        System.loadLibrary("tor");
    }

    volatile static String currentStatus = STATUS_OFF;

    private static File appTorServiceDir = null;
    private static File controlSocket = null;
    public static final String CONTROL_SOCKET_NAME = "ControlSocket";
    private static final String UNINITIALIZED = "UNINITIALIZED";
    private static String broadcastPackageName = UNINITIALIZED;

    public static int socksPort = -1;
    public static int httpTunnelPort = -1;

    // Store the opaque reference as a long (pointer) for the native code
    @SuppressWarnings({"FieldMayBeFinal", "unused"})
    private long torConfiguration = -1;
    @SuppressWarnings({"FieldMayBeFinal"," unused"})
    private int torControlFd = -1;

    private volatile TorControlConnection torControlConnection;

    /**
     * This lock must be acquired before calling createTorConfiguration() and
     * held until mainConfigurationFree() has been called.
     */
   // private static final ReentrantLock runLock = new ReentrantLock();

    private native boolean createTorConfiguration();

    private native void mainConfigurationFree();

    public native static FileDescriptor prepareFileDescriptor(String path);

    private native boolean mainConfigurationSetCommandLine(String[] args);

    private native boolean mainConfigurationSetupControlSocket();

    private native int runMain();


    public class LocalBinder extends Binder {
        public TorService getService() {
            return TorService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        // TODO send broadcastStatus() here?
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        broadcastStatus(this, STATUS_STARTING);
        startTorServiceThread();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sendBroadcastStatusIntent(this);
        return super.onStartCommand(intent, flags, startId);
    }

    private final Thread torThread = new Thread("tor") {
        @Override
        public void run() {
            final Context context = getApplicationContext();
            try {
                createTorConfiguration();
                setDefaultProxyPorts();

                ArrayList<String> lines = new ArrayList<>(Arrays.asList("tor", "--verify-config", // must always be here
                        "--RunAsDaemon", "0",
                        "-f", getTorrc(context).getAbsolutePath(),
                        "--defaults-torrc", getDefaultsTorrc(context).getAbsolutePath(),
                        "--ignore-missing-torrc",
                        "--SyslogIdentityTag", TAG,
                        "--CacheDirectory", new File(getCacheDir(), TAG).getAbsolutePath(),
                        "--DataDirectory", getAppTorServiceDataDir(context).getAbsolutePath(),
                        "--ControlSocket", getControlSocket(context).getAbsolutePath(),
                        "--CookieAuthentication", "0",
                        // can be moved to ControlPort messages
                        "--LogMessageDomains", "1",
                        "--TruncateLogFile", "1"
                ));
                String[] verifyLines = lines.toArray(new String[0]);
                if (!mainConfigurationSetCommandLine(verifyLines)) {
                    throw new IllegalArgumentException("Setting command line failed: " + Arrays.toString(verifyLines));
                }
                int result = runMain(); // run verify
                if (result != 0) {
                    throw new IllegalArgumentException("Bad command flags: " + Arrays.toString(verifyLines));
                }

                /**
                controlPortThreadStarted = new CountDownLatch(1);
                controlPortThread.start();
                controlPortThreadStarted.await();**/

                String[] runLines = new String[lines.size() - 1];
                runLines[0] = "tor";
                for (int i = 2; i < lines.size(); i++) {
                    runLines[i - 1] = lines.get(i);
                }
                if (!mainConfigurationSetCommandLine(runLines)) {
                    throw new IllegalArgumentException("Setting command line failed: " + Arrays.toString(runLines));
                }
                if (!mainConfigurationSetupControlSocket()) {
                    throw new IllegalStateException("Setting up ControlPort failed!");
                }
                if (runMain() != 0) {
                    throw new IllegalStateException("Tor could not start!");
                }

            } catch (IllegalStateException | IllegalArgumentException e) {
                e.printStackTrace();
                broadcastError(context, e);
            } finally {
                broadcastStatus(context, STATUS_STOPPING);
                mainConfigurationFree();
                TorService.this.stopSelf();
            }
        }
    };

    private void setDefaultProxyPorts() {
        String socksPort = "auto";
        if (isPortAvailable(9050)) {
            socksPort = Integer.toString(9050);
        }
        String httpTunnelPort = "auto";
        if (isPortAvailable(8118)) {
            httpTunnelPort = Integer.toString(8118);
        }

        String defaults = "SOCKSPort " + socksPort;
        defaults += "\nHTTPTunnelPort " + httpTunnelPort + "\n";

        try {
            PrintWriter pw = new PrintWriter(new FileWriter(getDefaultsTorrc(this), false));
            pw.append(defaults);
            pw.flush();
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getPortFromGetInfo(String key) {
        final String value = getInfo(key);
        if (value.trim().isEmpty()) return 0; // port is disabled
        return Integer.parseInt(value.substring(value.lastIndexOf(':') + 1, value.length() - 1));
    }

    /**
     * Start Tor in a {@link Thread} with the minimum required config.  The
     * rest of the config should happen via the Control Port.  First Tor
     * runs with {@code --verify-config} to check the command line flags and
     * any {@code torrc} config.  If they are correct, then this waits for the
     * {@link #} to start so it is running before Tor could
     * potentially create the {@code ControlSocket}.  Then finally Tor is
     * started in its own {@code Thread}.
     * <p>
     * Tor daemon does not output early debug messages to logcat, only after it
     * tries to connect to the ports.  So it is important that Tor does not run
     * into port conflicts when first starting.
     *
     * @see <a href="https://trac.torproject.org/projects/tor/ticket/32036">#32036  output debug logs to logcat as early as possible on Android</a>
     * @see <a href="https://github.com/torproject/tor/blob/40be20d542a83359ea480bbaa28380b4137c88b2/src/app/config/config.c#L4730">options that must be on the command line</a>
     */
    private void startTorServiceThread() {

        Log.i(TAG, "Acquired lock");
        torThread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (torControlConnection != null) {
       //     torControlConnection.removeRawEventListener(startedEventListener);
        }

        if (controlSocket != null)
            controlSocket.delete();


        shutdownTor();
        broadcastStatus(TorService.this, STATUS_OFF);
    }

    public int getSocksPort() {
        return socksPort;
    }

    public int getHttpTunnelPort() {
        return httpTunnelPort;
    }

    /**
     * @return the value or null on error
     * @see <a href="https://gitweb.torproject.org/torspec.git/tree/control-spec.txt?id=bf318ccb042757cc47e47e19a63d1d825dcf222b#n527">control-spec GETINFO</a>
     */
    public String getInfo(String key) {
        try {
            return torControlConnection.getInfo(key);
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Send a signal to the Tor process to shut it down or halt it.
     * Does not wait for a response, or report errors.
     *
     * @see TorControlConnection#shutdownTor(String)
     */
    private void shutdownTor() {
        try {
            if (torControlConnection != null) {
                torControlConnection.shutdownTor(TorControlCommands.SIGNAL_SHUTDOWN);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get an instance of {@link TorControlConnection} to control this instance
     * of Tor, and configure it to set events.
     *
     * @see TorControlConnection#setEvents(java.util.List)
     */
    public TorControlConnection getTorControlConnection() {
        return torControlConnection;
    }

    /**
     * Broadcasts the current status to any apps following the status of TorService.
     */
    static void sendBroadcastStatusIntent(Context context) {
        Intent intent = getBroadcastIntent(context, currentStatus);
        context.sendBroadcast(intent);
    }

    /**
     * Sends the current status both internally and for any apps that need to
     * follow the status of TorService.
     */
    static void broadcastStatus(Context context, String currentStatus) {
        TorService.currentStatus = currentStatus;
        Intent intent = getBroadcastIntent(context, currentStatus);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        context.sendBroadcast(intent);
    }

    /**
     * This might be better handled by {@link android.content.ServiceConnection}
     * but there is no way to write tests for {@code ServiceConnection}.
     */
    static void broadcastError(Context context, Throwable e) {
        Intent intent = new Intent(ACTION_ERROR);
        if (e != null) {
            intent.putExtra(Intent.EXTRA_TEXT, e.getLocalizedMessage());
        }
        intent.setPackage(getBroadcastPackageName(context));
        intent.putExtra(EXTRA_SERVICE_PACKAGE_NAME, context.getPackageName());
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        context.sendBroadcast(intent);
    }

    private static Intent getBroadcastIntent(Context context, String currentStatus) {
        Intent intent = new Intent(TorService.ACTION_STATUS);
        intent.putExtra(EXTRA_SERVICE_PACKAGE_NAME, context.getPackageName());
        intent.setPackage(getBroadcastPackageName(context));
        intent.putExtra(EXTRA_STATUS, currentStatus);
        return intent;
    }

    private static boolean isPortAvailable(int port) {
        try {
            (new ServerSocket(port)).close();
            return true;
        } catch (IOException e) {
            // Could not connect.
            return false;
        }
    }
}
