package com.flowfoundation.wallet.page.token.custom.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentCustomTokenAddressInputBinding
import com.flowfoundation.wallet.page.token.custom.CustomTokenViewModel
import com.flowfoundation.wallet.utils.evmAddressPattern
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.visible


class CustomTokenAddressInputFragment : Fragment() {
    private lateinit var binding: FragmentCustomTokenAddressInputBinding
    private lateinit var customTokenViewModel: CustomTokenViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCustomTokenAddressInputBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding.etAddress) {
            doOnTextChanged { text, _, _, _ ->
                val input = text.toString().lowercase().trim()
                if (input.isEmpty()) {
                    hideErrorState()
                } else {
                    checkAddressVerifyAndSearch(input)
                }
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val keyword = text.toString()
                    checkAddressVerifyAndSearch(keyword)
                }
                return@setOnEditorActionListener false
            }
        }
        customTokenViewModel = ViewModelProvider(requireActivity())[CustomTokenViewModel::class.java].apply {
            importSuccessLiveData.observe(viewLifecycleOwner) { isSuccess ->
                binding.etAddress.setText("")
            }
        }
    }

    private fun checkAddressVerifyAndSearch(address: String) {
        val formatAddress = if (address.startsWith("0x")) address else "0x$address"
        hideErrorState()
        binding.etAddress.hideKeyboard()
        if (evmAddressPattern.matches(formatAddress)) {
            customTokenViewModel.fetchTokenInfoWithAddress(formatAddress)
        } else {
            binding.stateErrorAddress.visible()
        }
    }

    private fun hideErrorState() {
        if (binding.stateErrorAddress.isVisible()) {
            binding.stateErrorAddress.gone()
        }
    }
}