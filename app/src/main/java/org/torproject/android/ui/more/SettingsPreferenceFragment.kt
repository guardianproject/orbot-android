package org.torproject.android.ui.more

import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.torproject.android.OrbotApp
import org.torproject.android.R
import org.torproject.android.localization.Languages
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.tor.ShadowSocks
import org.torproject.android.util.Prefs
import org.torproject.android.util.sendIntentToService
import kotlin.text.orEmpty

class SettingsPreferenceFragment : Fragment() {
    private var selectedSection by mutableStateOf<SettingsSectionId?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedSection != null) selectedSection = null
                else { remove(); findNavController().popBackStack() }
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OrbotSettingsTheme {
                    SettingsScreen(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it },
                        onBack = {
                            if (selectedSection != null) selectedSection = null
                            else findNavController().popBackStack()
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen(
        selectedSection: SettingsSectionId?,
        onSectionSelected: (SettingsSectionId) -> Unit,
        onBack: () -> Unit,
    ) {
        val title = selectedSection?.title() ?: stringResource(R.string.menu_settings)

        SettingsPage(title = title, onBack = onBack) {
            when (selectedSection) {
                null -> SettingsOverview(onSectionSelected)
                SettingsSectionId.General -> GeneralSettings()
                SettingsSectionId.Kindness -> KindnessSettings()
                SettingsSectionId.NodeConfig -> NodeConfigSettings()
                SettingsSectionId.ReachableAddresses -> ReachableAddressSettings()
                SettingsSectionId.Connectivity -> ConnectivitySettings()
                SettingsSectionId.Padding -> PaddingSettings()
                SettingsSectionId.Proxy -> ProxySettings()
                SettingsSectionId.Debug -> DebugSettings()
            }
        }
    }

    @Composable
    private fun SettingsOverview(onSectionSelected: (SettingsSectionId) -> Unit) {
        SettingsList {
            SettingsSectionId.entries.forEach { section ->
                SettingRow(
                    title = section.title(),
                    summary = section.summary(),
                    onClick = { onSectionSelected(section) },
                )
            }
        }
    }

    @Composable
    private fun GeneralSettings() {
        val languages = Languages[requireActivity()]
        
        var defaultLocale by remember { mutableStateOf(Prefs.defaultLocale) }
        var startBoot by remember { mutableStateOf(Prefs.startOnBoot) }
        var allowBackgroundStarts by remember { mutableStateOf(Prefs.allowBackgroundStarts) }
        var openProxyOnAllInterfaces by remember { mutableStateOf(Prefs.openProxyOnAllInterfaces) }
        var powerUserMode by remember { mutableStateOf(Prefs.isPowerUserMode) }
        var detectRoot by remember { mutableStateOf(Prefs.detectRoot) }
        
        SettingsList {
            ListSettingRow(
                value = defaultLocale,
                title = stringResource(R.string.set_locale_title),
                entries = languages?.allNames?.toList().orEmpty(),
                entryValues = languages?.supportedLocales?.toList().orEmpty(),
                onValueChanged = { language ->
                    defaultLocale = language
                    Prefs.defaultLocale = language
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val split = language.split("_")
                        val lang = split[0]
                        val region = split.getOrNull(1).orEmpty()
                        val newLocale = Languages.buildLocaleForLanguage(lang, region)
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.create(
                                newLocale
                            )
                        )
                    } else {
                        requireActivity().sendIntentToService(OrbotConstants.ACTION_LOCAL_LOCALE_SET)
                        (requireActivity().application as OrbotApp).setLocale()
                        requireActivity().finish()
                    }
                },
            )
            SwitchSettingRow(
                checked = startBoot,
                title = stringResource(R.string.pref_start_boot_title),
                summary = stringResource(R.string.pref_start_boot_summary),
                onChanged = { value ->
                    startBoot = value
                    Prefs.startOnBoot = value
                },
            )
            SwitchSettingRow(
                checked = allowBackgroundStarts,
                title = stringResource(R.string.pref_allow_background_starts_title),
                summary = stringResource(R.string.pref_allow_background_starts_summary),
                onChanged = { value ->
                    allowBackgroundStarts = value
                    Prefs.allowBackgroundStarts = value
                },
            )
            SwitchSettingRow(
                checked = openProxyOnAllInterfaces,
                title = stringResource(R.string.pref_open_proxy_on_all_interfaces_title),
                summary = stringResource(R.string.pref_open_proxy_on_all_interfaces_summary),
                onChanged = { value ->
                    openProxyOnAllInterfaces = value
                    Prefs.openProxyOnAllInterfaces = value
                },
            )
            SwitchSettingRow(
                checked = powerUserMode,
                title = stringResource(R.string.power_user_mode),
                summary = stringResource(R.string.power_user_description),
                onChanged = { value ->
                    powerUserMode = value
                    Prefs.isPowerUserMode = value
                },
            )
            SettingRow(
                title = stringResource(R.string.battery_optimization_title),
                summary = stringResource(R.string.battery_optimization_pref_msg),
                onClick = {
                    BatteryOptimizationsSettingDialog().show(
                        parentFragmentManager,
                        BatteryOptimizationsSettingDialog.TAG,
                    )
                },
            )
            SwitchSettingRow(
                checked = detectRoot,
                title = stringResource(R.string.pref_detect_root_title),
                summary = stringResource(R.string.pref_detect_root_summary),
                onChanged = { value ->
                    detectRoot = value
                    Prefs.detectRoot = value
                },
            )
        }
    }

    @Composable
    private fun KindnessSettings() {
        var showSnowflakeProxyMsg by remember { mutableStateOf(Prefs.showSnowflakeProxyToast) }
        
        SettingsList {
            SwitchSettingRow(
                checked = showSnowflakeProxyMsg,
                title = stringResource(R.string.snowflake_proxy_msg_title),
                summary = stringResource(R.string.snowflake_proxy_msg_description),
                onChanged = { value ->
                    showSnowflakeProxyMsg = value
                    Prefs.showSnowflakeProxyToast = value
                },
            )
        }
    }

    @Composable
    private fun NodeConfigSettings() {
        var entranceNodes by remember { mutableStateOf(Prefs.entryNodes.orEmpty()) }
        var exitNodes by remember { mutableStateOf(Prefs.exitNodes.orEmpty()) }
        var excludeNodes by remember { mutableStateOf(Prefs.excludeNodes.orEmpty()) }
        var strictNodes by remember { mutableStateOf(Prefs.strictNodes) }
        
        SettingsList {
            EditTextSettingRow(
                value = entranceNodes,
                title = stringResource(R.string.pref_entrance_node),
                summary = stringResource(R.string.pref_entrance_node_summary),
                dialogTitle = stringResource(R.string.pref_entrance_node_dialog),
                onValueChanged = { value ->
                    entranceNodes = value
                    Prefs.entryNodes = value
                },
            )
            EditTextSettingRow(
                value = exitNodes,
                title = stringResource(R.string.exit_nodes),
                summary = stringResource(R.string.fingerprints_nicks_countries_and_addresses_for_the_last_hop),
                dialogTitle = stringResource(R.string.enter_exit_nodes),
                onValueChanged = { value ->
                    exitNodes = value
                    Prefs.exitNodes = value
                },
            )
            EditTextSettingRow(
                value = excludeNodes,
                title = stringResource(R.string.exclude_nodes),
                summary = stringResource(R.string.fingerprints_nicks_countries_and_addresses_to_exclude),
                dialogTitle = stringResource(R.string.enter_exclude_nodes),
                onValueChanged = { value ->
                    excludeNodes = value
                    Prefs.excludeNodes = value
                },
            )
            SwitchSettingRow(
                checked = strictNodes,
                title = stringResource(R.string.strict_nodes),
                summary = stringResource(R.string.strict_nodes_description),
                onChanged = { value ->
                    strictNodes = value
                    Prefs.strictNodes = value
                },
            )
        }
    }

    @Composable
    private fun ReachableAddressSettings() {
        var reachableAddresses by remember { mutableStateOf(Prefs.reachableAddresses) }
        var reachableAddressesPorts by remember { mutableStateOf(Prefs.reachableAddressesPorts.orEmpty()) }
        
        SettingsList {
            SwitchSettingRow(
                checked = reachableAddresses,
                title = stringResource(R.string.reachable_addresses),
                summary = stringResource(R.string.run_as_a_client_behind_a_firewall_with_restrictive_policies),
                onChanged = { value ->
                    reachableAddresses = value
                    Prefs.reachableAddresses = value
                },
            )
            EditTextSettingRow(
                value = reachableAddressesPorts,
                title = stringResource(R.string.reachable_ports),
                summary = stringResource(R.string.ports_reachable_behind_a_restrictive_firewall),
                dialogTitle = stringResource(R.string.enter_ports),
                onValueChanged = { value ->
                    reachableAddressesPorts = value
                    Prefs.reachableAddressesPorts = value
                },
            )
        }
    }

    @Composable
    private fun ConnectivitySettings() {
        var isolateDest by remember { mutableStateOf(Prefs.isolateDest) }
        var isolatePort by remember { mutableStateOf(Prefs.isolatePort) }
        var isolateProtocol by remember { mutableStateOf(Prefs.isolateProtocol) }
        var isolateKeepAlive by remember { mutableStateOf(Prefs.isolateKeepAlive) }
        var preferIpv6 by remember { mutableStateOf(Prefs.preferIpv6) }
        var disableIpv4 by remember { mutableStateOf(Prefs.disableIpv4) }
        
        SettingsList {
            SwitchSettingRow(
                checked = isolateDest,
                title = stringResource(R.string.pref_isolate_dest),
                summary = stringResource(R.string.pref_isolate_dest_summary),
                onChanged = { value ->
                    isolateDest = value
                    Prefs.isolateDest = value
                },
            )
            SwitchSettingRow(
                checked = isolatePort,
                title = stringResource(R.string.pref_isolate_port),
                summary = stringResource(R.string.pref_isolate_port_summary),
                onChanged = { value ->
                    isolatePort = value
                    Prefs.isolatePort = value
                },
            )
            SwitchSettingRow(
                checked = isolateProtocol,
                title = stringResource(R.string.pref_isolate_protocol),
                summary = stringResource(R.string.pref_isolate_protocol_summary),
                onChanged = { value ->
                    isolateProtocol = value
                    Prefs.isolateProtocol = value
                },
            )
            SwitchSettingRow(
                checked = isolateKeepAlive,
                title = stringResource(R.string.pref_isolate_keep_alive),
                summary = stringResource(R.string.pref_isolate_keep_alive_summary),
                onChanged = { value ->
                    isolateKeepAlive = value
                    Prefs.isolateKeepAlive = value
                },
            )
            SwitchSettingRow(
                checked = preferIpv6,
                title = stringResource(R.string.pref_prefer_ipv6),
                summary = stringResource(R.string.pref_prefer_ipv6_summary),
                onChanged = { value ->
                    preferIpv6 = value
                    Prefs.preferIpv6 = value
                },
            )
            SwitchSettingRow(
                checked = disableIpv4,
                title = stringResource(R.string.pref_disable_ipv4),
                summary = stringResource(R.string.pref_disable_ipv4_summary),
                onChanged = { value ->
                    disableIpv4 = value
                    Prefs.disableIpv4 = value
                },
            )
        }
    }

    @Composable
    private fun PaddingSettings() {
        var connectionPadding by remember { mutableStateOf(Prefs.connectionPadding) }
        var reducedConnectionPadding by remember { mutableStateOf(Prefs.reducedConnectionPadding) }
        var circuitPadding by remember { mutableStateOf(Prefs.circuitPadding) }
        var reducedCircuitPadding by remember { mutableStateOf(Prefs.reducedCircuitPadding) }
        
        SettingsList {
            SwitchSettingRow(
                checked = connectionPadding,
                title = stringResource(R.string.pref_connection_padding),
                summary = stringResource(R.string.pref_connection_padding_summary),
                onChanged = { value ->
                    connectionPadding = value
                    Prefs.connectionPadding = value
                },
            )
            SwitchSettingRow(
                checked = reducedConnectionPadding,
                title = stringResource(R.string.pref_reduced_connection_padding),
                summary = stringResource(R.string.pref_reduced_connection_padding_summary),
                onChanged = { value ->
                    reducedConnectionPadding = value
                    Prefs.reducedConnectionPadding = value
                },
            )
            SwitchSettingRow(
                checked = circuitPadding,
                title = stringResource(R.string.pref_circuit_padding),
                summary = stringResource(R.string.pref_circuit_padding_summary),
                onChanged = { value ->
                    circuitPadding = value
                    Prefs.circuitPadding = value
                },
            )
            SwitchSettingRow(
                checked = reducedCircuitPadding,
                title = stringResource(R.string.pref_reduced_circuit_padding),
                summary = stringResource(R.string.pref_reduced_circuit_padding_summary),
                onChanged = { value ->
                    reducedCircuitPadding = value
                    Prefs.reducedCircuitPadding = value
                },
            )
        }
    }

    @Composable
    private fun ProxySettings() {
        var proxyType by remember { mutableStateOf((Prefs.outboundProxy.first?.scheme ?: "")) }
        var proxySS by remember { mutableStateOf((Prefs.outboundProxy.first?.toString() ?: "")) }
        var proxyHost by remember { mutableStateOf((Prefs.outboundProxy.first?.host ?: "")) }
        var proxyPort by remember { mutableStateOf((Prefs.outboundProxy.first?.port?.toString() ?: "")) }
        var proxyUsername by remember { mutableStateOf((Prefs.outboundProxy.first?.userInfo?.split(":")?.getOrNull(0) ?: "")) }
        var proxyPassword by remember { mutableStateOf((Prefs.outboundProxy.first?.userInfo?.split(":")?.getOrNull(1) ?: "")) }
        
        val entries = stringArrayResourceList(R.array.proxy_types).toMutableList()
        val entryValues = stringArrayResourceList(R.array.proxy_type_values).toMutableList()
        if (!ShadowSocks.isShadowSocksSupported()) {
            val ssIndex = entryValues.indexOf(ShadowSocks.SCHEME)
            if (ssIndex != -1) {
                entries.removeAt(ssIndex)
                entryValues.removeAt(ssIndex)
            }
            if (proxyType == ShadowSocks.SCHEME) {
                proxyType = ""
            }
        }

        SettingsList {
            SettingRow(
                title = null,
                summary = stringResource(R.string.note_snowflake_doesn_t_support_proxies),
            )
            ListSettingRow(
                value = proxyType,
                title = stringResource(R.string.pref_proxy_type_title),
                entries = entries,
                entryValues = entryValues,
                onValueChanged = { proxyType = it },
            )
            if (proxyType == ShadowSocks.SCHEME) {
                EditTextSettingRow(
                    value = proxySS,
                    title = stringResource(R.string.pref_proxy_ss_title),
                    dialogTitle = stringResource(R.string.pref_proxy_ss_summary),
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                    simpleSummary = true,
                    onValueChanged = { value ->
                        proxySS = value
                    },
                )
            } else if (proxyType.isNotEmpty()) {
                EditTextSettingRow(
                    value = proxyHost,
                    title = stringResource(R.string.pref_proxy_host_title),
                    dialogTitle = stringResource(R.string.pref_proxy_host_summary),
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                    simpleSummary = true,
                    onValueChanged = { value ->
                        proxyHost = value
                    },
                )
                EditTextSettingRow(
                    value = proxyPort,
                    title = stringResource(R.string.pref_proxy_port_title),
                    dialogTitle = stringResource(R.string.pref_proxy_port_summary),
                    inputType = InputType.TYPE_CLASS_NUMBER,
                    maxLength = 5,
                    simpleSummary = true,
                    onValueChanged = { value ->
                        proxyPort = value
                    },
                )
                EditTextSettingRow(
                    value = proxyUsername,
                    title = stringResource(R.string.pref_proxy_username_title),
                    dialogTitle = stringResource(R.string.pref_proxy_username_summary),
                    simpleSummary = true,
                    onValueChanged = { value ->
                        proxyUsername = value
                    },
                )
                EditTextSettingRow(
                    value = proxyPassword,
                    title = stringResource(R.string.pref_proxy_password_title),
                    dialogTitle = stringResource(R.string.pref_proxy_password_summary),
                    password = true,
                    simpleSummary = true,
                    onValueChanged = { value ->
                        proxyPassword = value
                    },
                )
            }
        }
    }

    @Composable
    private fun DebugSettings() {
        var sockPort by remember { mutableStateOf(Prefs.proxySocksPort.orEmpty()) }
        var httpPort by remember { mutableStateOf(Prefs.proxyHttpPort.orEmpty()) }
        var transport by remember { mutableStateOf(Prefs.torTransPort.orEmpty()) }
        var dnsPort by remember { mutableStateOf(Prefs.torDnsPort.orEmpty()) }
        var customTorrc by remember { mutableStateOf(Prefs.customTorRc.orEmpty()) }
        var enableLogging by remember { mutableStateOf(Prefs.useDebugLogging) }
        
        SettingsList {
            EditTextSettingRow(
                value = sockPort,
                title = stringResource(R.string.pref_socks_title),
                summary = stringResource(R.string.pref_socks_summary),
                dialogTitle = stringResource(R.string.pref_socks_dialog),
                inputType = InputType.TYPE_CLASS_NUMBER,
                maxLength = 5,
                onValueChanged = { value ->
                    sockPort = value
                    Prefs.proxySocksPort = value
                },
            )
            EditTextSettingRow(
                value = httpPort,
                title = stringResource(R.string.pref_http_title),
                summary = stringResource(R.string.pref_http_summary),
                dialogTitle = stringResource(R.string.pref_http_dialog),
                inputType = InputType.TYPE_CLASS_NUMBER,
                maxLength = 5,
                onValueChanged = { value ->
                    httpPort = value
                    Prefs.proxyHttpPort = value
                },
            )
            EditTextSettingRow(
                value = transport,
                title = stringResource(R.string.pref_transport_title),
                summary = stringResource(R.string.pref_transport_summary),
                dialogTitle = stringResource(R.string.pref_transport_dialog),
                onValueChanged = { value ->
                    transport = value
                    Prefs.torTransPort = value
                },
            )
            EditTextSettingRow(
                value = dnsPort,
                title = stringResource(R.string.pref_dnsport_title),
                summary = stringResource(R.string.pref_dnsport_summary),
                dialogTitle = stringResource(R.string.pref_dnsport_dialog),
                onValueChanged = { value ->
                    dnsPort = value
                    Prefs.torDnsPort = value
                },
            )
            EditTextSettingRow(
                value = customTorrc,
                title = stringResource(R.string.pref_torrc_title),
                summary = stringResource(R.string.pref_torrc_summary),
                dialogTitle = stringResource(R.string.pref_torrc_dialog),
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                onValueChanged = { value ->
                    customTorrc = value
                    Prefs.customTorRc = value
                },
            )
            SwitchSettingRow(
                checked = enableLogging,
                title = "Debug Log",
                summary = stringResource(R.string.enable_debug_log_to_output_must_use_adb_or_alogcat_to_view_),
                onChanged = { value ->
                    enableLogging = value
                    Prefs.useDebugLogging = value
                },
            )
        }
    }

    private enum class SettingsSectionId(
        @param:StringRes val titleRes: Int,
        @param:StringRes val summaryRes: Int? = null
    ) {
        General(R.string.pref_general_group),
        Kindness(R.string.volunteer_mode),
        NodeConfig(
            R.string.pref_node_configuration,
            R.string.pref_node_configuration_summary
        ),
        ReachableAddresses(R.string.reachable_addresses),
        Connectivity(R.string.setting_connectivity),
        Padding(R.string.setting_padding),
        Proxy(R.string.pref_proxy_title),
        Debug(R.string.setting_debug);

        @Composable
        fun title(): String = stringResource(titleRes)

        @Composable
        fun summary(): String? = summaryRes?.let { stringResource(it) }
    }
}
