package com.flowfoundation.wallet.page.backup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.flowfoundation.wallet.databinding.FragmentBackupRecoveryPhraseInfoBinding
import com.flowfoundation.wallet.databinding.FragmentBackupSeedPhraseInfoBinding
import com.flowfoundation.wallet.page.backup.viewmodel.BackupSeedPhraseViewModel
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicAdapter
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.saveBackupMnemonicToPreference
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import com.instabug.library.Instabug


class BackupSeedPhraseInfoFragment: Fragment() {
    private lateinit var binding: FragmentBackupSeedPhraseInfoBinding
    private lateinit var viewModel: BackupSeedPhraseViewModel
    private val adapter by lazy { MnemonicAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupSeedPhraseInfoBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[BackupSeedPhraseViewModel::class.java].apply {
            mnemonicListLiveData.observe(viewLifecycleOwner) { list ->
                adapter.setNewDiffData(list)
            }

        }
        initPhrases()
    }

    private fun initPhrases() {
        with(binding.mnemonicContainer) {
            adapter = this@BackupSeedPhraseInfoFragment.adapter
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
        with(binding.btnNext) {
            setOnClickListener {
                saveBackupMnemonicToPreference(viewModel.getMnemonic())
                this@BackupSeedPhraseInfoFragment.requireActivity().finish()
            }
        }
        viewModel.loadMnemonic()
    }
}