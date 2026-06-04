package org.torproject.android.ui.more

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.torproject.android.R
import org.torproject.android.ui.core.BaseActivity
import org.torproject.android.util.Prefs

class SafetyPreferenceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OrbotSettingsTheme {
                    SafetyScreen(
                        onBack = { findNavController().popBackStack() },
                        onOpenCamo = { findNavController().navigate(R.id.open_camo) },
                        onSecureWindowChanged = { (activity as BaseActivity).resetSecureFlags() }
                    )
                }
            }
        }
    }

    @Composable
    private fun SafetyScreen(
        onBack: () -> Unit,
        onOpenCamo: () -> Unit,
        onSecureWindowChanged: (Boolean) -> Unit
    ) {
        var secureWindow by remember {
            mutableStateOf(Prefs.isSecureWindow)
        }

        var requirePassword by remember {
            mutableStateOf(Prefs.requireDeviceAuthentication)
        }

        var unlockWithBiometrics by remember {
            mutableStateOf(!Prefs.disallowBiometricAuthentication)
        }

        SettingsPage(
            title = stringResource(R.string.title_safety),
            onBack = onBack
        ) {
            SettingsList {
                SettingRow(
                    title = stringResource(R.string.setting_app_icon_title),
                    summary = stringResource(R.string.setting_app_icon_description),
                    onClick = onOpenCamo,
                )
                SwitchSettingRow(
                    checked = secureWindow,
                    title = stringResource(R.string.setting_block_screenshot_title),
                    summary = stringResource(R.string.setting_block_screenshot_description),
                    onChanged = {
                        secureWindow = it
                        Prefs.isSecureWindow = it
                        onSecureWindowChanged(it)
                    },
                )
                SwitchSettingRow(
                    checked = requirePassword,
                    title = stringResource(R.string.setting_unlock_pass_title),
                    summary = stringResource(R.string.setting_unlock_pass_description),
                    onChanged = {
                        requirePassword = it
                        Prefs.requireDeviceAuthentication = it
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && requirePassword) {
                    SwitchSettingRow(
                        checked = unlockWithBiometrics,
                        title = stringResource(R.string.setting_unlock_with_biometrics_title),
                        summary = stringResource(R.string.setting_unlock_with_biometrics_description),
                        onChanged = {
                            unlockWithBiometrics = it
                            Prefs.disallowBiometricAuthentication = !it
                        },
                    )
                }
            }
        }
    }
}
