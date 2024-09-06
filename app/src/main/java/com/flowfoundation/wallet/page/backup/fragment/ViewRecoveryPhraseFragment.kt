package com.flowfoundation.wallet.page.backup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.flowfoundation.wallet.databinding.FragmentViewRecoveryPhraseBinding
import com.flowfoundation.wallet.page.backup.viewmodel.BackupViewMnemonicViewModel
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicAdapter
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import com.instabug.library.Instabug

class ViewRecoveryPhraseFragment: Fragment() {
    private lateinit var binding: FragmentViewRecoveryPhraseBinding
    private lateinit var viewModel: BackupViewMnemonicViewModel
    private val adapter by lazy { MnemonicAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentViewRecoveryPhraseBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this@ViewRecoveryPhraseFragment.requireActivity())[BackupViewMnemonicViewModel::class.java].apply {
            mnemonicListLiveData.observe(viewLifecycleOwner) { list ->
                adapter.setNewDiffData(list)
            }

        }
        initPhrases()
    }

    private fun initPhrases() {
        with(binding.mnemonicContainer) {
            setVisible()
            adapter = this@ViewRecoveryPhraseFragment.adapter
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
            addItemDecoration(GridSpaceItemDecoration(vertical = 16.0))
            Instabug.addPrivateViews(this)
        }
        binding.copyButton.setOnClickListener {
            viewModel.copyMnemonic()
        }
        viewModel.loadMnemonic()
    }
}