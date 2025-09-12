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

import android.content.Intent;
import android.content.SharedPreferences;
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

import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.UdpPort;
import org.torproject.android.service.OrbotService;
import org.torproject.android.service.ui.Notifications;
import org.torproject.android.service.util.Prefs;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import IPtProxy.IPtProxy;
import IPtProxy.PacketFlow;

public class OrbotVpnManager implements Handler.Callback {
    private static final String TAG = "OrbotVpnManager";
    boolean isStarted = false;
    private ParcelFileDescriptor mInterface;
    private int mTorSocks = -1;
    private int mTorDns = -1;
    private final VpnService mService;
    private final SharedPreferences prefs;
    private DNSResolver mDnsResolver;

    private final ExecutorService mExec = Executors.newFixedThreadPool(10);
    private Thread mThreadPacket;
    private boolean keepRunningPacket = false;

    private FileInputStream fis;
    private DataOutputStream fos;

    private static final int DELAY_FD_LISTEN_MS = 5000;

    public OrbotVpnManager(OrbotService service) {
        mService = service;
        prefs = Prefs.getSharedPrefs(mService.getApplicationContext());
    }

    public void handleIntent(VpnService.Builder builder, Intent intent) {
        if (intent == null) return;
        var action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case ACTION_START -> {
                Log.d(TAG, "starting VPN");
                isStarted = true;
            }
            case ACTION_STOP -> {
                isStarted = false;
                Log.d(TAG, "stopping VPN");
                stopVPN();

                //reset ports
                mTorSocks = -1;
                mTorDns = -1;
            }
            case LOCAL_ACTION_PORTS -> {
                Log.d(TAG, "setting VPN ports");
                int torSocks = intent.getIntExtra(EXTRA_SOCKS_PROXY_PORT, -1);
                int torDns = intent.getIntExtra(EXTRA_DNS_PORT, -1);

                //if running, we need to restart
                if ((torSocks != -1 && torSocks != mTorSocks && torDns != -1 && torDns != mTorDns)) {
                    mTorSocks = torSocks;
                    mTorDns = torDns;
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
        keepRunningPacket = false;

        if (mInterface != null) {
            try {
                Log.d(TAG, "closing interface, destroying VPN interface");
                IPtProxy.stopSocks();
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

        if (mThreadPacket != null && mThreadPacket.isAlive()) {
            mThreadPacket.interrupt();
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message message) {
        Toast.makeText(mService, message.what, Toast.LENGTH_SHORT).show();
        return true;
    }

    public final static String FAKE_DNS = "10.0.0.1";

    private synchronized void setupTun2Socks(final VpnService.Builder builder) {
        try {
            final String defaultRoute = "0.0.0.0";
            final String virtualGateway = "192.168.50.1";

            builder.addAddress(virtualGateway, 24)
                    .addRoute(defaultRoute, 0)
                    .addRoute(FAKE_DNS, 32)
                    .addDnsServer(FAKE_DNS) //just setting a value here so DNS is captured by TUN interface
                    .setSession(Notifications.getVpnSessionName(mService));

            //handle ipv6
            builder.addAddress("fdfe:dcba:9876::1", 126);
            builder.addRoute("::", 0);

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

            builder.setBlocking(true);

            mInterface = builder.establish();
            mDnsResolver = new DNSResolver(mTorDns);

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

    private void startListeningToFD() throws IOException {
        if (mInterface == null) return; // Prepare hasn't been called yet

        fis = new FileInputStream(mInterface.getFileDescriptor());
        fos = new DataOutputStream(new FileOutputStream(mInterface.getFileDescriptor()));

        //write packets back out to TUN
        PacketFlow pFlow = packet -> {
            try {
                fos.write(packet);
            } catch (IOException e) {
                Log.e(TAG, "error writing to VPN fd", e);
            }
        };

        IPtProxy.startSocks(pFlow, "127.0.0.1", mTorSocks);

        //read packets from TUN and send to go-tun2socks
        mThreadPacket = new Thread() {
            public void run() {

                var buffer = new byte[32767 * 2]; //64k
                keepRunningPacket = true;
                while (keepRunningPacket) {
                    try {
                        int pLen = fis.read(buffer); // will block on API 21+

                        if (pLen > 0) {
                            var pdata = Arrays.copyOf(buffer, pLen);
                            try {
                                var packet = IpSelector.newPacket(pdata, 0, pdata.length);

                                if (packet instanceof IpPacket ipPacket) {
                                    if (isPacketDNS(ipPacket))
                                        mExec.execute(new RequestPacketHandler(ipPacket, pFlow, mDnsResolver));
                                    else //noinspection StatementWithEmptyBody
                                        if (isPacketICMP(ipPacket)) {
                                            //do nothing, drop!
                                        } else IPtProxy.inputPacket(pdata);
                                }
                            } catch (IllegalRawDataException e) {
                                Log.e(TAG, e.toString());
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "error reading from VPN fd: " + e.getLocalizedMessage());
                    }
                }
            }
        };
        mThreadPacket.start();
    }

    private static boolean isPacketDNS(IpPacket p) {
        if (p.getHeader().getProtocol() == IpNumber.UDP) {
            var up = (UdpPacket) p.getPayload();
            return up.getHeader().getDstPort() == UdpPort.DOMAIN;
        }
        return false;
    }

    private static boolean isPacketICMP(IpPacket p) {
        return (p.getHeader().getProtocol() == IpNumber.ICMPV4 || p.getHeader().getProtocol() == IpNumber.ICMPV6);
    }

    private void doAppBasedRouting(VpnService.Builder builder) throws NameNotFoundException {
        var apps = TorifiedApp.Companion.getApps(mService, prefs);
        var individualAppsWereSelected = false;
        var isLockdownMode = isVpnLockdown(mService);

        for (TorifiedApp app : apps) {
            if (app.isTorified() && (!app.getPackageName().equals(mService.getPackageName()))) {
                if (prefs.getBoolean(app.getPackageName() + APP_TOR_KEY, true)) {
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

            // disallow tor apps to avoid tor over tor, Orbot doesnt need to concern itself with them
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
