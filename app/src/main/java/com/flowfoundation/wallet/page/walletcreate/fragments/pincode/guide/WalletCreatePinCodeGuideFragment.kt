package com.flowfoundation.wallet.page.walletcreate.fragments.pincode.guide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.flowfoundation.wallet.databinding.FragmentWalletCreatePinGuideBinding

class WalletCreatePinCodeGuideFragment : Fragment() {

    private lateinit var binding: FragmentWalletCreatePinGuideBinding
    private lateinit var presenter: WalletCreatePinCodeGuidePresenter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletCreatePinGuideBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = WalletCreatePinCodeGuidePresenter(this, binding)
    }
}