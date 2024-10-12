package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.flowfoundation.wallet.databinding.FragmentBackupRecoveryPhraseInfoBinding
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupCompletedItem
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupRecoveryPhraseViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicAdapter
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import com.instabug.library.Instabug


class BackupRecoveryPhraseInfoFragment : Fragment() {

    private lateinit var binding: FragmentBackupRecoveryPhraseInfoBinding
    private lateinit var viewModel: BackupRecoveryPhraseViewModel
    private val adapter by lazy { MnemonicAdapter() }

    private val backupViewModel by lazy {
        ViewModelProvider(requireParentFragment().requireActivity())[MultiBackupViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupRecoveryPhraseInfoBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireParentFragment())[BackupRecoveryPhraseViewModel::class.java].apply {
            mnemonicListLiveData.observe(viewLifecycleOwner) { list ->
                adapter.setNewDiffData(list)
            }
        }
        initPhrases()
    }

    private fun initPhrases() {
        with(binding.mnemonicContainer) {
            adapter = this@BackupRecoveryPhraseInfoFragment.adapter
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
            addItemDecoration(GridSpaceItemDecoration(vertical = 16.0))
            Instabug.addPrivateViews(this)
            visible()
        }
        with(binding.copyButton) {
            setOnClickListener {
                viewModel.copyMnemonic()
            }
        }
        binding.backupProgress.setProgressInfo(BackupOption.BACKUP_WITH_RECOVERY_PHRASE, true)
        with(binding.btnNext) {
            setOnClickListener {
                backupViewModel.toNext(BackupCompletedItem(
                    type = BackupType.MANUAL,
                    mnemonic = viewModel.getMnemonic()
                ))
            }
        }
        viewModel.loadMnemonic()
    }
}