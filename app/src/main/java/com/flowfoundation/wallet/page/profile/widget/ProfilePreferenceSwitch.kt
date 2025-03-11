package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.uiScope

class ProfilePreferenceSwitch : ProfilePreference {

    private val switchView by lazy { LayoutInflater.from(context).inflate(R.layout.view_settings_switch, this, false) as SwitchCompat }

    private var onCheckedChangeListener: ((isChecked: Boolean) -> Unit)? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        setExtendView(switchView, ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        setOnClickListener { toggleSwitch() }
    }

    fun setChecked(isChecked: Boolean, withAnimation: Boolean = false) {
        uiScope {
            switchView.isChecked = isChecked
            if (!withAnimation) switchView.jumpDrawablesToCurrentState()
        }
    }

    fun isChecked() = switchView.isChecked

    fun setOnCheckedChangeListener(listener: (isChecked: Boolean) -> Unit) {
        this.onCheckedChangeListener = listener
    }

    private fun toggleSwitch() {
        switchView.isChecked = !switchView.isChecked
        onCheckedChangeListener?.invoke(switchView.isChecked)
    }
}