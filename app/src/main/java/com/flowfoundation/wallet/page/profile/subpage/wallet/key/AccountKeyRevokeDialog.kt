package com.flowfoundation.wallet.page.profile.subpage.wallet.key

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogAccountKeyRevokeBinding
import com.flowfoundation.wallet.manager.account.AccountKeyManager
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.ButtonState
import com.nftco.flow.sdk.FlowAddress


class AccountKeyRevokeDialog : BottomSheetDialogFragment() {
    private val indexId by lazy { arguments?.getInt(EXTRA_INDEX_ID) ?: -1 }
    private lateinit var binding: DialogAccountKeyRevokeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogAccountKeyRevokeBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            closeButton.setOnClickListener { dismiss() }
            skipButton.setOnClickListener { dismiss() }
            sendButton.setOnProcessing {
                revokeKey()
            }
        }
    }

    private fun revokeKey() {
        ioScope {
            if (indexId < 0) {
                return@ioScope
            }
            val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return@ioScope
            val address = WalletManager.wallet()?.walletAddress() ?: return@ioScope
            val flowAddress = FlowAddress(address)
            val keyIndex = flowAddress.currentKeyId(cryptoProvider.getPublicKey())
            if (keyIndex == indexId) {
                toast(msgRes = R.string.revoke_failed)
                return@ioScope
            }
            val isSuccess = AccountKeyManager.revokeAccountKey(indexId)
            safeRun {
                if (isSuccess) {
                    dismiss()
                } else {
                    toast(msgRes = R.string.revoke_failed)
                    uiScope {
                        binding.sendButton.changeState(ButtonState.DEFAULT)
                    }
                }
            }
        }
    }

    companion object {

        private const val EXTRA_INDEX_ID = "extra_index_id"

        fun show(activity: FragmentActivity, indexId: Int) {
            AccountKeyRevokeDialog().apply {
                arguments = Bundle().apply {
                    putInt(EXTRA_INDEX_ID, indexId)
                }
            }.show(activity.supportFragmentManager, "")
        }
    }
}