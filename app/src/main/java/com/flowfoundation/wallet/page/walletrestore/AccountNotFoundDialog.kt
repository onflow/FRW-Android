package com.flowfoundation.wallet.page.walletrestore

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.walletcreate.WalletCreateActivity
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

    private val createButton by lazy { findViewById<View>(R.id.create_button) }
    private val cancelButton by lazy { findViewById<View>(R.id.cancel_button) }

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_account_not_found, this)

        createButton.setOnClickListener {
            onCancel()
            (context as? Activity)?.finish()
            Wallet.store().updateMnemonic(mnemonic).store()
            if (isTestnet() || isPreviewnet()) {
                SwitchNetworkDialog(context, DialogType.CREATE).show()
            } else {
                WalletCreateActivity.launch(context)
            }
        }
        cancelButton.setOnClickListener { onCancel() }
    }
}

