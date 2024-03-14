package com.flowfoundation.wallet.page.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isRooted

class RootDetectedBanner : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        setVisible(false)
        ioScope {
            if (isRooted()) {
                setup()
            }
        }
    }

    private fun setup() {
        setVisible(true)
        LayoutInflater.from(context).inflate(R.layout.view_root_detected_banner, this)

        findViewById<View>(R.id.root_view).setOnClickListener { RootAlertActivity.launch(context) }
    }
}