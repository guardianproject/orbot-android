package org.torproject.android.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.torproject.android.R
import org.torproject.android.service.util.Prefs

class RequirePasswordPrompt {
    companion object {

        const val AUTHENTICATORS =
            BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK

        fun openPrompt(
            activity: FragmentActivity,
            callback: BiometricPrompt.AuthenticationCallback
        ) {

            // display error for no authentication or system error and abort flow
            val authenticationErrorCode = BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)
            if (authenticationErrorCode != BiometricManager.BIOMETRIC_SUCCESS) {
                @SuppressLint("WrongConstant") // we are only using the "right" constants here, from the API...
                callback.onAuthenticationError(authenticationErrorCode, getAuthenticationErrorMessage(authenticationErrorCode, activity))
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
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            val prompt =
                BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            prompt.authenticate(promptInfo)
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
    }
}