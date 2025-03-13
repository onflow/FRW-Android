package com.flowfoundation.wallet.utils

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.flowfoundation.wallet.page.scan.ScanBarcodeActivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric

@RunWith(RobolectricTestRunner::class)
class BarcodeUtilsTest {

    @Test
    fun `test Fragment registerBarcodeLauncher callback with success`() {
        // Create a Fragment
        val fragment = Fragment()
        var capturedResult: String? = null
        
        // Create mock ActivityResultLauncher
        val mockLauncher = fragment.registerBarcodeLauncher { result ->
            capturedResult = result
        }

        // Simulate successful scan
        val scanResult = ScanIntentResult("test_qr_code", null, null, null)
        (mockLauncher as ScanContract.ScanResultCallback).onScanResultCallback(scanResult)

        // Verify callback was called with correct result
        assertThat(capturedResult).isEqualTo("test_qr_code")
    }

    @Test
    fun `test Fragment registerBarcodeLauncher callback with null result`() {
        val fragment = Fragment()
        var capturedResult: String? = null
        
        val mockLauncher = fragment.registerBarcodeLauncher { result ->
            capturedResult = result
        }

        // Simulate cancelled/failed scan
        val scanResult = ScanIntentResult(null, null, null, null)
        (mockLauncher as ScanContract.ScanResultCallback).onScanResultCallback(scanResult)

        // Verify callback was called with null
        assertThat(capturedResult).isNull()
    }

    @Test
    fun `test FragmentActivity registerBarcodeLauncher callback with success`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()
        var capturedResult: String? = null
        
        val mockLauncher = activity.registerBarcodeLauncher { result ->
            capturedResult = result
        }

        // Simulate successful scan
        val scanResult = ScanIntentResult("test_qr_code", null, null, null)
        (mockLauncher as ScanContract.ScanResultCallback).onScanResultCallback(scanResult)

        // Verify callback was called with correct result
        assertThat(capturedResult).isEqualTo("test_qr_code")
    }

    @Test
    fun `test FragmentActivity registerBarcodeLauncher callback with null result`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()
        var capturedResult: String? = null
        
        val mockLauncher = activity.registerBarcodeLauncher { result ->
            capturedResult = result
        }

        // Simulate cancelled/failed scan
        val scanResult = ScanIntentResult(null, null, null, null)
        (mockLauncher as ScanContract.ScanResultCallback).onScanResultCallback(scanResult)

        // Verify callback was called with null
        assertThat(capturedResult).isNull()
    }

    @Test
    fun `test launch configures ScanOptions correctly`() {
        val mockLauncher = mock<ActivityResultLauncher<ScanOptions>>()
        val optionsCaptor = argumentCaptor<ScanOptions>()

        mockLauncher.launch()

        verify(mockLauncher).launch(optionsCaptor.capture())
        
        val options = optionsCaptor.firstValue
        assertThat(options.orientationLocked).isTrue()
        assertThat(options.beepEnabled).isFalse()
        assertThat(options.desiredBarcodeFormats).isEqualTo(ScanOptions.QR_CODE)
        assertThat(options.moreExtras).containsEntry(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
        assertThat(options.captureActivity).isEqualTo(ScanBarcodeActivity::class.java)
    }

    @Test
    fun `test launch with custom ScanOptions`() {
        val mockLauncher = mock<ActivityResultLauncher<ScanOptions>>()
        val optionsCaptor = argumentCaptor<ScanOptions>()

        // Launch with default options
        mockLauncher.launch()

        verify(mockLauncher).launch(optionsCaptor.capture())
        
        val options = optionsCaptor.firstValue
        
        // Verify all default options are set
        with(options) {
            assertThat(orientationLocked).isTrue()
            assertThat(beepEnabled).isFalse()
            assertThat(desiredBarcodeFormats).isEqualTo(ScanOptions.QR_CODE)
            assertThat(moreExtras).containsEntry(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
            assertThat(captureActivity).isEqualTo(ScanBarcodeActivity::class.java)
        }
    }
} 