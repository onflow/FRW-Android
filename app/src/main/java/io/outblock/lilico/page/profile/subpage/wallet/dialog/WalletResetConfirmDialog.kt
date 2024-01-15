package io.outblock.lilico.page.profile.subpage.wallet.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.outblock.lilico.databinding.DialogResetWalletConfirmBinding
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.page.restore.WalletRestoreActivity
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.uiScope

class WalletResetConfirmDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogResetWalletConfirmBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogResetWalletConfirmBinding.inflate(inflater)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.closeButton.setOnClickListener { dismiss() }
        with(binding) {
            actionButton.setOnClickListener {
                WalletRestoreActivity.launch(it.context)
                dismiss()
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