package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentPrivateKeyStoreInfoBinding
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel


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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            etJson.setText("{\"activeAccounts\":[{\"address\":\"0x9E926623609D9a22988BFf7239400F0A5Abcf52a\",\"coin\":60,\"derivationPath\":\"m/44'/60'/0'/0/0\",\"publicKey\":\"04f977a2bca5f489872e5ca3826a997b278f2bbe6a7b5c0a82cef310849902d89ea5ea9d2110cad375244045ea9164270c74b65d5363ba5534ea61eb96fdfbf9c5\"}],\"crypto\":{\"cipher\":\"aes-128-ctr\",\"cipherparams\":{\"iv\":\"7f474204366c79d4e1b9eff7f39da7ad\"},\"ciphertext\":\"488eed84623a0e4b53df5b241b56a6c15a308b3c530d43874b0516dd502801455c4af7331e48376003e3a0012606201fd5f9e153e26bfc242f6f84356fb95c10323da3863ba27220f39fedd5b9\",\"kdf\":\"scrypt\",\"kdfparams\":{\"dklen\":32,\"n\":16384,\"p\":4,\"r\":8,\"salt\":\"\"},\"mac\":\"3b1165ca6c34ecd38e86a69c6ab73cd7033a0b47a89aeb0377f317148eb83f3f\"},\"id\":\"a1586ce1-547f-4c40-85a5-970f68d7f1d3\",\"name\":\"flow test\",\"type\":\"mnemonic\",\"version\":3}")
            etPassword.setText("password")
            btnImport.setOnClickListener {
                restoreViewModel.importKeyStore(
                    etJson.text.toString().trim(),
                    etPassword.text.toString(),
                    ""
                )
            }
        }
    }

}