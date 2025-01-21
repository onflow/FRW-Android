package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentBackupDropboxBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupDropboxState
import com.flowfoundation.wallet.page.backup.multibackup.presenter.BackupDropboxPresenter
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupDropboxViewModel


class BackupDropboxFragment: Fragment() {
    private lateinit var binding: FragmentBackupDropboxBinding
    private lateinit var presenter: BackupDropboxPresenter
    private lateinit var viewModel: BackupDropboxViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupDropboxBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter = BackupDropboxPresenter(this, binding)

        viewModel = ViewModelProvider(this)[BackupDropboxViewModel::class.java].apply {
            backupStateLiveData.observe(viewLifecycleOwner) {
                presenter.bind(it)
            }
            uploadMnemonicLiveData.observe(viewLifecycleOwner) {
                presenter.uploadMnemonic(it)
            }
        }
        presenter.bind(BackupDropboxState.CREATE_BACKUP)
    }
}