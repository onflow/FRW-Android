package com.flowfoundation.wallet.page.walletrestore.fragments.error

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentRestoreErrorBinding
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreErrorOption
import com.flowfoundation.wallet.page.walletrestore.WALLET_RESTORE_STEP_GUIDE
import com.flowfoundation.wallet.page.walletrestore.WalletRestoreViewModel
import com.flowfoundation.wallet.utils.extensions.res2String


class WalletRestoreErrorFragment(val option: RestoreErrorOption) : Fragment() {

    private lateinit var binding: FragmentRestoreErrorBinding
    private val pageViewModel by lazy { ViewModelProvider(requireActivity())[WalletRestoreViewModel::class.java] }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestoreErrorBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            tvErrorTitle.text = option.titleId.res2String()
            tvErrorDesc.text = option.descId.res2String()
            tvTry.setOnClickListener {
                pageViewModel.changeStep(WALLET_RESTORE_STEP_GUIDE)
            }
        }
    }
}