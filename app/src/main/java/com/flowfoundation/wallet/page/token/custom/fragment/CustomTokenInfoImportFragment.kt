package com.flowfoundation.wallet.page.token.custom.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentCustomTokenInfoImportBinding
import com.flowfoundation.wallet.page.token.custom.CustomTokenViewModel
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.orZero
import com.flowfoundation.wallet.utils.extensions.visible


class CustomTokenInfoImportFragment : Fragment() {
    private lateinit var binding: FragmentCustomTokenInfoImportBinding
    private val customTokenViewModel by lazy {
        ViewModelProvider(requireActivity())[CustomTokenViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCustomTokenInfoImportBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentToken = customTokenViewModel.getCurrentToken()
        with(binding) {
            btnImport.isEnabled = currentToken != null && currentToken.isEnable()
            tvAddress.text = currentToken?.contractAddress.orEmpty()
            tvName.text = currentToken?.name.orEmpty()
            tvSymbol.text = currentToken?.symbol.orEmpty()
            tvDecimal.text = currentToken?.decimal?.orZero().toString()
            if (currentToken?.flowIdentifier.isNullOrBlank()) {
                tvIdentifier.gone()
                tvIdentifierTitle.gone()
            } else {
                tvIdentifier.visible()
                tvIdentifierTitle.visible()
                tvIdentifier.text = currentToken?.flowIdentifier.orEmpty()
            }
            btnImport.setOnClickListener {
                customTokenViewModel.importToken()
            }
        }
    }
}