package com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentWalletRestoreMnemonicBinding
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.model.WalletRestoreMnemonicModel
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.presenter.WalletRestoreMnemonicPresenter

class WalletRestoreMnemonicFragment : Fragment() {

    private lateinit var binding: FragmentWalletRestoreMnemonicBinding

    private lateinit var presenter: WalletRestoreMnemonicPresenter
    private lateinit var viewModel: WalletRestoreMnemonicViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletRestoreMnemonicBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = WalletRestoreMnemonicPresenter(this, binding)
        viewModel = ViewModelProvider(requireActivity())[WalletRestoreMnemonicViewModel::class.java].apply {
            mnemonicSuggestListLiveData.observe(viewLifecycleOwner) { presenter.bind(WalletRestoreMnemonicModel(mnemonicList = it)) }
            selectSuggestLiveData.observe(viewLifecycleOwner) { presenter.bind(WalletRestoreMnemonicModel(selectSuggest = it)) }
            invalidWordListLiveData.observe(viewLifecycleOwner) { presenter.bind(WalletRestoreMnemonicModel(invalidWordList = it)) }
        }
    }

    override fun onDestroyView() {
        presenter.unbind()
        super.onDestroyView()
    }

}