package org.torproject.android.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.torproject.android.R
import org.torproject.android.service.util.Prefs

class RequirePasswordPrompt {
    companion object {
        fun openPrompt(activity: FragmentActivity, callback: BiometricPrompt.AuthenticationCallback) {
            val appName = if (Prefs.isCamoEnabled()) Prefs.getCamoAppDisplayName() else activity.getString(
                R.string.app_name)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setConfirmationRequired(true)
                .setTitle("Unlock $appName")
                .setSubtitle("Your device password is needed to unlock Orbot")
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            val prompt =
                BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            prompt.authenticate(promptInfo)
        }
    }
}