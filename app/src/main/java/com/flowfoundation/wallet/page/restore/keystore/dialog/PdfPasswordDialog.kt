package com.flowfoundation.wallet.page.restore.keystore.dialog

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.widget.doOnTextChanged
import com.flowfoundation.wallet.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * Dialog for entering password to unlock password-protected PDF files
 */
class PdfPasswordDialog(
    private val context: Context,
    private val pdfFilePath: String,
    private val onUnlock: (password: String) -> Unit,
    private val onCancel: () -> Unit = {}
) {
    private var dialog: Dialog? = null

    fun show() {
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(PdfPasswordDialogView(context, pdfFilePath, { password ->
                dialog?.dismiss()
                onUnlock(password)
            }, {
                dialog?.dismiss()
                onCancel()
            }))
            with(create()) {
                dialog = this
                setCanceledOnTouchOutside(false)
                show()
            }
        }
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}

@SuppressLint("ViewConstructor")
class PdfPasswordDialogView(
    context: Context,
    private val pdfFilePath: String,
    private val onUnlock: (password: String) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {

    private val etPassword by lazy { findViewById<TextInputEditText>(R.id.et_password) }
    private val cancelButton by lazy { findViewById<MaterialButton>(R.id.cancel_button) }
    private val unlockButton by lazy { findViewById<MaterialButton>(R.id.unlock_button) }

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_pdf_password, this)

        // Initially disable unlock button until password is entered
        unlockButton.isEnabled = false

        etPassword.doOnTextChanged { text, _, _, _ ->
            unlockButton.isEnabled = !text.isNullOrEmpty()
        }

        cancelButton.setOnClickListener {
            hideKeyboard()
            onCancel()
        }

        unlockButton.setOnClickListener {
            hideKeyboard()
            val password = etPassword.text?.toString()?.trim() ?: ""
            if (password.isNotEmpty()) {
                onUnlock(password)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etPassword.windowToken, 0)
    }
}

