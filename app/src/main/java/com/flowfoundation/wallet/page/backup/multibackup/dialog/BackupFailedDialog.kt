package com.flowfoundation.wallet.page.backup.multibackup.dialog

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity


class BackupFailedDialog(
    private val context: Context
) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(BackupFailedDialogView(context) { dialog?.cancel() })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
private class BackupFailedDialogView(
    context: Context,
    private val onCancel: () -> Unit,
) : FrameLayout(context) {
    private val closeButton by lazy { findViewById<View>(R.id.close_button) }

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_backup_failed, this)
        closeButton.setOnClickListener {
            BaseActivity.getCurrentActivity()?.let {
                onCancel()
                it.finish()
            } ?: run {
                onCancel()
            }
        }
    }
}