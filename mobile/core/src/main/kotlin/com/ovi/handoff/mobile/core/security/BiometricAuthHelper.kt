package com.ovi.handoff.mobile.core.security

import android.app.Activity
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi

public object BiometricAuthHelper {
    @RequiresApi(Build.VERSION_CODES.P)
    public fun authenticate(
        activity: Activity,
        title: String = "Authorize Critical Action",
        subtitle: String = "Fingerprint or device lock confirmation required",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cancellationSignal = CancellationSignal()
        val executor = activity.mainExecutor

        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton("Cancel", executor) { _, _ ->
                onError("Authentication cancelled")
            }
            .build()

        prompt.authenticate(
            cancellationSignal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString?.toString() ?: "Authentication error")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed")
                }
            }
        )
    }
}
