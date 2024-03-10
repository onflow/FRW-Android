package com.flowfoundation.wallet.page.walletcreate.fragments.warning

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentWalletCreateWarningBinding

class WalletCreateWarningFragment : Fragment() {

    private lateinit var binding: FragmentWalletCreateWarningBinding

    private lateinit var viewModel: WalletCreateWarningViewModel

    private lateinit var presenter: WalletCreateWarningPresenter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletCreateWarningBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = WalletCreateWarningPresenter(this, binding)

        viewModel = ViewModelProvider(this)[WalletCreateWarningViewModel::class.java].apply {
            registerCallbackLiveData.observe(viewLifecycleOwner) { presenter.bind(WalletCreateWarningModel(isRegisterSuccess = it)) }
        }
    }
}