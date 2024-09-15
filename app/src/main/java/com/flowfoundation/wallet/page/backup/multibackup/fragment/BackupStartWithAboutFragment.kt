package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentBackupStartWithAboutBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupStartOption
import com.flowfoundation.wallet.page.backup.multibackup.presenter.BackupStartWithAboutPresenter
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupStartWithAboutViewModel


class BackupStartWithAboutFragment: Fragment() {
    private lateinit var binding: FragmentBackupStartWithAboutBinding
    private lateinit var viewModel: BackupStartWithAboutViewModel
    private lateinit var presenter: BackupStartWithAboutPresenter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupStartWithAboutBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter = BackupStartWithAboutPresenter(this)
        viewModel = ViewModelProvider(this)[BackupStartWithAboutViewModel::class.java].apply {
            optionChangeLiveData.observe(viewLifecycleOwner) {
                presenter.bind(it)
            }
            changeOption(BackupStartOption.BACKUP_ABOUT)
        }
    }
}