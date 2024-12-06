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
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.receive.ReceiveActivity
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog

class StorageLimitErrorDialog(private val context: Context) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(StorageLimitErrorDialogView(context) { dialog?.cancel() })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
class StorageLimitErrorDialogView(context: Context, private val onCancel:() -> Unit): FrameLayout(context) {
    private val binding = DialogStorageLimitErrorBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)

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