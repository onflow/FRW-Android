package com.flowfoundation.wallet.page.evm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityEnableEvmBinding
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceCreateCOAAccount
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.nftco.flow.sdk.FlowTransactionStatus
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class EnableEVMActivity : BaseActivity() {

    private lateinit var binding: ActivityEnableEvmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnableEvmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(false).applyStatusBar()

        with(binding) {
            tvSkip.setOnClickListener { finish() }
            tvMore.setOnClickListener {
                //todo learn more
            }

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
                                finish()
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
        fun launch(context: Context) {
            context.startActivity(Intent(context, EnableEVMActivity::class.java))
        }
    }
}