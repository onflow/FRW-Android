package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentBackupCompletedBinding
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel
import com.flowfoundation.wallet.utils.extensions.setVisible


class BackupCompletedFragment : Fragment() {

    private lateinit var binding: FragmentBackupCompletedBinding
    private val backupViewModel by lazy {
        ViewModelProvider(this.requireActivity())[MultiBackupViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupCompletedBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            val optionList = backupViewModel.getBackupOptionList()
            if (optionList.size > 2) {
                ivOptionIconFirst.setImageResource(optionList[0].iconId)
                ivOptionIconSecond.setImageResource(optionList[1].iconId)
                ivOptionIconPlus.setVisible()
                ivOptionIconSecond.setVisible()
            } else {
                ivOptionIconFirst.setImageResource(optionList[0].iconId)
                ivOptionIconPlus.setVisible(false)
                ivOptionIconSecond.setVisible(false)
            }

            btnNext.setOnClickListener {
                this@BackupCompletedFragment.requireActivity().finish()
            }
        }
    }
}