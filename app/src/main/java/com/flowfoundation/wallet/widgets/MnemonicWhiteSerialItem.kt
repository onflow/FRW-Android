package com.flowfoundation.wallet.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicModel

class MnemonicWhiteSerialItem : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    private val indexView by lazy { findViewById<TextView>(R.id.index_view) }
    private val textView by lazy { findViewById<TextView>(R.id.text_view) }

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_mnemonic_item_white_serial, this)
    }

    fun setText(text: MnemonicModel) {
        indexView.text = "${text.index}"
        textView.text = text.text
    }
}