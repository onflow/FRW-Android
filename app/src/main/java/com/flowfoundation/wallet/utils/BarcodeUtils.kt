package com.flowfoundation.wallet.utils

import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.flowfoundation.wallet.page.scan.ScanBarcodeActivity


fun Fragment.registerBarcodeLauncher(callback: (result: String?) -> Unit): ActivityResultLauncher<ScanOptions> {
    return registerForActivityResult(ScanContract()) { result -> callback.invoke(result.contents) }
}

fun FragmentActivity.registerBarcodeLauncher(callback: (result: String?) -> Unit): ActivityResultLauncher<ScanOptions> {
    return registerForActivityResult(ScanContract()) { result -> callback.invoke(result.contents) }
}

fun ActivityResultLauncher<ScanOptions>.launch() {
    launch(ScanOptions().apply {
        setOrientationLocked(true)
        setBeepEnabled(false)
        addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
        captureActivity = ScanBarcodeActivity::class.java
    })
}