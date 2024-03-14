package com.flowfoundation.wallet.page.window

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.drive.GoogleDriveAuthActivity
import com.flowfoundation.wallet.page.browser.releaseBrowser
import com.flowfoundation.wallet.page.browser.subpage.filepicker.FilePickerActivity
import com.flowfoundation.wallet.page.security.biometric.BiometricActivity
import com.flowfoundation.wallet.page.security.pin.SecurityPinActivity
import com.flowfoundation.wallet.page.window.bubble.attachBubble
import com.flowfoundation.wallet.page.window.bubble.releaseBubble
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.widgets.floatwindow.FloatWindow
import com.flowfoundation.wallet.widgets.floatwindow.FloatWindowConfig

class WindowFrame {

    companion object {
        private const val WINDOW_TAG = "WindowFrame"

        @SuppressLint("StaticFieldLeak")
        private var windowFrame: View? = null

        fun attach(activity: Activity) {
            if (FloatWindow.isShowing(WINDOW_TAG)) {
                return
            }

            FloatWindow.builder().apply {
                setConfig(
                    FloatWindowConfig(
                        gravity = Gravity.TOP or Gravity.START,
                        contentView = windowInstance(activity),
                        tag = WINDOW_TAG,
                        isTouchEnable = true,
                        disableAnimation = true,
                        hardKeyEventEnable = true,
                        immersionStatusBar = true,
                        width = ScreenUtils.getScreenWidth(),
                        height = ScreenUtils.getScreenHeight(),
                        widthMatchParent = true,
                        heightMatchParent = true,
                        isFullScreen = true,
                        ignorePage = listOf(
                            FilePickerActivity::class,
                            GoogleDriveAuthActivity::class,
                            BiometricActivity::class,
                            SecurityPinActivity::class,
                        )
                    )
                )
                show(activity)
            }

            attachBubble()
        }

        fun release() {
            releaseBrowser()
            releaseBubble()
            FloatWindow.dismiss(WINDOW_TAG)
            windowFrame = null
        }

        fun browserContainer(): ViewGroup? {
            tryAttach()
            return windowFrame?.findViewById(R.id.browser_container)
        }

        fun bubbleContainer(): ViewGroup? {
            tryAttach()
            return windowFrame?.findViewById(R.id.bubble_container)
        }

        private fun tryAttach() {
            if (!FloatWindow.isShowing(WINDOW_TAG)) {
                val activity = BaseActivity.getCurrentActivity() ?: return
                attach(activity)
            }
        }

        @SuppressLint("InflateParams")
        private fun windowInstance(activity: Activity): View {
            return windowFrame ?: LayoutInflater.from(activity).inflate(R.layout.window_frame, null).apply { windowFrame = this }
        }
    }
}