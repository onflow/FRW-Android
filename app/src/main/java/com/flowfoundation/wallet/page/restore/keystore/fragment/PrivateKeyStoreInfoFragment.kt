package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentPrivateKeyStoreInfoBinding
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher
import org.json.JSONObject


class PrivateKeyStoreInfoFragment: Fragment() {
    private lateinit var binding: FragmentPrivateKeyStoreInfoBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[KeyStoreRestoreViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPrivateKeyStoreInfoBinding.inflate(inflater)
        return binding.root
    }

    private fun canRestore(): Boolean {
        val json = binding.etJson.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        return isValidJson(json) && password.isNotEmpty()
    }

    private fun isValidJson(input: String): Boolean {
        return try {
            JSONObject(input)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            etJson.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    btnImport.isEnabled = canRestore()
                }
            })
            etPassword.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    btnImport.isEnabled = canRestore()
                }
            })
            btnImport.setOnClickListener {
                restoreViewModel.importKeyStore(
                    etJson.text.toString().trim(),
                    etPassword.text.toString().trim(),
                    etAddress.text.toString().trim()
                )
            }
            btnImport.isEnabled = false
        }
    }

}