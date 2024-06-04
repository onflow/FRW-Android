package com.flowfoundation.wallet.page.walletrestore

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_USERNAME
import com.flowfoundation.wallet.page.walletcreate.WalletCreateActivity
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog

class AccountNotFoundDialog(
    private val context: Context,
    private val mnemonic: String,
) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(AccountNotFoundDialogView(context, mnemonic) { dialog?.cancel() })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
private class AccountNotFoundDialogView(
    context: Context,
    private val mnemonic: String,
    private val onCancel: () -> Unit,
) : FrameLayout(context) {

    private val descView by lazy { findViewById<TextView>(R.id.desc_view) }
    private val createButton by lazy { findViewById<View>(R.id.create_button) }
    private val cancelButton by lazy { findViewById<View>(R.id.cancel_button) }

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_account_not_found, this)

        descView.text = SpannableString(R.string.account_not_found_desc.res2String()).apply {
            val protection = R.string.your_phrase.res2String()
            val index = indexOf(protection)
            setSpan(ForegroundColorSpan(R.color.colorSecondary.res2color()), index, index + protection.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        createButton.setOnClickListener {
            onCancel()
            (context as? Activity)?.finish()
            Wallet.store().updateMnemonic(mnemonic).store()
            if (isPreviewnet()) {
                SwitchNetworkDialog(context, DialogType.CREATE).show()
            } else {
                WalletCreateActivity.launch(context)
            }
        }
        cancelButton.setOnClickListener { onCancel() }
    }
}

