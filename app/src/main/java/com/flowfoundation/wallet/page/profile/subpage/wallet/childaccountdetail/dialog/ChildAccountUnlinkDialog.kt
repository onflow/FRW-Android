package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogUnlinkChildAccountBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.ButtonState

class ChildAccountUnlinkDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogUnlinkChildAccountBinding
    private val data by lazy { arguments?.getParcelable<ChildAccount>(EXTRA_DATA) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogUnlinkChildAccountBinding.inflate(inflater)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val account = data ?: return
        binding.closeButton.setOnClickListener { dismiss() }
        with(binding) {
            dappIcon.loadAvatar(account.icon)
            dappName.text = account.name
            dappAddress.text = account.address

            ioScope {
                val userInfo = AccountManager.userInfo() ?: return@ioScope
                val address = WalletManager.selectedWalletAddress()

                uiScope {
                    walletIcon.loadAvatar(userInfo.avatar)
                    walletName.setText(R.string.wallet)
                    walletAddress.text = address
                }
            }
        }

        binding.startButton.setOnProcessing {
            sendUnlinkTransaction(account)
        }
    }

    private fun sendUnlinkTransaction(account: ChildAccount) {
        ioScope {
            val transactionId = CadenceScript.CADENCE_UNLINK_CHILD_ACCOUNT.transactionByMainWallet {
                arg { address(account.address.toAddress()) }
            }

            if (transactionId.isNullOrBlank()) {
                uiScope { binding.startButton.changeState(ButtonState.DEFAULT) }
                toast(R.string.unlink_fail)
                return@ioScope
            }
            val transactionState = TransactionState(
                transactionId = transactionId,
                time = System.currentTimeMillis(),
                state = FlowTransactionStatus.PENDING.num,
                type = TransactionState.TYPE_TRANSACTION_DEFAULT,
                data = Gson().toJson(account),
            )
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
            uiScope { activity?.finish() }
        }
    }

    companion object {
        private const val EXTRA_DATA = "extra_data"
        fun show(fragmentManager: FragmentManager, account: ChildAccount) {
            ChildAccountUnlinkDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA, account)
                }
            }.show(fragmentManager, "")
        }
    }
}