package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentPrivateKeyInfoBinding
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher


class PrivateKeyInfoFragment: Fragment() {
    private lateinit var binding: FragmentPrivateKeyInfoBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[KeyStoreRestoreViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPrivateKeyInfoBinding.inflate(inflater)
        return binding.root
    }

    private fun canRestore(): Boolean {
        val private = binding.etPrivateKey.text.toString().trim()
        return validatePrivateKey(private)
    }

    private fun validatePrivateKey(input: String): Boolean {
        val privateKeyRegex = Regex("^(0x)?[0-9a-fA-F]{64}$")
        return privateKeyRegex.matches(input)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            etPrivateKey.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    btnImport.isEnabled = canRestore()
                }
            })
            btnImport.setOnClickListener {
                restoreViewModel.importPrivateKey(
                    etPrivateKey.text.toString().trim(),
                    etAddress.text.toString().trim()
                )
            }
            btnImport.isEnabled = false
        }
    }

}