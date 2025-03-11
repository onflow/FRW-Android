package com.flowfoundation.wallet.page.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowfoundation.wallet.manager.biometric.BlockBiometricManager
import com.flowfoundation.wallet.page.security.biometric.BiometricActivity
import com.flowfoundation.wallet.page.security.pin.SecurityPinActivity
import com.flowfoundation.wallet.utils.getPinCode
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isBiometricEnable
import com.flowfoundation.wallet.utils.uiScope
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


// check security then open page
fun FragmentActivity.securityOpen(action: Intent) {
    ioScope {
        if (isBiometricEnable()) {
            uiScope {
                BlockBiometricManager.showBiometricPrompt(this) { isSuccess, _ ->
                    if (isSuccess) {
                        startActivity(action)
                    }
                }
            }
        } else {
            uiScope {
                if (getPinCode().isBlank()) {
                    startActivity(action)
                } else {
                    SecurityPinActivity.launch(this, SecurityPinActivity.TYPE_CHECK, action = action)
                }
            }
        }
    }
}

// check security
suspend fun securityVerification(activity: FragmentActivity) = suspendCoroutine { cont ->
    uiScope {
        if (isBiometricEnable()) {
            cont.resume(securityBiometricVerification(activity))
        } else {
            cont.resume(securityPinCodeVerification(activity))
        }
    }
}

private suspend fun securityBiometricVerification(activity: FragmentActivity) = suspendCoroutine { cont ->
    uiScope {
        LocalBroadcastManager.getInstance(activity).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getBooleanExtra(BiometricActivity.EXTRA_RESULT, false)?.let { cont.resume(it) }
                LocalBroadcastManager.getInstance(activity).unregisterReceiver(this)
            }
        }, IntentFilter(BiometricActivity.ACTION))
        BiometricActivity.launch(activity)
    }
}

private suspend fun securityPinCodeVerification(activity: FragmentActivity) = suspendCoroutine { cont ->
    uiScope {
        if (getPinCode().isBlank()) {
            cont.resume(true)
        } else {
            val filter = "${System.currentTimeMillis()}"
            LocalBroadcastManager.getInstance(activity).registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    cont.resume(intent?.getBooleanExtra("verify", false) ?: false)
                    LocalBroadcastManager.getInstance(activity).unregisterReceiver(this)
                }
            }, IntentFilter(filter))
            SecurityPinActivity.launch(activity, SecurityPinActivity.TYPE_CHECK, broadcastAction = filter)
        }
    }
}