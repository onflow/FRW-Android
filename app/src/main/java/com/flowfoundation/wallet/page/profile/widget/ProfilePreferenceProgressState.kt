package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.setVisible

class ProfilePreferenceProgressState : ProfilePreference {

    private val stateView by lazy { LayoutInflater.from(context).inflate(R.layout.view_profile_preference_progress_state, this, false) }

    private val progressBar by lazy { stateView.findViewById<ProgressBar>(R.id.progress_bar) }
    private val checkedView by lazy { stateView.findViewById<View>(R.id.checked_view) }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        setExtendView(stateView)
        stateView.setVisible(false)
    }

    fun showProgress() {
        stateView.setVisible(true)
        progressBar.setVisible(true)
        descView.setVisible(false)
    }

    fun setChecked(isChecked: Boolean) {
        stateView.setVisible(isChecked)
        descView.setVisible(!isChecked)
        checkedView.setVisible(isChecked)
        progressBar.setVisible(false)
    }
}