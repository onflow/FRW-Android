package com.flowfoundation.wallet.page.dialog.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogBackupTipsBinding
import com.flowfoundation.wallet.page.backup.BackupRecoveryPhraseActivity
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
        val isDriveRetired = arguments?.getBoolean(EXTRA_GOOGLE_DRIVE_RETIRED, false) == true
        with(binding) {
            root.requestFocus()
            if (isDriveRetired) {
                titleView.setText(R.string.google_drive_backup_retired_title)
                descView.setText(R.string.google_drive_backup_retired_desc)
            }

            llCheck.setOnClickListener {
                checkBox.isChecked = checkBox.isChecked.not()
                ioScope {
                    setDoNotShowBackupDialog(checkBox.isChecked)
                }
            }
            closeButton.setOnClickListener { dismiss() }
            skipButton.setOnClickListener { dismiss() }
            startButton.setOnClickListener {
                if (isDriveRetired) {
                    startActivity(BackupRecoveryPhraseActivity.createIntent(requireContext()))
                } else {
                    WalletBackupActivity.launch(requireContext())
                }
                dismiss()
            }
        }
    }

    companion object {
        private const val EXTRA_GOOGLE_DRIVE_RETIRED = "extra_google_drive_retired"

        /** Shown when Google Drive was the wallet's only backup and Drive backup is disabled. */
        fun showGoogleDriveRetired(fragmentManager: FragmentManager) {
            BackupTipsDialog().apply {
                arguments = Bundle().apply { putBoolean(EXTRA_GOOGLE_DRIVE_RETIRED, true) }
            }.showNow(fragmentManager, "")
        }

        fun show(fragmentManager: FragmentManager) {
            BackupTipsDialog().showNow(fragmentManager, "")
        }
    }
}