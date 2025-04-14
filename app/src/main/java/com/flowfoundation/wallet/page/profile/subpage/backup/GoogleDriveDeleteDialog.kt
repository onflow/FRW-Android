package com.flowfoundation.wallet.page.profile.subpage.backup

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.drive.GoogleDriveAuthActivity

class GoogleDriveDeleteDialog(
    private val context: Context,
    private val onDeleting: () -> Unit,
) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(DialogView(context, onCancel = { dialog?.cancel() }) {
                dialog?.cancel()
                onDeleting.invoke()
            })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
private class DialogView(
    context: Context,
    private val onCancel: () -> Unit,
    private val onAction: () -> Unit,
) : FrameLayout(context) {

    private val createButton by lazy { findViewById<View>(R.id.create_button) }
    private val cancelButton by lazy { findViewById<View>(R.id.cancel_button) }

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_delete_google_drive_backup, this)

        createButton.setOnClickListener {
            onAction()
            GoogleDriveAuthActivity.deleteBackup(context)
        }
        cancelButton.setOnClickListener { onCancel() }
    }
}

