package com.flowfoundation.wallet.widgets

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.progressindicator.CircularProgressIndicator
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.interceptor.PayerServiceInterceptor
import com.flowfoundation.wallet.network.model.PayerErrorResponse
import com.flowfoundation.wallet.network.model.SurgeInfo
import com.flowfoundation.wallet.utils.logd

/**
 * SurgePricingAlertViewXML - XML-based version of the surge pricing alert dialog
 *
 * This version uses an XML layout file for better UI control and preview capabilities
 * in Android Studio's Layout Editor.
 *
 * Benefits:
 * - Visual preview in Android Studio
 * - Better separation of UI and logic
 * - Easier customization through XML
 * - Theme support and style resources
 */
class SurgePricingAlertViewXML {

    companion object {
        private const val TAG = "SurgePricingAlertXML"
        private const val HOLD_DURATION_MS = 2500L // 2.5 seconds hold to confirm

        @Volatile
        private var currentDialog: Dialog? = null

        @Volatile
        private var isAlertShowing = false

        @Volatile
        private var userDecisionCallback: ((Boolean) -> Unit)? = null

        /**
         * Show surge pricing alert dialog using XML layout
         */
        @JvmStatic
        fun showSurgeAlert(
            activity: Activity,
            errorResponse: PayerErrorResponse,
            surgeInfo: SurgeInfo? = null,
            onUserDecision: (accepted: Boolean) -> Unit
        ) {
            if (isAlertShowing) {
                logd(TAG, "Alert already showing, ignoring duplicate request")
                return
            }

            if (activity.isFinishing || activity.isDestroyed) {
                logd(TAG, "Activity is finishing or destroyed, cannot show alert")
                return
            }

            activity.runOnUiThread {
                try {
                    dismissCurrentAlert()

                    userDecisionCallback = onUserDecision
                    isAlertShowing = true

                    // Track that the alert was shown
                    MixpanelManager.surgePricingAlertShown()

                    // Create the dialog
                    val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

                    // Inflate the XML layout
                    val inflater = LayoutInflater.from(activity)
                    val dialogView = inflater.inflate(R.layout.dialog_surge_pricing_alert, null)

                    // Setup the views
                    setupDialogViews(dialogView, surgeInfo, activity)

                    // Setup the dialog
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    dialog.setContentView(dialogView)
                    dialog.setCancelable(false)
                    dialog.setCanceledOnTouchOutside(false)

                    // Make the background semi-transparent
                    dialog.window?.apply {
                        setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
                        setFlags(
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND
                        )
                        attributes?.dimAmount = 0.5f
                    }

                    currentDialog = dialog
                    dialog.show()

                } catch (e: Exception) {
                    logd(TAG, "Error showing surge alert: ${e.message}")
                    isAlertShowing = false
                    userDecisionCallback = null
                }
            }
        }

        /**
         * Setup the dialog views with data and click listeners
         */
        private fun setupDialogViews(
            dialogView: View,
            surgeInfo: SurgeInfo?,
            context: Context
        ) {
            // Title (now centered)
            val titleText = dialogView.findViewById<TextView>(R.id.titleText)
            titleText.text = context.getString(R.string.surge_pricing_title)

            // Surge fee value
            val surgeFeeValue = dialogView.findViewById<TextView>(R.id.surgeFeeValue)
            val estimatedFee = surgeInfo?.maxFee?.let { String.format("%.6f", it) } ?: "0.003"
            surgeFeeValue.text = "$estimatedFee FLOW"

            // Description
            val descriptionText = dialogView.findViewById<TextView>(R.id.descriptionText)
            val multiplier = surgeInfo?.getMultiplierAsDouble()?.toInt() ?: 4
            descriptionText.text = "Due to high network activity, transaction fees are elevated, and Flow Wallet is temporarily not paying for your gas. Current network fees are ${multiplier}× higher than usual."

            // Close button (X)
            val closeButton = dialogView.findViewById<TextView>(R.id.closeButton)
            closeButton.setOnClickListener {
                logd(TAG, "User cancelled surge pricing via close button")
                userDecisionCallback?.invoke(false)
                dismissCurrentAlert()
            }

            // Cancel button removed - user can use X button to cancel

            // Hold to confirm button
            setupHoldToConfirmButton(dialogView, surgeInfo)
        }

        /**
         * Setup the hold-to-confirm button with progress animation
         */
        @SuppressLint("ClickableViewAccessibility")
        private fun setupHoldToConfirmButton(
            dialogView: View,
            surgeInfo: SurgeInfo?
        ) {
            val holdButtonContainer = dialogView.findViewById<FrameLayout>(R.id.holdButtonContainer)
            val holdButtonText = dialogView.findViewById<TextView>(R.id.holdButtonText)
            val progressIndicator = dialogView.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressIndicator)

            var startTimeMillis = 0L
            var isHolding = false
            val handler = Handler(Looper.getMainLooper())
            var progressTask: Runnable? = null

            // Initially set progress to 0 and make it determinate
            progressIndicator.isIndeterminate = false
            progressIndicator.progress = 0

            val updateProgress = {
                if (isHolding) {
                    val elapsedTime = System.currentTimeMillis() - startTimeMillis
                    val progress = ((elapsedTime.toFloat() / HOLD_DURATION_MS) * 100).toInt().coerceIn(0, 100)

                    progressIndicator.progress = progress

                    if (progress >= 100) {
                        // Completed holding - change to indeterminate spinner
                        progressIndicator.isIndeterminate = true
                        holdButtonText.text = ""
                        userDecisionCallback?.invoke(true)

                        // Keep dialog open with spinner showing while transaction processes
                    } else {
                        // Continue updating progress
                        progressTask?.let { handler.postDelayed(it, 16) } // Update every 16ms for smooth animation
                    }
                }
            }

            progressTask = Runnable { updateProgress() }

            val resetButton = {
                isHolding = false
                startTimeMillis = 0L
                progressTask?.let { handler.removeCallbacks(it) }

                // Reset progress indicator
                progressIndicator.isIndeterminate = false
                progressIndicator.progress = 0
                holdButtonText.text = holdButtonText.context.getString(R.string.surge_hold_to_confirm)
            }

            holdButtonContainer.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!isHolding && !progressIndicator.isIndeterminate) {
                            isHolding = true
                            startTimeMillis = System.currentTimeMillis()
                            handler.postDelayed(progressTask!!, 16)
                            logd(TAG, "Hold button pressed - starting hold timer")
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isHolding && !progressIndicator.isIndeterminate) {
                            // User released before completing
                            resetButton()
                            logd(TAG, "Hold button released early")
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        /**
         * Dismiss the current alert dialog
         */
        @JvmStatic
        fun dismissCurrentAlert() {
            try {
                currentDialog?.dismiss()
                currentDialog = null
                isAlertShowing = false
                userDecisionCallback = null
                logd(TAG, "Alert dismissed")
            } catch (e: Exception) {
                logd(TAG, "Error dismissing alert: ${e.message}")
            }
        }

        /**
         * Check if an alert is currently showing
         */
        @JvmStatic
        fun isAlertShowing(): Boolean = isAlertShowing
    }
}
