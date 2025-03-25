package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.isProfileSwitchTipsShown
import com.flowfoundation.wallet.utils.setProfileSwitchTipsShown
import kotlinx.coroutines.launch

class ProfileSwitchTipsBanner : FrameLayout, LifecycleOwner {

    private var isViewAttached = false
    private var _isTipsShown = false
    private val isTipsShown: Boolean
        get() = _isTipsShown

    override val lifecycle: Lifecycle
        get() = (context as? LifecycleOwner)?.lifecycle 
            ?: throw IllegalStateException("Context must implement LifecycleOwner")

    constructor(context: Context) : super(context) {
        init()
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }
    
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        setVisible(false)
        checkAndShowTips()
    }

    private fun checkAndShowTips() {
        if (!isViewAttached) return
        
        lifecycleScope.launch {
            try {
                _isTipsShown = isProfileSwitchTipsShown()
                if (!isTipsShown) {
                    setup()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking tips status", e)
                setVisible(false)
            }
        }
    }

    private fun setup() {
        try {
            setVisible(true)
            LayoutInflater.from(context).inflate(R.layout.view_profile_tips_banner, this, true)

            findViewById<View>(R.id.close_button)?.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        setProfileSwitchTipsShown()
                        setVisible(false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving tips status", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up tips banner", e)
            setVisible(false)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isViewAttached = true
        if (!isTipsShown) {
            checkAndShowTips()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isViewAttached = false
    }

    companion object {
        private const val TAG = "ProfileSwitchTipsBanner"
    }
}