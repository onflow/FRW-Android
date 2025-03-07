package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentBackupDropboxWithPinBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupDropboxOption
import com.flowfoundation.wallet.page.backup.multibackup.presenter.BackupDropboxWithPinPresenter
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupDropboxWithPinViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel


class BackupDropboxWithPinFragment: Fragment() {
    private lateinit var binding: FragmentBackupDropboxWithPinBinding
    private lateinit var withPinPresenter: BackupDropboxWithPinPresenter
    private lateinit var withPinViewModel: BackupDropboxWithPinViewModel

    private val backupViewModel by lazy {
        ViewModelProvider(requireActivity())[MultiBackupViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupDropboxWithPinBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        withPinPresenter = BackupDropboxWithPinPresenter(this)
        withPinViewModel = ViewModelProvider(this)[BackupDropboxWithPinViewModel::class.java].apply {
            optionChangeLiveData.observe(viewLifecycleOwner) {
                withPinPresenter.bind(it)
            }
            backupFinishLiveData.observe(viewLifecycleOwner) {
                withPinPresenter.toNext(it)
            }
            changeOption(BackupDropboxOption.BACKUP_PIN)
            setCurrentIndex(backupViewModel.getCurrentIndex())
        }
    }
}