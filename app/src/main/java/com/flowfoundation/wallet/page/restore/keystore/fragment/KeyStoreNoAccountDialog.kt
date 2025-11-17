package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel

class KeyStoreNoAccountDialog(
    private val context: Context,
) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(KeyStoreNoAccountDialogView(context) { dialog?.cancel() })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
private class KeyStoreNoAccountDialogView(
    context: Context,
    private val onCancel: () -> Unit,
) : FrameLayout(context) {

    private val createButton by lazy { findViewById<View>(R.id.create_button) }
    private val cancelButton by lazy { findViewById<View>(R.id.cancel_button) }

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_keystore_no_account, this)

        createButton.setOnClickListener {
            onCancel()
            // Notify restoreViewModel to create new account
            // Get restoreViewModel instance and call corresponding method
            notifyRestoreViewModel()
        }
        cancelButton.setOnClickListener { onCancel() }
    }
    
    private fun notifyRestoreViewModel() {
        // Since we're in a Dialog, we need to get ViewModel through Activity
        val activity = context as? androidx.fragment.app.FragmentActivity
        activity?.let {
            val viewModel = ViewModelProvider(it)[KeyStoreRestoreViewModel::class.java]
            // Call method similar to importKeyStoreAddress to handle creating new account
            viewModel.createNewAccountFromKeystore()
        }
    }
}