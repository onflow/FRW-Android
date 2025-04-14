package com.flowfoundation.wallet.page.profile.subpage.wallet.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogResetWalletConfirmBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope

class WalletResetConfirmDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogResetWalletConfirmBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogResetWalletConfirmBinding.inflate(inflater)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.closeButton.setOnClickListener { dismiss() }
        with(binding) {
            actionButton.setOnClickListener {
                binding.actionButton.setProgressVisible(true)
                AccountManager.removeCurrentAccount()
            }
            ioScope {
                val address = WalletManager.selectedWalletAddress()
                uiScope { addressTextView.text = "($address)" }
            }
        }
    }

    companion object {

        fun show(fragmentManager: FragmentManager) {
            WalletResetConfirmDialog().apply {
            }.show(fragmentManager, "")
        }
    }
}