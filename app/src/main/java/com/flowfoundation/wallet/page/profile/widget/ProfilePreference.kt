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
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible

open class ProfilePreference : FrameLayout {

    @DrawableRes
    internal val icon: Int
    internal val iconEnable: Boolean
    internal val title: String
    internal var subtitle: String
    internal var desc: String

    @ColorInt
    internal var descColor: Int

    internal val isArrowVisible: Boolean

    private val rootView by lazy { findViewById<ViewGroup>(R.id.rootView) }
    private val iconView by lazy { findViewById<ImageView>(R.id.icon_view) }
    private val titleView by lazy { findViewById<TextView>(R.id.title_view) }
    private val subtitleView by lazy { findViewById<TextView>(R.id.subtitle_view) }
    val descView: TextView by lazy { findViewById(R.id.desc_view) }
    private val arrowView by lazy { findViewById<View>(R.id.arrow_view) }
    private val extendContainer by lazy { findViewById<ViewGroup>(R.id.extend_container) }
    private val beginningContainer by lazy { findViewById<ViewGroup>(R.id.beginning_container) }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        val styleAttrs = context.obtainStyledAttributes(attrs, R.styleable.ProfilePreference, defStyleAttr, 0)
        iconEnable = styleAttrs.getBoolean(R.styleable.ProfilePreference_iconEnable, true)
        icon = styleAttrs.getResourceId(R.styleable.ProfilePreference_icon, 0)
        title = styleAttrs.getString(R.styleable.ProfilePreference_title).orEmpty()
        subtitle = styleAttrs.getString(R.styleable.ProfilePreference_subtitle).orEmpty()
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

    fun setSubtitle(subtitle: String) {
        this.subtitle = subtitle
        subtitleView.setVisible(subtitle.isNotBlank())
        subtitleView.text = subtitle
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

        descView.text = desc
        descView.setTextColor(descColor)
        titleView.text = title
        subtitleView.setVisible(subtitle.isNotBlank())
        subtitleView.text = subtitle
    }
}