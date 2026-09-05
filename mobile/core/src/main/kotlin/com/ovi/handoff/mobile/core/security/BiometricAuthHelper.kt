package com.ovi.handoff.mobile.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Device-owner confirmation before a high-risk approval.
 *
 * Rewritten around `androidx.biometric` for two reasons. The platform `BiometricPrompt` path it used
 * needed `setUserAuthenticationParameters`, which is API 30, while this app supports API 29, so on the
 * oldest supported devices the old helper threw and fell through to its generic catch. And the caller
 * previously ended with `?: onApprove()`, meaning that whenever the composition context was not an
 * Activity, a critical action was approved with no authentication at all.
 *
 * [authenticate] therefore never falls back to success. If the device cannot authenticate, that is
 * reported through [onUnavailable] and the decision is the caller's to make explicitly.
 */
public object BiometricAuthHelper {

    private const val ALLOWED_AUTHENTICATORS: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    public enum class Availability {
        AVAILABLE,

        /** Hardware exists but nothing is enrolled, so the user can fix this in settings. */
        NOT_ENROLLED,

        /** No usable hardware or credential on this device. */
        UNAVAILABLE
    }

    public fun availability(context: android.content.Context): Availability =
        when (BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
            else -> Availability.UNAVAILABLE
        }

    /**
     * Prompts for biometric or device-credential confirmation.
     *
     * @param onUnavailable called when this device cannot prompt at all. Never treated as success.
     */
    public fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        when (availability(activity)) {
            Availability.NOT_ENROLLED -> {
                onUnavailable("No fingerprint, face, or device lock is set up on this phone.")
                return
            }
            Availability.UNAVAILABLE -> {
                onUnavailable("This device cannot confirm your identity.")
                return
            }
            Availability.AVAILABLE -> Unit
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // A user-initiated cancel is not a failure worth surfacing as an error.
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onFailure("")
                    } else {
                        onFailure(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // Not terminal: the prompt stays open for another attempt.
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .setConfirmationRequired(true)
                .build()
        )
    }
}
