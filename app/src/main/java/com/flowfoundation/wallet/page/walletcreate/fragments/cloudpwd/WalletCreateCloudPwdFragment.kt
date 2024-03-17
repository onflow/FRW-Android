package com.flowfoundation.wallet.page.walletcreate.fragments.cloudpwd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentWalletCreateCloudPwdBinding

class WalletCreateCloudPwdFragment : Fragment() {

    private lateinit var binding: FragmentWalletCreateCloudPwdBinding
    private lateinit var presenter: WalletCreateCloudPwdPresenter
    private lateinit var viewModel: WalletCreateCloudPwdViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletCreateCloudPwdBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = WalletCreateCloudPwdPresenter(this, binding)
        viewModel = ViewModelProvider(this)[WalletCreateCloudPwdViewModel::class.java].apply {
            backupCallbackLiveData.observe(viewLifecycleOwner) { presenter.bind(WalletCreateCloudPwdModel(isBackupSuccess = it)) }
        }
    }
}