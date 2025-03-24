package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isProfileSwitchTipsShown
import com.flowfoundation.wallet.utils.setProfileSwitchTipsShown

class ProfileSwitchTipsBanner : FrameLayout {

    constructor(context: Context) : super(context) {
        init(context)
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }
    
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    private fun init(context: Context) {
        setVisible(false)
        if (!isProfileSwitchTipsShown()) {
            setup(context)
        }
    }

    private fun setup(context: Context) {
        try {
            setVisible(true)
            LayoutInflater.from(context).inflate(R.layout.view_profile_tips_banner, this, true)

            findViewById<View>(R.id.close_button)?.setOnClickListener {
                ioScope { setProfileSwitchTipsShown() }
                setVisible(false)
            }
        } catch (e: Exception) {
            // Log the error but don't crash
            e.printStackTrace()
            setVisible(false)
        }
    }
}