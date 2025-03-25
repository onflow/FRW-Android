package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentBackupRecoveryPhraseBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupRecoveryPhraseOption
import com.flowfoundation.wallet.page.backup.multibackup.presenter.BackupRecoveryPhrasePresenter
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupRecoveryPhraseViewModel


class BackupRecoveryPhraseFragment : Fragment() {

    private lateinit var binding: FragmentBackupRecoveryPhraseBinding
    private lateinit var viewModel: BackupRecoveryPhraseViewModel
    private lateinit var presenter: BackupRecoveryPhrasePresenter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupRecoveryPhraseBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter = BackupRecoveryPhrasePresenter(this)
        viewModel = ViewModelProvider(this)[BackupRecoveryPhraseViewModel::class.java].apply {
            optionChangeLiveData.observe(viewLifecycleOwner) {
                presenter.bind(it)
            }
            changeOption(BackupRecoveryPhraseOption.BACKUP_WARING)
        }
    }

}