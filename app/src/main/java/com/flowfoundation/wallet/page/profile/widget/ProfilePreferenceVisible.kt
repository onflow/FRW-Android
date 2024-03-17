package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.setVisible

class ProfilePreferenceVisible : ProfilePreference {

    private val extendView by lazy { LayoutInflater.from(context).inflate(R.layout.view_profile_preference_visible, this, false) as ViewGroup }

    private val visibleWrapper by lazy { findViewById<ViewGroup>(R.id.visible_wrapper) }
    private val invisibleWrapper by lazy { findViewById<ViewGroup>(R.id.invisible_wrapper) }

    private val visibleStateView by lazy { findViewById<View>(R.id.visible_check_state_view) }
    private val invisibleStateView by lazy { findViewById<View>(R.id.invisible_check_state_view) }

    private var onStateChangeListener: ((isVisible: Boolean) -> Unit)? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        setExtendView(extendView)

        visibleWrapper.setOnClickListener {
            onStateChangeListener?.invoke(true)
        }
        invisibleWrapper.setOnClickListener {
            onStateChangeListener?.invoke(false)
        }
    }

    fun icons(@DrawableRes visible: Int, @DrawableRes invisible: Int) {
        extendView.findViewById<ImageView>(R.id.visible_icon_view).setImageResource(visible)
        extendView.findViewById<ImageView>(R.id.invisible_icon_view).setImageResource(invisible)
    }

    fun updateState(isVisible: Boolean) {
        visibleStateView.setVisible(isVisible)
        invisibleStateView.setVisible(!isVisible)
    }

    fun setOnStateChangeListener(listener: (isVisible: Boolean) -> Unit) {
        onStateChangeListener = listener
    }
}