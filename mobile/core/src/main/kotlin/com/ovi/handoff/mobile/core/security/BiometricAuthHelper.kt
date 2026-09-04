package com.ovi.handoff.mobile.core.security

import android.app.Activity
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.UserNotAuthenticatedException

public object BiometricAuthHelper {
    private const val KEY_NAME = "handoff_biometric_key"

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_NAME)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            val builder = KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(300, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            
            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        }
        return keyStore.getKey(KEY_NAME, null) as SecretKey
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public fun authenticate(
        activity: Activity,
        title: String = "Authorize Critical Action",
        subtitle: String = "Fingerprint or device lock confirmation required",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
            
            // Try to initialize cipher. If the 300-second window is active, this succeeds immediately!
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            // We are within the 5-minute cache window. Success!
            onSuccess()
            return
        } catch (e: UserNotAuthenticatedException) {
            // Cache expired or not authenticated yet. Need to prompt.
        } catch (e: Exception) {
            // Key might be invalidated if biometrics changed. Delete and retry.
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(KEY_NAME)
            } catch (ignored: Exception) {}
        }

        val cancellationSignal = CancellationSignal()
        val executor = activity.mainExecutor

        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
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
