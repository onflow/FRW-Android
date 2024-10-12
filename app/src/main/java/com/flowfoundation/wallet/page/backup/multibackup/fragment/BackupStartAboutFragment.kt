package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentBackupStartAboutBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupStartOption
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupStartWithAboutViewModel


class BackupStartAboutFragment : Fragment() {
    private lateinit var binding: FragmentBackupStartAboutBinding
    private val viewModel by lazy {
        ViewModelProvider(requireParentFragment())[BackupStartWithAboutViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupStartAboutBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            btnOk.setOnClickListener {
                viewModel.changeOption(BackupStartOption.BACKUP_START)
            }
        }
    }

}