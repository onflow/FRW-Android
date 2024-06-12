package com.flowfoundation.wallet.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.fragment.app.FragmentActivity
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.WidgetSendButtonBinding
import com.flowfoundation.wallet.page.security.securityVerification
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import kotlin.math.min

class SendButton : TouchScaleCardView {

    private val duration = 1.2f * DateUtils.SECOND_IN_MILLIS

    private var binding = WidgetSendButtonBinding.inflate(LayoutInflater.from(context))

    private var indicatorProgress = 0.0f

    private var progressTask = Runnable { updateProgress() }

    private var isPressedDown = false

    private var onProcessing: (() -> Unit)? = null

    private var defaultTextId: Int
    private var processingTextId: Int

    private var state = ButtonState.DEFAULT

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        val array = context.obtainStyledAttributes(attrs, R.styleable.SendButton, defStyleAttr, 0)
        defaultTextId = array.getResourceId(R.styleable.SendButton_defaultText, 0)
        processingTextId = array.getResourceId(R.styleable.SendButton_processingText, 0)
        array.recycle()
        init()
    }

    private fun init() {
        addView(binding.root)
        with(binding) {
            holdToSend.setText(defaultTextId)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state != ButtonState.DEFAULT || !isEnabled) {
            return super.onTouchEvent(event)
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressedDown = true
                postDelayed(progressTask, 16)
                logd(TAG, "onTouchEvent down")
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchUp()
        }

        return true
    }

    fun updateDefaultText(textId: Int) {
        defaultTextId = textId
        binding.holdToSend.setText(defaultTextId)
    }

    fun updateProcessingText(textId: Int) {
        processingTextId = textId
    }

    fun setOnProcessing(onProcessing: () -> Unit) {
        this.onProcessing = onProcessing
    }

    private fun onTouchUp() {
        logd(TAG, "onTouchUp()")
        isPressedDown = false
        if (state == ButtonState.DEFAULT) {
            changeState(ButtonState.DEFAULT)
        }
    }

    fun changeState(state: ButtonState) {
        this.state = state
        with(binding) {
            when (state) {
                ButtonState.DEFAULT -> {
                    setScaleEnable(true)
                    progressBar.changeIndeterminate(false)
                    removeCallbacks(progressTask)
                    indicatorProgress = 0f
                    holdToSend.setText(defaultTextId)
                    progressBar.setProgress(0, true)
                }
                ButtonState.VERIFICATION -> verification()
                ButtonState.LOADING -> {
                    setScaleEnable(false)
                    holdToSend.setText(processingTextId)
                    progressBar.changeIndeterminate(true)
                }
            }
        }
    }

    private fun updateProgress() {
        if (indicatorProgress > duration || !isPressedDown) {
            return
        }
        indicatorProgress += 16

        val progress = min(100, ((indicatorProgress / duration) * 100).toInt())

        binding.progressBar.progress = progress

        if (progress >= 100) {
            logd(TAG, "updateProgress() 100")
            changeState(ButtonState.VERIFICATION)
            return
        }

        if (progress < 100) {
            postDelayed(progressTask, 16)
        }
    }

    private fun verification() {
        uiScope {
            val isVerified = securityVerification(findActivity(this) as FragmentActivity)
            if (isVerified) {
                changeState(ButtonState.LOADING)
                onProcessing?.invoke()
            } else {
                changeState(ButtonState.DEFAULT)
            }
        }
    }

    companion object {
        private val TAG = SendButton::class.java.simpleName
    }
}

enum class ButtonState {
    DEFAULT,
    VERIFICATION,
    LOADING,
}

private fun CircularProgressIndicator.changeIndeterminate(isIndeterminate: Boolean) {
    setVisible(false)
    this.isIndeterminate = isIndeterminate
    setVisible(true)
}