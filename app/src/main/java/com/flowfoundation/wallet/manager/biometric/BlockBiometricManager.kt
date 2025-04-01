package com.flowfoundation.wallet.manager.biometric

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import java.util.concurrent.Executor


object BlockBiometricManager {
    private val TAG = BlockBiometricManager::class.java.simpleName

    fun checkIsBiometricEnable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticateCode = biometricManager.canAuthenticate(BIOMETRIC_WEAK)
        when (authenticateCode) {
            BiometricManager.BIOMETRIC_SUCCESS -> logd(TAG, "App can authenticate using biometrics.")
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> loge(TAG, "No biometric features available on this device.")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> loge(TAG, "Biometric features are currently unavailable.")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> loge(TAG, "Biometric features are currently none enrolled.")
            else -> loge(TAG, "Biometric features authenticateCode:$authenticateCode")
        }
        return authenticateCode == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(activity: FragmentActivity, callback: (isSuccess: Boolean, errorMsg: String) -> Unit):
            BiometricPrompt {
        val promptInfo: BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Cancel")
            .build()

        val executor = Executor { Handler(Looper.getMainLooper()).post(it) }
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                loge(TAG, "Authentication error: $errString")
                callback(false, errString.toString().ifEmpty { R.string.auth_error.res2String() })
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                logd(TAG, "onAuthenticationSucceeded:${result.cryptoObject}")
                callback(true, "")
            }

            override fun onAuthenticationFailed() {
                loge(TAG, "Authentication failed")
                callback(false, R.string.auth_error.res2String())
            }
        })

        return biometricPrompt.apply { authenticate(promptInfo) }
    }

}