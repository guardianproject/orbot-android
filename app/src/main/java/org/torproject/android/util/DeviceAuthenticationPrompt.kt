package org.torproject.android.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.torproject.android.R
import org.torproject.android.service.util.Prefs

class DeviceAuthenticationPrompt {
    companion object {

        /**
         * Launch a system prompt requiring the user to authenticate their
         * Android device. On APIs lower than 30, this has to support the password
         * and biometrics (if user configured them). On 30+, it can be toggled to
         * disallow biometrics
         */
        fun openPrompt(
            activity: FragmentActivity,
            callback: BiometricPrompt.AuthenticationCallback
        ) {
            val authenticators = getAuthenticators()
            // display error for no authentication or system error and abort flow
            val authenticationErrorCode =
                BiometricManager.from(activity).canAuthenticate(authenticators)
            if (authenticationErrorCode != BiometricManager.BIOMETRIC_SUCCESS) {
                callback.onAuthenticationError(
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    getAuthenticationErrorMessage(authenticationErrorCode, activity)
                )
                return
            }

            val appName =
                if (Prefs.isCamoEnabled()) Prefs.getCamoAppDisplayName() else activity.getString(
                    R.string.app_name
                )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setConfirmationRequired(true)
                .setTitle(appName)
                .setSubtitle(activity.getString(R.string.unlock_app_msg, appName))
                .setAllowedAuthenticators(authenticators)
                .build()

            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                .authenticate(promptInfo)
        }

        /**
         * Returns null if the device is configured to do the authentication prompt
         * or else a user-facing String indicating that the device is not configured
         * or otherwise fundamentally unable to authenticate
         */
        private fun getAuthenticationErrorMessage(errorCode: Int, context: Context): CharSequence {
            return when (errorCode) {
                BiometricManager.BIOMETRIC_SUCCESS -> ""

                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    context.getString(R.string.error_no_password_set)

                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                    context.getString(R.string.device_lock_security_update_needed)

                else -> context.getString(R.string.device_lock_unsupported)
            }
        }

        /**
         * Get legal BiometricManager.Authenticators for different versions of Android
         * if on API 30+ this is configured with a preference, otherwise its hardcoded
         */
        private fun getAuthenticators(): Int {
            val biometricOrPassword =
                BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return biometricOrPassword
            }
            return if (Prefs.disallowBiometricAuthentication())
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else
                biometricOrPassword
        }
    }
}