package com.flowfoundation.wallet.widgets.webview.evm.dialog

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.ViewTransactionParameterBinding


class TransactionParameterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewTransactionParameterBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    fun setData(data: String) {
        binding.tvValue.text = data
    }

    fun setData(key: String, value: String) {
        binding.tvKey.text = key
        binding.tvValue.text = value
    }
}