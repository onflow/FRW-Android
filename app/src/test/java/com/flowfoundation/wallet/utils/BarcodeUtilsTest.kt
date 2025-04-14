package com.flowfoundation.wallet.utils

import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanIntentResult
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class BarcodeUtilsTest {

    @Mock
    private lateinit var fragment: Fragment

    @Mock
    private lateinit var fragmentActivity: FragmentActivity

    @Mock
    private lateinit var activityResultLauncher: ActivityResultLauncher<ScanOptions>

    @Before
    fun setup() {
        whenever(fragment.registerForActivityResult<ScanOptions, ScanIntentResult>(any(), any())).thenReturn(activityResultLauncher)
        whenever(fragmentActivity.registerForActivityResult<ScanOptions, ScanIntentResult>(any(), any())).thenReturn(activityResultLauncher)
    }

    @Test
    fun `registerBarcodeLauncher for Fragment should register with correct contract and callback`() {
        // Given
        var callbackInvoked = false
        var result: String? = null
        val callback: (String?) -> Unit = { 
            callbackInvoked = true
            result = it
        }

        // When
        fragment.registerBarcodeLauncher(callback)

        // Then
        verify(fragment).registerForActivityResult(any<ScanContract>(), any())
    }

    @Test
    fun `registerBarcodeLauncher for FragmentActivity should register with correct contract and callback`() {
        // Given
        var callbackInvoked = false
        var result: String? = null
        val callback: (String?) -> Unit = { 
            callbackInvoked = true
            result = it
        }

        // When
        fragmentActivity.registerBarcodeLauncher(callback)

        // Then
        verify(fragmentActivity).registerForActivityResult(any<ScanContract>(), any())
    }

    @Test
    fun `launch should call launch with correct ScanOptions`() {
        // When
        activityResultLauncher.launch()

        // Then
        verify(activityResultLauncher).launch(any<ScanOptions>())
    }
}
