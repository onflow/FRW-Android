package io.outblock.lilico.page.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import io.outblock.lilico.R
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.isRooted

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