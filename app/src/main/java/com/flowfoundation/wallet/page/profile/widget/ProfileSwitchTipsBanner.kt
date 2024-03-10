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

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        setVisible(false)
        ioScope {
            if (!isProfileSwitchTipsShown()) {
                setup()
            }
        }
    }

    private fun setup() {
        setVisible(true)
        LayoutInflater.from(context).inflate(R.layout.view_profile_tips_banner, this)

        findViewById<View>(R.id.close_button).setOnClickListener {
            ioScope { setProfileSwitchTipsShown() }
            setVisible(false)
        }
    }
}