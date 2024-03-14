package com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.flowfoundation.wallet.databinding.FragmentWalletCreatePinCodeBinding

class WalletCreatePinCodeFragment : Fragment() {

    private lateinit var binding: FragmentWalletCreatePinCodeBinding
    private lateinit var presenter: WalletCreatePinCodePresenter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletCreatePinCodeBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = WalletCreatePinCodePresenter(this, binding)
    }
}