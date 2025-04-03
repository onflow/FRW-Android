package com.flowfoundation.wallet.page.storage

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.DialogStorageLimitErrorBinding
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.model.StorageLimitDialogType
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.receive.ReceiveActivity
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog
import com.flowfoundation.wallet.utils.extensions.res2String

class StorageLimitErrorDialog(private val context: Context, private val type: StorageLimitDialogType) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(StorageLimitErrorDialogView(context, type) { dialog?.cancel() })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
class StorageLimitErrorDialogView(context: Context, type: StorageLimitDialogType, private val onCancel:() -> Unit):
    FrameLayout(context) {
    private val binding = DialogStorageLimitErrorBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
        when (type) {
            StorageLimitDialogType.LIMIT_REACHED_WARNING -> {
                binding.tvTitle.text = R.string.storage_limit_warning.res2String()
                binding.tvDesc.text = R.string.storage_limit_reached_desc.res2String()
            }
            StorageLimitDialogType.LIMIT_AFTER_ACTION_WARNING -> {
                binding.tvTitle.text = R.string.storage_limit_warning.res2String()
                binding.tvDesc.text = R.string.storage_limit_after_action_desc.res2String()
            }
            StorageLimitDialogType.LIMIT_REACHED_ERROR -> {
                binding.tvTitle.text = R.string.storage_limit_error.res2String()
                binding.tvDesc.text = R.string.storage_limit_error_desc.res2String()
            }
        }
        binding.tvTips.text = context.getString(R.string.storage_limit_error_tips, AccountInfoManager.getLeastFlowBalance())
        binding.ivClose.setOnClickListener { onCancel() }
        binding.tvLearnMore.paintFlags = binding.tvLearnMore.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvLearnMore.setOnClickListener {
            BaseActivity.getCurrentActivity()?.let {
                openBrowser(it, "https://developers.flow.com/build/basics/fees#storage")
            }
        }
        binding.btnDeposit.setOnClickListener {
            ReceiveActivity.launch(context)
        }

        binding.btnBuy.setOnClickListener {
            BaseActivity.getCurrentActivity()?.let {
                SwapDialog.show(it.supportFragmentManager)
            }
        }
    }
}