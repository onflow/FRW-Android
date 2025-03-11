package com.flowfoundation.wallet.page.evm

import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityEnableEvmBinding
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceCreateCOAAccount
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
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

            val text = R.string.enable_evm_title_to_evm.res2String()
            val evmText = R.string.evm_on_flow.res2String()
            val index = text.indexOf(evmText)
            if (index < 0 || index + evmText.length > text.length) {
                binding.tvTitleEvm.text = text
            } else {
                val start = binding.tvTitleEvm.paint.measureText(text.substring(0, index))
                binding.tvTitleEvm.text = SpannableStringBuilder(text).apply {
                    val startColor = R.color.evm_on_flow_start_color.res2color()
                    val endColor = R.color.evm_on_flow_end_color.res2color()
                    val gradientTextWidth = binding.tvTitleEvm.paint.measureText(text)
                    val shader = LinearGradient(
                        start, 0f, gradientTextWidth, 0f,
                        intArrayOf(startColor, endColor), null,
                        Shader.TileMode.CLAMP,
                    )
                    setSpan(
                        ShaderSpan(shader),
                        index,
                        index + evmText.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            tvSkip.setOnClickListener { finish() }
            tvMore.setOnClickListener {
                "https://flow.com/upgrade/crescendo/evm".openInSystemBrowser(this@EnableEVMActivity, ignoreInAppBrowser = true)
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
                        MixpanelManager.coaCreation(txId)
                        EVMWalletManager.fetchEVMAddress { isSuccess ->
                            uiScope {
                                hideLoading()
                            }
                            if (isSuccess) {
                                finish()
                                EVMWalletManager.getEVMAddress()?.let { address ->
                                    WalletManager.selectWalletAddress(address)
                                    MainActivity.relaunch(Env.getApp())
                                }
                            }
                        }
                    } else if (it.isFailed()) {
                        MixpanelManager.coaCreation(txId, it.errorMessage)
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

    private inner class ShaderSpan(private val shader: Shader) : ForegroundColorSpan(0) {
        override fun updateDrawState(tp: TextPaint) {
            tp.shader = shader
        }
    }
}
