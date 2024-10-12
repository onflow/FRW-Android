package com.flowfoundation.wallet.page.backup.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentBackupSeedPhraseWarningBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupSeedPhraseOption
import com.flowfoundation.wallet.page.backup.viewmodel.BackupSeedPhraseViewModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.toast

class BackupSeedPhraseWarningFragment : Fragment() {

    private lateinit var binding: FragmentBackupSeedPhraseWarningBinding

    private lateinit var pageViewModel: BackupSeedPhraseViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBackupSeedPhraseWarningBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pageViewModel = ViewModelProvider(requireActivity())[BackupSeedPhraseViewModel::class.java].apply {
            createBackupCallbackLiveData.observe(viewLifecycleOwner) { isSuccess ->
                if (isSuccess) {
                    changeOption(BackupSeedPhraseOption.BACKUP_SEED_PHRASE)
                } else {
                    toast(msgRes = R.string.backup_failed)
                    binding.nextButton.setProgressVisible(false)
                }
            }
        }
        with(binding) {
            warningCheck1.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            warningCheck2.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            warningCheck3.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            nextButton.text = R.string.create_backup.res2String()
            nextButton.setOnClickListener {
                createBackup()
            }
        }

    }

    private fun createBackup() {
        binding.nextButton.setProgressVisible(true)
        pageViewModel.uploadToChain()
    }

    private fun onCheckChanged() {
        val uncheckedColor = R.color.border_3.res2color()
        val checkedColor = R.color.colorSecondary.res2color()
        with(binding) {
            val isConfirmed = warningCheck1.isChecked && warningCheck2.isChecked && warningCheck3.isChecked
            nextButton.isEnabled = isConfirmed
            warning1.backgroundTintList = ColorStateList.valueOf(if (warningCheck1.isChecked) checkedColor else uncheckedColor)
            warning2.backgroundTintList = ColorStateList.valueOf(if (warningCheck2.isChecked) checkedColor else uncheckedColor)
            warning3.backgroundTintList = ColorStateList.valueOf(if (warningCheck3.isChecked) checkedColor else uncheckedColor)
        }
    }


}