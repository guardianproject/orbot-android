/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.torproject.android.service.vpn;

import static org.torproject.android.service.OrbotConstants.*;
import static org.torproject.jni.TorService.ACTION_STOP;

import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.torproject.android.service.OrbotService;
import org.torproject.android.service.TProxyService;
import org.torproject.android.service.Notifications;
import org.torproject.android.util.Prefs;
import org.torproject.jni.TorService;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class OrbotVpnManager implements Handler.Callback {
    private static final String TAG = "OrbotVpnManager";
    boolean isStarted = false;
    private ParcelFileDescriptor mInterface;
    private int mTorSocks = -1;
    private final VpnService mService;

    private FileInputStream fis;
    private DataOutputStream fos;

    private static final int DELAY_FD_LISTEN_MS = 5000;

    public OrbotVpnManager(OrbotService service) {
        mService = service;
    }

    public void handleIntent(VpnService.Builder builder, Intent intent) {
        if (intent == null) return;
        var action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case TorService.ACTION_START -> {
                Log.d(TAG, "starting VPN");
                isStarted = true;
            }
            case ACTION_STOP -> {
                isStarted = false;
                Log.d(TAG, "stopping VPN");
                stopVPN();

                //reset ports
                mTorSocks = -1;
            }
            case LOCAL_ACTION_PORTS -> {
                Log.d(TAG, "setting VPN ports");
                var torSocks = intent.getIntExtra(EXTRA_SOCKS_PROXY_PORT, -1);

                //if running, we need to restart
                if ((torSocks != -1 && torSocks != mTorSocks)) {
                    mTorSocks = torSocks;
                    setupTun2Socks(builder);
                }
            }
        }
    }

    public void restartVPN(VpnService.Builder builder) {
        stopVPN();
        setupTun2Socks(builder);
    }

    private void stopVPN() {
        if (mInterface == null) return;
        try {
            Log.d(TAG, "closing interface, destroying VPN interface");
            TProxyService.TProxyStopService();
            if (fis != null) {
                fis.close();
                fis = null;
            }

            if (fos != null) {
                fos.close();
                fos = null;
            }

            mInterface.close();
            mInterface = null;
        } catch (Exception | Error e) {
            Log.d(TAG, "error stopping tun2socks", e);
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message message) {
        Toast.makeText(mService, message.what, Toast.LENGTH_SHORT).show();
        return true;
    }

    private synchronized void setupTun2Socks(final VpnService.Builder builder) {
        try {
            builder.addAddress(TProxyService.VIRTUAL_GATEWAY_IPV4, 32)
                    .setMtu(TProxyService.TUNNEL_MTU)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(TProxyService.FAKE_DNS)
                    .setSession(Notifications.getVpnSessionName(mService))
                    //handle ipv6
                    .addAddress(TProxyService.VIRTUAL_GATEWAY_IPV6, 128)
                    .addRoute("::", 0);

            /*
             * Can't use this since our HTTP proxy is only CONNECT and not a full proxy
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy("localhost",mTorHttp));
            }**/

            doAppBasedRouting(builder);

            // https://developer.android.com/reference/android/net/VpnService.Builder#setMetered(boolean)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);

                // Explicitly allow both families, so we do not block
                // traffic for ones without DNS servers (issue 129).
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
            }

            mInterface = builder.establish();

            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                try {
                    startListeningToFD();
                } catch (IOException e) {
                    Log.d(TAG, "VPN tun listening has stopped", e);
                }
            }, DELAY_FD_LISTEN_MS);

        } catch (Exception e) {
            Log.d(TAG, "VPN tun setup has stopped", e);
        }
    }

    public File getHevSocksTunnelConfFile() throws IOException {
        var file = new File(mService.getCacheDir(), "tproxy.conf");
        //noinspection ResultOfMethodCallIgnored
        file.createNewFile();
        var fos = new FileOutputStream(file, false);

        var tproxy_conf = "misc:\n" +
//                "  log-file: /data/data/org.torproject.android.debug/cache/hev.log \n" +
                "  log-level: warn\n" + // set to "debug" when debugging
                "  task-stack-size: " + TProxyService.TASK_SIZE + "\n" +
                "tunnel:\n" +
                "  ipv4: " + TProxyService.VIRTUAL_GATEWAY_IPV4 + "\n" +
                "  ipv6: '" + TProxyService.VIRTUAL_GATEWAY_IPV6 + "'\n" +
                "  mtu: " + TProxyService.TUNNEL_MTU + "\n" +
                "socks5:\n" +
                "  port: " + mTorSocks + "\n" +
                "  address: 127.0.0.1\n" +
                "  udp: 'udp'\n" +
                "mapdns:\n" +
                "  address: " + TProxyService.FAKE_DNS + "\n" +
                "  port: 53\n" +
                "  network: 240.0.0.0\n" +
                "  netmask: 240.0.0.0\n" +
                "  cache-size: 10000\n";

        // TODO handle socks username and password here

        Log.d(TAG, tproxy_conf);

        fos.write(tproxy_conf.getBytes());
        fos.close();
        return file;
    }

    private void startListeningToFD() throws IOException {
        if (mInterface == null) return;
        fis = new FileInputStream(mInterface.getFileDescriptor());
        fos = new DataOutputStream(new FileOutputStream(mInterface.getFileDescriptor()));

        File conf = getHevSocksTunnelConfFile();

        TProxyService.TProxyStartService(conf.getAbsolutePath(), mInterface.getFd());
    }

    private void doAppBasedRouting(VpnService.Builder builder) throws NameNotFoundException {
        var apps = TorifiedApp.Companion.getApps(mService);
        var individualAppsWereSelected = false;
        var isLockdownMode = isVpnLockdown(mService);

        for (TorifiedApp app : apps) {
            if (app.isTorified() && (!app.getPackageName().equals(mService.getPackageName()))) {
                if (Prefs.isAppTorified(app.getPackageName())) {
                    builder.addAllowedApplication(app.getPackageName());
                }
                individualAppsWereSelected = true;
            }
        }
        Log.i(TAG, "App based routing is enabled?=" + individualAppsWereSelected + ", isLockdownMode=" + isLockdownMode);

        if (isLockdownMode) {
             /* TODO https://github.com/guardianproject/orbot/issues/774
                Need to allow briar, onionshare, etc to enter orbot's vpn gateway, but not enter the tor
                network, that way these apps can use their own tor connection
                 // TODO  "add" these packages here...
                 */
        }

        if (!individualAppsWereSelected && !isLockdownMode) {
            // disallow orbot itself...
            builder.addDisallowedApplication(mService.getPackageName());

            // disallow tor apps to avoid tor over tor, Orbot doesn't need to concern itself with them
            for (String packageName : BYPASS_VPN_PACKAGES)
                builder.addDisallowedApplication(packageName);
        }
    }

    /**
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    public boolean isStarted() {
        return isStarted;
    }

    private boolean isVpnLockdown(final VpnService vpn) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return vpn.isLockdownEnabled();
        } else {
            return false;
        }
    }
}
