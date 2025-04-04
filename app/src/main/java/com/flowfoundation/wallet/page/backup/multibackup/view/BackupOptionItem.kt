package com.flowfoundation.wallet.page.backup.multibackup.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.setVisible


class BackupOptionItem : FrameLayout {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        val array =
            context.obtainStyledAttributes(attrs, R.styleable.BackupOptionItem, defStyleAttr, 0)
        val image = array.getResourceId(R.styleable.BackupOptionItem_option_item_icon, 0)
        val nameId = array.getResourceId(R.styleable.BackupOptionItem_option_item_name, 0)
        array.recycle()

        LayoutInflater.from(context).inflate(R.layout.layout_backup_option_item, this)
        val imageView = findViewById<ImageView>(R.id.iv_option_icon)
        val textView = findViewById<TextView>(R.id.tv_option_title)

        imageView.setImageResource(image)
        if (nameId != 0) {
            textView.setText(nameId)
        }
        changeItemStatus(false)
    }

    fun changeItemStatus(isSelected: Boolean) {
        findViewById<View>(R.id.view_selected).setVisible(isSelected)
        findViewById<ImageView>(R.id.iv_selected).setVisible(isSelected)
    }
}