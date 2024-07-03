package com.flowfoundation.wallet.page.walletcreate.fragments.legal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentWalletCreateLegalBinding
import com.flowfoundation.wallet.page.walletcreate.WalletCreateViewModel

class WalletCreateLegalFragment : Fragment() {

    private lateinit var binding: FragmentWalletCreateLegalBinding

    private val pageViewModel by lazy { ViewModelProvider(requireActivity())[WalletCreateViewModel::class.java] }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletCreateLegalBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {

            termsButton.setOnClickListener { openWebUrl("https://lilico.app/about/terms") }
            privacyButton.setOnClickListener { openWebUrl("https://lilico.app/about/privacy-policy") }

            nextButton.setOnClickListener { pageViewModel.nextStep() }
        }
    }

    private fun openWebUrl(url: String) {
        requireActivity().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}