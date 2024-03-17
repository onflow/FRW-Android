package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible

class ProfilePreferenceMark : ProfilePreference {

    private val markView by lazy { LayoutInflater.from(context).inflate(R.layout.view_profile_preference_mark, this, false) as TextView }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        setExtendView(markView)
        markView.setVisible(false)
        descView.setVisible(false)
    }

    fun setMarkText(markText: String) {
        markView.setVisible(markText.isNotBlank())
        markView.text = markText
        if (arrowView.visibility == View.VISIBLE) {
            markView.updateLayoutParams<LayoutParams> {
                this.setMargins(0, 0, 8.dp2px().toInt(), 0)
            }
        }
    }
}