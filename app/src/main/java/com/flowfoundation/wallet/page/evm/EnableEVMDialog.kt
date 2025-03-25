package com.flowfoundation.wallet.page.evm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.databinding.DialogEnableEvmBinding
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceCreateCOAAccount
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nftco.flow.sdk.FlowTransactionStatus

class EnableEVMDialog : BottomSheetDialogFragment() {
    private lateinit var binding: DialogEnableEvmBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogEnableEvmBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            ivClose.setOnClickListener { dismissAllowingStateLoss() }
            tvLater.setOnClickListener { dismissAllowingStateLoss() }

            btnEnable.setOnClickListener {
                enableEVM()
            }
        }
    }

    private fun showLoading() {
        binding.btnEnable.setProgressVisible(true)
    }

    private fun hideLoading() {
        binding.btnEnable.setProgressVisible(false)
    }

    private fun enableEVM() {
        showLoading()
        ioScope {
            try {
                val txId = cadenceCreateCOAAccount()
                val transactionState = TransactionState(
                    transactionId = txId!!,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.UNKNOWN.num,
                    type = TransactionState.TYPE_TRANSACTION_DEFAULT,
                    data = ""
                )
                TransactionStateManager.newTransaction(transactionState)
                uiScope { pushBubbleStack(transactionState) }
                TransactionStateWatcher(txId).watch {
                    if (it.isExecuteFinished()) {
                        EVMWalletManager.fetchEVMAddress { isSuccess ->
                            uiScope {
                                hideLoading()
                            }
                            if (isSuccess) {
                                dismissAllowingStateLoss()
                                EVMWalletManager.getEVMAddress()?.let { address ->
                                    WalletManager.selectWalletAddress(address)
                                    MainActivity.relaunch(Env.getApp())
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiScope {
                    hideLoading()
                }
            }
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager) {
            EnableEVMDialog().show(fragmentManager, "")
        }
    }
}