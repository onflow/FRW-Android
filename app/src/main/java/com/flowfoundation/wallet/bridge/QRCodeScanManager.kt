package com.flowfoundation.wallet.bridge

import android.app.Activity
import android.content.Intent
import com.facebook.react.bridge.Promise

object QRCodeScanManager {
    private var pendingPromise: Promise? = null

    fun setPendingPromise(promise: Promise) {
        pendingPromise = promise
    }

    fun handleScanResult(resultCode: Int, data: Intent?) {
        val promise = pendingPromise ?: return
        pendingPromise = null

        when (resultCode) {
            Activity.RESULT_OK -> {
                val scanResult = data?.getStringExtra("SCAN_RESULT")
                if (!scanResult.isNullOrEmpty()) {
                    promise.resolve(scanResult)
                } else {
                    promise.reject("SCAN_ERROR", "No scan result received", null)
                }
            }
            Activity.RESULT_CANCELED -> {
                promise.reject("SCAN_CANCELLED", "QR scan was cancelled", null)
            }
            else -> {
                promise.reject("SCAN_ERROR", "QR scan failed with result code: $resultCode", null)
            }
        }
    }
}