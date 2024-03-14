package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentBackupGoogleDriveBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupGoogleDriveState
import com.flowfoundation.wallet.page.backup.multibackup.presenter.BackupGoogleDrivePresenter
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupGoogleDriveViewModel


class BackupGoogleDriveFragment : Fragment() {

    private lateinit var binding: FragmentBackupGoogleDriveBinding
    private lateinit var presenter: BackupGoogleDrivePresenter
    private lateinit var viewModel: BackupGoogleDriveViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupGoogleDriveBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter = BackupGoogleDrivePresenter(this, binding)
        viewModel = ViewModelProvider(this)[BackupGoogleDriveViewModel::class.java].apply {
            backupStateLiveData.observe(viewLifecycleOwner) {
                presenter.bind(it)
            }
            uploadMnemonicLiveData.observe(viewLifecycleOwner) {
                presenter.uploadMnemonic(it)
            }
        }
        presenter.bind(BackupGoogleDriveState.CREATE_BACKUP)
    }

}