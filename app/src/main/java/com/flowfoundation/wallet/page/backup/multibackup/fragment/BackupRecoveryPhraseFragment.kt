package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.instabug.library.Instabug
import com.flowfoundation.wallet.databinding.FragmentBackupRecoveryPhraseBinding
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupRecoveryPhraseViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicAdapter
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration


class BackupRecoveryPhraseFragment : Fragment() {

    private lateinit var binding: FragmentBackupRecoveryPhraseBinding
    private lateinit var viewModel: BackupRecoveryPhraseViewModel
    private val adapter by lazy { MnemonicAdapter() }

    private val backupViewModel by lazy {
        ViewModelProvider(this.requireActivity())[MultiBackupViewModel::class.java]
    }

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
        viewModel = ViewModelProvider(this)[BackupRecoveryPhraseViewModel::class.java].apply {
            createBackupCallbackLiveData.observe(viewLifecycleOwner) { isSuccess ->
                if (isSuccess) {
                    backupViewModel.toNext()
                }
            }
            mnemonicListLiveData.observe(viewLifecycleOwner) { list ->
                adapter.setNewDiffData(list)
            }

        }
        initPhrases()
    }

    private fun initPhrases() {
        with(binding.mnemonicContainer) {
            setVisible()
            adapter = this@BackupRecoveryPhraseFragment.adapter
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
            addItemDecoration(GridSpaceItemDecoration(vertical = 16.0))
            Instabug.addPrivateViews(this)
        }
        binding.stringContainer.setVisible(false)
        binding.copyButton.setOnClickListener {
            viewModel.copyMnemonic()
        }
        with(binding.btnNext) {
            setOnClickListener {
                setProgressVisible(true)
                viewModel.uploadToChainAndSync()
            }
        }
        viewModel.loadMnemonic()
    }
}