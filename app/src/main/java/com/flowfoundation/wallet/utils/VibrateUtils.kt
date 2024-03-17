package com.flowfoundation.wallet.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

fun vibrateTransaction() {
    vibrate().vibrate(VibrationEffect.createOneShot(50, 150))
}

private fun vibrate() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    (Env.getApp().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
} else {
    Env.getApp().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
}
