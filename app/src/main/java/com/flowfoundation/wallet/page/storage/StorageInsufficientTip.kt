package com.flowfoundation.wallet.page.storage

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.ViewStorageInsufficientTipBinding
import com.flowfoundation.wallet.manager.account.model.ValidateTransactionResult
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.visible


class StorageInsufficientTip @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val binding = ViewStorageInsufficientTipBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    fun setInsufficientTip(result: ValidateTransactionResult) {
        when (result) {
            ValidateTransactionResult.STORAGE_INSUFFICIENT -> showTip(R.string.storage_limit_reached.res2String())
            ValidateTransactionResult.BALANCE_INSUFFICIENT -> showTip(R.string.flow_limit_reached.res2String())
            else -> this.gone()
        }
    }

    private fun showTip(tipStr: String) {
        binding.tvTip.text = tipStr
        setOnClickListener {
            //todo learn more
        }
        visible()
    }

}