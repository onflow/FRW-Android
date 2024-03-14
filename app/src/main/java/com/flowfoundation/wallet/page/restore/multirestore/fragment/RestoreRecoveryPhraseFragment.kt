package com.flowfoundation.wallet.page.restore.multirestore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentRestoreRecoveryPhraseBinding
import com.flowfoundation.wallet.page.restore.multirestore.presenter.RestoreRecoveryPhrasePresenter
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.WalletRestoreMnemonicViewModel
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.model.WalletRestoreMnemonicModel


class RestoreRecoveryPhraseFragment : Fragment() {
    private lateinit var binding: FragmentRestoreRecoveryPhraseBinding
    private lateinit var presenter: RestoreRecoveryPhrasePresenter
    private lateinit var viewModel: WalletRestoreMnemonicViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestoreRecoveryPhraseBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter = RestoreRecoveryPhrasePresenter(this, binding)
        viewModel =
            ViewModelProvider(requireActivity())[WalletRestoreMnemonicViewModel::class.java].apply {
                mnemonicSuggestListLiveData.observe(viewLifecycleOwner) {
                    presenter.bind(
                        WalletRestoreMnemonicModel(mnemonicList = it)
                    )
                }
                selectSuggestLiveData.observe(viewLifecycleOwner) {
                    presenter.bind(
                        WalletRestoreMnemonicModel(selectSuggest = it)
                    )
                }
                invalidWordListLiveData.observe(viewLifecycleOwner) {
                    presenter.bind(
                        WalletRestoreMnemonicModel(invalidWordList = it)
                    )
                }
            }
    }

    override fun onDestroyView() {
        presenter.unbind()
        super.onDestroyView()
    }
}