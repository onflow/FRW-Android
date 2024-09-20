package com.flowfoundation.wallet.page.dialog.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogBackupTipsBinding
import com.flowfoundation.wallet.page.backup.WalletBackupActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.setDoNotShowBackupDialog

class BackupTipsDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogBackupTipsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogBackupTipsBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            root.requestFocus()

            llCheck.setOnClickListener {
                checkBox.isChecked = checkBox.isChecked.not()
                ioScope {
                    setDoNotShowBackupDialog(checkBox.isChecked)
                }
            }
            closeButton.setOnClickListener { dismiss() }
            skipButton.setOnClickListener { dismiss() }
            startButton.setOnClickListener {
                WalletBackupActivity.launch(requireContext())
                dismiss()
            }
        }
    }

    companion object {

        fun show(fragmentManager: FragmentManager) {
            BackupTipsDialog().showNow(fragmentManager, "")
        }
    }
}