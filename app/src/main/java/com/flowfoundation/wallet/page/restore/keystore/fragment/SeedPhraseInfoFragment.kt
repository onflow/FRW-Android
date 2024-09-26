package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentPrivateKeyStoreInfoBinding
import com.flowfoundation.wallet.databinding.FragmentSeedPhraseInfoBinding
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher


class SeedPhraseInfoFragment: Fragment() {
    private lateinit var binding: FragmentSeedPhraseInfoBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[KeyStoreRestoreViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSeedPhraseInfoBinding.inflate(inflater)
        return binding.root
    }

    private fun canRestore(): Boolean {
        val json = binding.etSeedPhrase.text.toString().trim()
        return json.isNotEmpty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            clAdvanced.setOnClickListener {
                if (clAdvancedLayout.isVisible()) {
                    ivAdvanced.setImageResource(R.drawable.ic_seed_phrase_advanced_open)
                    clAdvancedLayout.gone()
                } else {
                    ivAdvanced.setImageResource(R.drawable.ic_seed_phrase_advanced_close)
                    clAdvancedLayout.visible()
                }
            }
            etSeedPhrase.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    btnImport.isEnabled = canRestore()
                }
            })
            btnImport.setOnClickListener {
                restoreViewModel.importSeedPhrase(
                    etSeedPhrase.text.toString().trim(),
                    etAddress.text.toString().trim(),
                    etPassphrase.text.toString().trim(),
                    etDerivationPath.text.toString().trim()
                )
            }
        }
    }

}