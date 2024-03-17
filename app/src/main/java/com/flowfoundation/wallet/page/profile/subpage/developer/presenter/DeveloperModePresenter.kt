package com.flowfoundation.wallet.page.profile.subpage.developer.presenter

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityDeveloperModeSettingBinding
import com.flowfoundation.wallet.manager.app.*
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.profile.subpage.developer.DeveloperModeViewModel
import com.flowfoundation.wallet.page.profile.subpage.developer.model.DeveloperPageModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.ProgressDialog
import kotlinx.coroutines.delay

class DeveloperModePresenter(
    private val activity: FragmentActivity,
    private val binding: ActivityDeveloperModeSettingBinding,
) : BasePresenter<DeveloperPageModel> {

    private val progressDialog by lazy { ProgressDialog(activity) }

    private val viewModel by lazy { ViewModelProvider(activity)[DeveloperModeViewModel::class.java] }

    init {
        uiScope {
            with(binding) {
                val isDeveloperModeEnable = isDeveloperMode()
                val isSandboxEnabled = isSandboxEnabled()
                developerModePreference.setChecked(isDeveloperModeEnable)

                group2.setVisible(isDeveloperModeEnable)
                mainnetPreference.setChecked(isMainnet())
                testnetPreference.setChecked(isTestnet())

                mainnetPreference.setCheckboxColor(R.color.colorSecondary.res2color())
                testnetPreference.setCheckboxColor(R.color.colorSecondary.res2color())

                mainnetPreference.setOnCheckedChangeListener {
                    testnetPreference.setChecked(!it)
                    changeNetwork(if (it) NETWORK_MAINNET else NETWORK_TESTNET)
                }

                sandboxEnableButton.setOnClickListener { enableSandbox() }
                sandboxEnableButton.setVisible(!isSandboxEnabled)
                sandboxTitle.setTextColor(if (isSandboxEnabled) R.color.text.res2color() else R.color.neutrals9.res2color())
                sandboxCheckbox.setVisible(isSandboxEnabled)
                sandboxWrapper.setOnClickListener { changeNetwork(NETWORK_SANDBOX) }
                sandboxWrapper.isEnabled = isSandboxEnabled

                testnetPreference.setOnCheckedChangeListener {
                    mainnetPreference.setChecked(!it)
                    changeNetwork(if (it) NETWORK_TESTNET else NETWORK_MAINNET)
                }

                developerModePreference.setOnCheckedChangeListener {
                    group2.setVisible(it)
                    setDeveloperModeEnable(it)
                    if (!it) {
                        changeNetwork(NETWORK_MAINNET)
                    }
                }
            }
        }
    }

    override fun bind(model: DeveloperPageModel) {
        model.progressDialogVisible?.let { if (it) progressDialog.show() else progressDialog.dismiss() }
        model.result?.let {
            if (!it) {
                toast(msgRes = R.string.switch_network_failed)
                with(binding) {
                    mainnetPreference.setChecked(isTestnet())
                    testnetPreference.setChecked(isMainnet())
                }
            }
        }
    }

    private fun changeNetwork(network: Int) {
        updateChainNetworkPreference(network) {
            ioScope {
                delay(200)
                refreshChainNetworkSync()
                doNetworkChangeTask()
                uiScope {
                    delay(200)
                    viewModel.changeNetwork()
                    binding.mainnetPreference.setChecked(isMainnet())
                    binding.testnetPreference.setChecked(isTestnet())
                    binding.sandboxCheckbox.isChecked = isSandboxNet()
                }
            }
        }
    }

    private fun enableSandbox() {
        binding.sandboxEnableButton.setVisible(false)
        binding.sandboxProgressbar.setVisible(true)
        ioScope {
            val response = retrofit(network = NETWORK_NAME_SANDBOX).create(ApiService::class.java).enableSandboxNet()
            val transactionId = response.transactionId
            if (!transactionId.isNullOrBlank()) {
                val transactionState = TransactionState(
                    transactionId = transactionId,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.UNKNOWN.num,
                    type = TransactionState.TYPE_TRANSACTION_DEFAULT,
                    data = "",
                )

                TransactionStateManager.newTransaction(transactionState)
                uiScope { pushBubbleStack(transactionState) }
                TransactionStateWatcher(transactionId).watch {
                    if (it.isExecuteFinished()) {
                        sandboxEnabled()
                    }
                }
            } else if (response.status == 400) {
                sandboxEnabled()
            }
        }
    }

    private fun sandboxEnabled() {
        ioScope { setSandboxEnabled() }
        uiScope {
            binding.sandboxEnableButton.setVisible(false)
            binding.sandboxProgressbar.setVisible(false)
            binding.sandboxTitle.setTextColor(R.color.text.res2color())
            binding.sandboxCheckbox.setVisible(true)
            binding.sandboxWrapper.isEnabled = true
        }
    }
}