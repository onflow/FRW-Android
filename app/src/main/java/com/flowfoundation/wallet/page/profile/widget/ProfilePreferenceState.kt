package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.setVisible

class ProfilePreferenceState : ProfilePreference {

    private val stateView by lazy { LayoutInflater.from(context).inflate(R.layout.view_profile_preference_state, this, false) }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        setExtendView(stateView)
        stateView.setVisible(false)
    }

    fun setStateVisible(isStateVisible: Boolean) {
        stateView.setVisible(isStateVisible)
        descView.setVisible(!isStateVisible)
    }
}