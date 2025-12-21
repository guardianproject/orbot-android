package org.torproject.android.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.freehaven.tor.control.RawEventListener;
import net.freehaven.tor.control.TorControlCommands;

import org.torproject.android.R;
import org.torproject.android.util.Prefs;
import org.torproject.android.util.StringUtils;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class OrbotRawEventListener implements RawEventListener {
    private final OrbotService mService;
    private long mTotalBandwidthWritten, mTotalBandwidthRead;
    private final Map<String, DebugLoggingNode> hmBuiltNodes;
    private final Map<Integer, ExitNode> exitNodeMap;
    private final Set<Integer> ignoredInternalCircuits;

    private static final String CIRCUIT_BUILD_FLAG_IS_INTERNAL = "IS_INTERNAL";
    private static final String CIRCUIT_BUILD_FLAG_ONE_HOP_TUNNEL = "ONEHOP_TUNNEL";

    OrbotRawEventListener(OrbotService orbotService) {
        mService = orbotService;
        mTotalBandwidthRead = 0;
        mTotalBandwidthWritten = 0;
        hmBuiltNodes = new HashMap<>();

        exitNodeMap = new HashMap<>();
        ignoredInternalCircuits = new HashSet<>();
    }

    @Override
    public void onEvent(String keyword, String data) {
        String[] payload = data.split(" ");
        switch (keyword) {
            case TorControlCommands.EVENT_BANDWIDTH_USED ->
                    handleBandwidth(Long.parseLong(payload[0]), Long.parseLong(payload[1]));
            case TorControlCommands.EVENT_NEW_DESC -> handleNewDescriptors(payload);
            case TorControlCommands.EVENT_STREAM_STATUS -> {

                handleStreamEventExpandedNotifications(payload[1], payload[3], payload[2], payload[4]);

                if (Prefs.useDebugLogging()) handleStreamEventsDebugLogging(payload[1], payload[0]);
            }
            case TorControlCommands.EVENT_CIRCUIT_STATUS -> {
                String status = payload[1];
                String circuitId = payload[0];
                String path;
                if (payload.length < 3 || status.equals(TorControlCommands.CIRC_EVENT_LAUNCHED))
                    path = "";
                else path = payload[2];
                handleCircuitStatus(status, circuitId, path);

                // don't bother looking up internal circuits that Orbot clients won't directly use
                if (data.contains(CIRCUIT_BUILD_FLAG_ONE_HOP_TUNNEL) || data.contains(CIRCUIT_BUILD_FLAG_IS_INTERNAL)) {
                    ignoredInternalCircuits.add(Integer.parseInt(circuitId));
                }
                handleCircuitStatusExpandedNotifications(status, circuitId, path);
            }
            case TorControlCommands.EVENT_OR_CONN_STATUS ->
                    handleConnectionStatus(payload[1], payload[0]);
            case TorControlCommands.EVENT_DEBUG_MSG, TorControlCommands.EVENT_INFO_MSG,
                 TorControlCommands.EVENT_NOTICE_MSG, TorControlCommands.EVENT_WARN_MSG,
                 TorControlCommands.EVENT_ERR_MSG -> handleDebugMessage(keyword, data);
            case null, default ->  // unrecognized keyword
                    mService.logNotice("Message (" + keyword + "): " + data);
        }
    }

    private static String formatBandwidthCount(Context context, long bitsPerSecond) {
        var nf = NumberFormat.getInstance(Locale.getDefault());
        if (bitsPerSecond < 1e6)
            return nf.format(Math.round(((float) ((int) (bitsPerSecond * 10 / 1024)) / 10))) + context.getString(R.string.kibibyte_per_second);
        else
            return nf.format(Math.round(((float) ((int) (bitsPerSecond * 100 / 1024 / 1024)) / 100))) + context.getString(R.string.mebibyte_per_second);
    }

    private void handleBandwidth(long read, long written) {
        String message = formatBandwidthCount(mService, read) + " ↓ / " + formatBandwidthCount(mService, written) + " ↑";

        if (mService.mCurrentStatus == TorStatus.ON)
            mService.showBandwidthNotification(message, read != 0 || written != 0);

        mTotalBandwidthWritten += written;
        mTotalBandwidthRead += read;
        var bandwidthIntent = new Intent(OrbotConstants.LOCAL_ACTION_BANDWIDTH)
                .putExtra(OrbotConstants.LOCAL_EXTRA_TOTAL_WRITTEN, mTotalBandwidthWritten)
                .putExtra(OrbotConstants.LOCAL_EXTRA_TOTAL_READ, mTotalBandwidthRead)
                .putExtra(OrbotConstants.LOCAL_EXTRA_LAST_WRITTEN, written)
                .putExtra(OrbotConstants.LOCAL_EXTRA_LAST_READ, read);
        LocalBroadcastManager.getInstance(mService).sendBroadcast(bandwidthIntent);
    }

    private void handleNewDescriptors(String[] descriptors) {
        for (String descriptor : descriptors)
            Log.d("Orbot", "descriptors: " + descriptor);
    }

    private void handleStreamEventExpandedNotifications(String status, String target, String circuitId, String clientProtocol) {
        if (!status.equals(TorControlCommands.STREAM_EVENT_SUCCEEDED)) return;
        if (!clientProtocol.contains("SOCKS5")) return;
        var id = Integer.parseInt(circuitId);
        if (target.contains(".onion"))
            return; // don't display to users exit node info for onion addresses!
        var node = exitNodeMap.get(id);
        if (node != null) {
            if (node.country == null && !node.querying) {
                node.querying = true;
                mService.mExecutor.execute(() -> {
                    try {
                        String[] networkStatus = mService.conn.getInfo("ns/id/" + node.fingerPrint).split(" ");
                        node.ipAddress = networkStatus[6];
                        var countryCode = mService.conn.getInfo("ip-to-country/" + node.ipAddress).toUpperCase(Locale.getDefault());
                        if (!countryCode.equals(TOR_CONTROLLER_COUNTRY_CODE_UNKNOWN)) {
                            var emoji = StringUtils.convertCountryCodeToFlagEmoji(countryCode);
                            var countryName = new Locale("", countryCode).getDisplayName();
                            node.country = emoji + " " + countryName;
                        } else node.country = "";
                        mService.setNotificationSubtext(node.toString());
                    } catch (Exception ignored) {
                    }
                });
            } else {
                if (node.country != null) mService.setNotificationSubtext(node.toString());
                else mService.setNotificationSubtext(null);
            }
        }
    }

    private static final String TOR_CONTROLLER_COUNTRY_CODE_UNKNOWN = "??";

    private void handleStreamEventsDebugLogging(String streamId, String status) {
        Log.d("Orbot", "StreamStatus (" + streamId + "): " + status);
    }

    private void handleCircuitStatusExpandedNotifications(String circuitStatus, String circuitId, String path) {
        var id = Integer.parseInt(circuitId);
        switch (circuitStatus) {
            case TorControlCommands.CIRC_EVENT_BUILT -> {
                if (ignoredInternalCircuits.contains(id))
                    return; // this circuit won't be used by user clients
                var nodes = path.split(",");
                var exit = nodes[nodes.length - 1];
                var fingerprint = exit.split("~")[0];
                exitNodeMap.put(id, new ExitNode(fingerprint));
            }
            case TorControlCommands.CIRC_EVENT_CLOSED -> {
                exitNodeMap.remove(id);
                ignoredInternalCircuits.remove(id);
            }
            case TorControlCommands.CIRC_EVENT_FAILED -> ignoredInternalCircuits.remove(id);
        }
    }

    private void handleCircuitStatus(String circuitStatus, String circuitId, String path) {
        if (!Prefs.useDebugLogging()) return;

        var sb = new StringBuilder("Circuit (" + circuitId + ") " + circuitStatus + ": ");
        var st = new StringTokenizer(path, ",");
        DebugLoggingNode node;
        var isFirstNode = true;
        var nodeCount = st.countTokens();

        while (st.hasMoreTokens()) {
            var nodePath = st.nextToken();
            String nodeId = null, nodeName = null;
            String[] nodeParts;

            if (nodePath.contains("=")) nodeParts = nodePath.split("=");
            else nodeParts = nodePath.split("~");

            if (nodeParts.length == 1) {
                nodeId = nodeParts[0].substring(1);
                nodeName = nodeId;
            } else if (nodeParts.length == 2) {
                nodeId = nodeParts[0].substring(1);
                nodeName = nodeParts[1];
            }

            if (nodeId == null) continue;

            node = hmBuiltNodes.get(nodeId);

            if (node == null) {
                node = new DebugLoggingNode();
                node.id = nodeId;
                node.name = nodeName;
            }

            node.status = circuitStatus;

            sb.append(node.name);

            if (st.hasMoreTokens()) sb.append(" > ");

            if (circuitStatus.equals(TorControlCommands.CIRC_EVENT_EXTENDED) && isFirstNode) {
                hmBuiltNodes.put(node.id, node);
                isFirstNode = false;
            } else if (circuitStatus.equals(TorControlCommands.CIRC_EVENT_LAUNCHED)) {
                if (Prefs.useDebugLogging() && nodeCount > 3) Log.d("Orbot", sb.toString());
            } else if (circuitStatus.equals(TorControlCommands.CIRC_EVENT_CLOSED)) {
                hmBuiltNodes.remove(node.id);
            }
        }
    }

    private void handleConnectionStatus(String status, String unparsedNodeName) {
        var message = "orConnStatus (" + parseNodeName(unparsedNodeName) + "): " + status;
        Log.d("Orbot", message);
    }

    private void handleDebugMessage(String severity, String message) {
        if (severity.equalsIgnoreCase("debug")) Log.d("Orbot", severity + ": " + message);
        else mService.logNotice(severity + ": " + message);
    }

    public Map<String, DebugLoggingNode> getNodes() {
        return hmBuiltNodes;
    }

    /**
     * Used to store metadata about an exit node if expanded notifications are turned on
     */
    public static class ExitNode {
        ExitNode(String fingerPrint) {
            this.fingerPrint = fingerPrint;
        }

        public final String fingerPrint;
        public String country;
        public String ipAddress;
        boolean querying = false;

        @NonNull
        @Override
        public String toString() {
            return ipAddress + " " + country;
        }
    }


    public static class DebugLoggingNode {
        public String status;
        public String id;
        public String name;
    }


    private static String parseNodeName(String node) {
        if (node.indexOf('=') != -1) {
            return node.substring(node.indexOf("=") + 1);
        } else if (node.indexOf('~') != -1) {
            return node.substring(node.indexOf("~") + 1);
        }
        return node;
    }
}
