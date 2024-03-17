package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentBackupGoogleDriveWithPinBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupGoogleDriveOption
import com.flowfoundation.wallet.page.backup.multibackup.presenter.BackupGoogleDriveWithPinPresenter
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupGoogleDriveWithPinViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel


class BackupGoogleDriveWithPinFragment: Fragment() {
    private lateinit var binding: FragmentBackupGoogleDriveWithPinBinding
    private lateinit var withPinPresenter: BackupGoogleDriveWithPinPresenter
    private lateinit var withPinViewModel: BackupGoogleDriveWithPinViewModel

    private val backupViewModel by lazy {
        ViewModelProvider(requireActivity())[MultiBackupViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupGoogleDriveWithPinBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        withPinPresenter = BackupGoogleDriveWithPinPresenter(this)
        withPinViewModel = ViewModelProvider(this)[BackupGoogleDriveWithPinViewModel::class.java].apply {
            optionChangeLiveData.observe(viewLifecycleOwner) {
                withPinPresenter.bind(it)
            }
            backupFinishLiveData.observe(viewLifecycleOwner) {
                withPinPresenter.toNext()
            }
            changeOption(BackupGoogleDriveOption.BACKUP_PIN)
            setCurrentIndex(backupViewModel.getCurrentIndex())
        }
    }
}