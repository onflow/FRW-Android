package com.flowfoundation.wallet.page.profile.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible

open class ProfilePreference : FrameLayout {

    @DrawableRes
    internal val icon: Int
    internal val iconEnable: Boolean
    internal val titleId: Int
    internal var subtitleId: Int
    internal var descId: Int
    private var desc: String

    @ColorInt
    internal var descColor: Int

    internal val isArrowVisible: Boolean

    private val rootView by lazy { findViewById<ViewGroup>(R.id.rootView) }
    private val iconView by lazy { findViewById<ImageView>(R.id.icon_view) }
    private val titleView by lazy { findViewById<TextView>(R.id.title_view) }
    private val subtitleView by lazy { findViewById<TextView>(R.id.subtitle_view) }
    val descView: TextView by lazy { findViewById(R.id.desc_view) }
    val arrowView: ImageView by lazy { findViewById(R.id.arrow_view) }
    private val extendContainer by lazy { findViewById<ViewGroup>(R.id.extend_container) }
    private val beginningContainer by lazy { findViewById<ViewGroup>(R.id.beginning_container) }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        val styleAttrs = context.obtainStyledAttributes(attrs, R.styleable.ProfilePreference, defStyleAttr, 0)
        iconEnable = styleAttrs.getBoolean(R.styleable.ProfilePreference_iconEnable, true)
        icon = styleAttrs.getResourceId(R.styleable.ProfilePreference_icon, 0)
        titleId = styleAttrs.getResourceId(R.styleable.ProfilePreference_titleId, 0)
        subtitleId = styleAttrs.getResourceId(R.styleable.ProfilePreference_subtitleId, 0)
        descId = styleAttrs.getResourceId(R.styleable.ProfilePreference_descId, 0)
        desc = styleAttrs.getString(R.styleable.ProfilePreference_desc).orEmpty()
        descColor = styleAttrs.getColor(R.styleable.ProfilePreference_descColor, R.color.note.res2color())
        isArrowVisible = styleAttrs.getBoolean(R.styleable.ProfilePreference_isArrowVisible, false)
        styleAttrs.recycle()

        LayoutInflater.from(context).inflate(R.layout.widget_profile_item, this)

        setup()
    }

    fun setExtendView(view: View, layoutParams: ViewGroup.LayoutParams? = null) {
        extendContainer.setVisible(true)
        extendContainer.addView(view, layoutParams ?: view.layoutParams)
    }

    fun setBeginningView(view: View, layoutParams: ViewGroup.LayoutParams? = null) {
        iconView.setVisible(false)
        beginningContainer.addView(view, layoutParams ?: view.layoutParams)
    }

    fun setDesc(desc: String) {
        this.desc = desc
        descView.text = desc
    }

    fun setDesc(descId: Int) {
        this.descId = descId
        if (descId != 0) {
            descView.setText(descId)
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        rootView.setOnClickListener(l)
    }

    private fun setup() {
        iconView.setVisible(iconEnable)
        if (icon != 0) {
            iconView.setImageResource(icon)
        }
        arrowView.setVisible(isArrowVisible)

        if (desc.isNotBlank()) {
            descView.text = desc
        } else {
            if (descId != 0) {
                descView.setText(descId)
            }
        }
        descView.setTextColor(descColor)
        if (titleId != 0) {
            titleView.setText(titleId)
        }
        if (subtitleId != 0) {
            subtitleView.visible()
            subtitleView.setText(subtitleId)
        } else {
            subtitleView.gone()
        }
    }
}