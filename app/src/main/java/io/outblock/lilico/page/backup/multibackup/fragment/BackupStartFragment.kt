package io.outblock.lilico.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.outblock.lilico.databinding.FragmentBackupStartBinding
import io.outblock.lilico.page.backup.BackupListManager
import io.outblock.lilico.page.backup.model.BackupType
import io.outblock.lilico.page.backup.multibackup.model.BackupOption
import io.outblock.lilico.page.backup.multibackup.viewmodel.MultiBackupViewModel
import io.outblock.lilico.utils.extensions.setVisible


class BackupStartFragment : Fragment() {
    private lateinit var binding: FragmentBackupStartBinding
    private val backupViewModel by lazy {
        ViewModelProvider(requireActivity())[MultiBackupViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupStartBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            btnNext.setVisible(false)
            btnNext.setOnClickListener {
                backupViewModel.startBackup()
            }
            if (BackupListManager.hadBackupOption(BackupType.GOOGLE_DRIVE)) {
                oiGoogleDrive.changeItemStatus(true)
            } else {
                oiGoogleDrive.setOnClickListener {
                    backupViewModel.selectOption(BackupOption.BACKUP_WITH_GOOGLE_DRIVE) { isSelected ->
                        oiGoogleDrive.changeItemStatus(isSelected)
                    }
                    checkBackupValid()
                }
            }
            if (BackupListManager.hadBackupOption(BackupType.MANUAL)) {
                oiRecoveryPhrase.changeItemStatus(true)
            } else {
                oiRecoveryPhrase.setOnClickListener {
                    backupViewModel.selectOption(BackupOption.BACKUP_WITH_RECOVERY_PHRASE) { isSelected ->
                        oiRecoveryPhrase.changeItemStatus(isSelected)
                    }
                    checkBackupValid()
                }
            }
            if (backupViewModel.getBackupOptionList().isEmpty() && BackupListManager.backupCount() == 0) {
                backupViewModel.selectOption(BackupOption.BACKUP_WITH_GOOGLE_DRIVE) { isSelected ->
                    oiGoogleDrive.changeItemStatus(isSelected)
                }
                backupViewModel.selectOption(BackupOption.BACKUP_WITH_RECOVERY_PHRASE) { isSelected ->
                    oiRecoveryPhrase.changeItemStatus(isSelected)
                }
                checkBackupValid()
            }
        }
    }

    private fun checkBackupValid() {
        binding.btnNext.setVisible(backupViewModel.isBackupValid())
    }
}