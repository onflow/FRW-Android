package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentBackupRecoveryPhraseWarningBinding
import com.flowfoundation.wallet.page.backup.multibackup.dialog.BackupAboutDialog
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupAbout
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupRecoveryPhraseOption
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupRecoveryPhraseViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.toast

class BackupRecoveryPhraseWarningFragment : Fragment() {

    private lateinit var binding: FragmentBackupRecoveryPhraseWarningBinding

    private lateinit var viewModel: BackupRecoveryPhraseViewModel

    private val backupViewModel by lazy {
        ViewModelProvider(requireParentFragment().requireActivity())[MultiBackupViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBackupRecoveryPhraseWarningBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireParentFragment())[BackupRecoveryPhraseViewModel::class.java].apply {
            createBackupCallbackLiveData.observe(viewLifecycleOwner) { isSuccess ->
                if (isSuccess) {
                    changeOption(BackupRecoveryPhraseOption.BACKUP_RECOVERY_PHRASE)
                } else {
                    toast(msgRes = R.string.backup_failed)
                    binding.createButton.setProgressVisible(false)
                }
            }
        }
        with(binding) {
            backupProgress.setProgressInfo(backupViewModel.getBackupOptionList(), BackupOption.BACKUP_WITH_RECOVERY_PHRASE, false)
            warningCheck1.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            warningCheck2.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            warningCheck3.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            tvLearnMore.setOnClickListener {
                BackupAboutDialog().show(childFragmentManager, BackupAbout.ABOUT_RECOVERY_PHRASE)
            }
            createButton.setOnClickListener {
                createBackup()
            }
        }

    }

    private fun createBackup() {
        binding.createButton.setProgressVisible(true)
        viewModel.uploadToChainAndSync()
    }

    private fun onCheckChanged() {
        val uncheckedColor = R.color.border_3.res2color()
        val checkedColor = R.color.colorSecondary.res2color()
        with(binding) {
            val isConfirmed = warningCheck1.isChecked && warningCheck2.isChecked && warningCheck3.isChecked
            createButton.isEnabled = isConfirmed
            warning1.backgroundTintList = ColorStateList.valueOf(if (warningCheck1.isChecked) checkedColor else uncheckedColor)
            warning2.backgroundTintList = ColorStateList.valueOf(if (warningCheck2.isChecked) checkedColor else uncheckedColor)
            warning3.backgroundTintList = ColorStateList.valueOf(if (warningCheck3.isChecked) checkedColor else uncheckedColor)
        }
    }


}