package com.flowfoundation.wallet.page.scan

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.ViewfinderView
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityScanBarcodeBinding
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible


class ScanBarcodeActivity : BaseActivity(), DecoratedBarcodeView.TorchListener {

    private lateinit var capture: CaptureManager

    private lateinit var binding: ActivityScanBarcodeBinding

    private var isTorchOn = false

    private val viewFinderView by lazy { binding.root.findViewById<ViewfinderView>(R.id.zxing_viewfinder_view) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBarcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(false).applyStatusBar()

        with(binding) {
            zxingBarcodeScanner.setTorchListener(this@ScanBarcodeActivity)
            actionWrapper.addStatusBarTopPadding()
            flashButton.setVisible(hasFlash())
            flashButton.setOnClickListener { switchFlashlight() }
            closeButton.setOnClickListener { finish() }
        }

        capture = CaptureManager(this, binding.zxingBarcodeScanner).apply {
            initializeFromIntent(intent, savedInstanceState)
            setShowMissingCameraPermissionDialog(false)
            decode()
        }

        with(viewFinderView) {
            setMaskColor(R.color.black_60.res2color())
            setLaserVisibility(true)
        }
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        super.onPanelClosed(featureId, menu)
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return binding.zxingBarcodeScanner.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onTorchOn() {
        isTorchOn = true
        binding.flashButton.setImageResource(R.drawable.ic_baseline_flash_on_24)
    }

    override fun onTorchOff() {
        isTorchOn = false
        binding.flashButton.setImageResource(R.drawable.ic_baseline_flash_off_24)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun switchFlashlight() {
        if (isTorchOn) {
            binding.zxingBarcodeScanner.setTorchOff()
        } else {
            binding.zxingBarcodeScanner.setTorchOn()
        }
    }

    private fun hasFlash(): Boolean = applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
}