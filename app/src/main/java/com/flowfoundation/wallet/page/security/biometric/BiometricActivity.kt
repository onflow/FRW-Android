package com.flowfoundation.wallet.page.security.biometric

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.biometric.BiometricPrompt
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.manager.biometric.BlockBiometricManager

class BiometricActivity : BaseActivity() {

    private var biometricPrompt: BiometricPrompt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
        UltimateBarX.with(this).color(Color.TRANSPARENT).fitWindow(false).light(false).applyStatusBar()

        biometricPrompt = BlockBiometricManager.showBiometricPrompt(this) { isSuccess, _ ->
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION).apply {
                putExtra(EXTRA_RESULT, isSuccess)
            })
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    override fun onDestroy() {
        biometricPrompt?.cancelAuthentication()
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION))
        super.onDestroy()
    }

    companion object {
        const val ACTION = "biometric_result_action"
        const val EXTRA_RESULT = "biometric_result"

        fun launch(context: Context) {
            context.startActivity(Intent(context, BiometricActivity::class.java))
        }
    }
}