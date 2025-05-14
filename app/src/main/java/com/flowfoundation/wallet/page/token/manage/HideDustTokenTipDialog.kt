package com.flowfoundation.wallet.page.token.manage

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.flowfoundation.wallet.R


class HideDustTokenTipDialog(private val context: Context) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(HideDustTokenTipView(context) { dialog?.cancel() })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
private class HideDustTokenTipView(context: Context, private val onDismiss: () -> Unit): FrameLayout(context) {
    private val closeButton by lazy { findViewById<View>(R.id.close_button) }

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_hide_dust_token_tip, this)
        closeButton.setOnClickListener {
            onDismiss()
        }
    }
}