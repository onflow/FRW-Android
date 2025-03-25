package com.flowfoundation.wallet.page.storage

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.ViewStorageInsufficientTipBinding
import com.flowfoundation.wallet.manager.account.model.StorageLimitDialogType
import com.flowfoundation.wallet.manager.account.model.ValidateTransactionResult
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.findActivity


class StorageInsufficientTip @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val binding = ViewStorageInsufficientTipBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
        binding.tvLearnMore.paintFlags = binding.tvLearnMore.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }

    fun setInsufficientTip(result: ValidateTransactionResult) {
        when (result) {
            ValidateTransactionResult.STORAGE_INSUFFICIENT -> {
                showTip(R.string.storage_limit_reached.res2String())
                setOnClickListener {
                    StorageLimitErrorDialog(context, StorageLimitDialogType.LIMIT_REACHED_WARNING).show()
                }
            }
            ValidateTransactionResult.BALANCE_INSUFFICIENT -> {
                showTip(R.string.flow_limit_reached.res2String())
                setOnClickListener {
                    openStorageLink()
                }
            }
            ValidateTransactionResult.STORAGE_INSUFFICIENT_AFTER_ACTION -> {
                showTip(R.string.storage_limit_reached_after_action.res2String())
                setOnClickListener {
                    StorageLimitErrorDialog(context, StorageLimitDialogType.LIMIT_AFTER_ACTION_WARNING).show()
                }
            }
            else -> this.gone()
        }
    }

    private fun showTip(tipStr: String) {
        binding.tvTip.text = tipStr
        visible()
    }

    private fun openStorageLink() {
        findActivity(this)?.let {
            openBrowser(it, "https://developers.flow.com/build/basics/fees#storage")
        }
    }

}