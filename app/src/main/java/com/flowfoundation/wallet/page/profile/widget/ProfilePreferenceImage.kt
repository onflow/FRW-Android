package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.loadAvatar

class ProfilePreferenceImage : ProfilePreference {

    private val imageView by lazy { LayoutInflater.from(context).inflate(R.layout.view_profile_preference_image, this, false) as ImageView }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        setExtendView(imageView)
    }

    fun setImageUrl(imageUrl: String) {
        imageView.loadAvatar(imageUrl)
    }
}