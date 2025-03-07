package com.flowfoundation.wallet.page.token.detail.widget

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogMoveTokenBinding
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.nft.move.SelectAccountDialog
import com.flowfoundation.wallet.page.token.detail.model.MoveToken
import com.flowfoundation.wallet.page.token.detail.provider.EVMAccountTokenProvider
import com.flowfoundation.wallet.page.token.detail.provider.FlowAccountTokenProvider
import com.flowfoundation.wallet.page.token.detail.provider.MoveTokenProvider
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setDecimalDigitsFilter
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.toSafeDecimal
import com.flowfoundation.wallet.utils.format
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.math.BigDecimal
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class MoveTokenDialog : BottomSheetDialogFragment() {
    private var contractId: String = FlowCoinListManager.getFlowCoinContractId()
    private lateinit var binding: DialogMoveTokenBinding
    private var fromBalance = BigDecimal.ZERO
    private var result: Continuation<Boolean>? = null
    private var isFlowCoin = false

    private var moveFromAddress: String = WalletManager.selectedWalletAddress()
    private var moveToAddress: String = EVMWalletManager.getEVMAddress().orEmpty()
    private var currentTokenProvider: MoveTokenProvider? = null
    private var currentToken: MoveToken? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogMoveTokenBinding.inflate(inflater)
        return binding.rootView
    }

    private fun getProvider(moveFromAddress: String): MoveTokenProvider {
        val isEVMAddress = EVMWalletManager.isEVMWalletAddress(moveFromAddress)
        return when {
            isEVMAddress && currentTokenProvider is EVMAccountTokenProvider -> currentTokenProvider!!
            isEVMAddress.not() && currentTokenProvider is FlowAccountTokenProvider -> currentTokenProvider!!
            else -> if (isEVMAddress) {
                EVMAccountTokenProvider()
            } else {
                FlowAccountTokenProvider()
            }.also { currentTokenProvider = it  }
        }
    }

    private fun checkAmount() {
        val amount = binding.etAmount.text.ifBlank { "0" }.toString().toSafeDecimal()
        val isOutOfBalance = amount > fromBalance
        if (isOutOfBalance && !binding.llErrorLayout.isVisible()) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Fade().apply { })
        } else if (!isOutOfBalance && binding.llErrorLayout.isVisible()) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Fade().apply { })
        }
        binding.llErrorLayout.setVisible(isOutOfBalance)
        binding.btnMove.isEnabled = verifyAmount() && !isOutOfBalance
        if (isFlowCoin) {
            uiScope {
                binding.storageTip.setInsufficientTip(AccountInfoManager.validateFlowTokenTransaction(amount, true))
            }
        }
    }

    private fun getEligibleAccounts(): List<String> {
        val walletAddress = WalletManager.wallet()?.walletAddress().orEmpty()
        val childAccounts = WalletManager.childAccountList(walletAddress)
            ?.get()
            ?.map { it.address } ?: emptyList()
        val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
        return listOf(walletAddress) + childAccounts + listOf(evmAddress)
    }

    private fun verifyAmount(): Boolean {
        val number = binding.etAmount.text.toString().toFloatOrNull()
        return number != null && number > 0
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        moveFromAddress = WalletManager.selectedWalletAddress()
        moveToAddress = if (WalletManager.isEVMAccountSelected()) {
            WalletManager.wallet()?.walletAddress().orEmpty()
        } else {
            EVMWalletManager.getEVMAddress().orEmpty()
        }
        with(binding.etAmount) {
            setDecimalDigitsFilter(contractId.decimal())
            doOnTextChanged { _, _, _, _ ->
                checkAmount()
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    hideKeyboard()
                    if (verifyAmount() && !binding.llErrorLayout.isVisible()) moveToken()
                }
                return@setOnEditorActionListener false
            }
        }
        with(binding) {
            tvMoveFee.text = "0.001 FLOW"
            tvMoveFeeTips.text = R.string.move_fee_tips.res2String()
            ivArrow.setOnClickListener {
                val temp = moveFromAddress
                moveFromAddress = moveToAddress
                moveToAddress = temp
                initView()
            }
            tvMax.setOnClickListener {
                val amount = fromBalance.format(8)
                etAmount.setText(amount)
                etAmount.setSelection(etAmount.text.length)
            }
            btnMove.setOnClickListener {
                moveToken()
            }
            ivClose.setOnClickListener {
                result?.resume(false)
                dismiss()
            }
            layoutFromAccount.setOnClickListener {
                uiScope {
                    val eligibleFrom = getEligibleAccounts().filter { it != binding.layoutToAccount.getAccountAddress() }
                    val newFromAddress = SelectAccountDialog().show(
                        selectedAddress = binding.layoutFromAccount.getAccountAddress(),
                        addressList = eligibleFrom,
                        fragmentManager = childFragmentManager
                    )
                    newFromAddress?.let { selected ->
                        binding.layoutFromAccount.setAccountInfo(selected)
                        moveFromAddress = selected
                        tvBalance.text = ""
                        etAmount.setText("")
                        btnMove.isEnabled = false
                        updateTokenInfo()
                        updateMoveFeeVisibility()
                    }
                }
            }
            layoutToAccount.setOnClickListener {
                uiScope {
                    // Build list of eligible To accounts (exclude current From address)
                    val eligibleTo = getEligibleAccounts().filter { it != binding.layoutFromAccount.getAccountAddress() }
                    val newToAddress = SelectAccountDialog().show(
                        selectedAddress = binding.layoutToAccount.getAccountAddress(),
                        addressList = eligibleTo,
                        fragmentManager = childFragmentManager
                    )
                    newToAddress?.let { selected ->
                        binding.layoutToAccount.setAccountInfo(selected)
                        moveToAddress = selected
                        updateMoveFeeVisibility()
                    }
                }

            }
            // Conditionally render arrows in dropdown
            val eligibleFrom = getEligibleAccounts().filter { it != layoutToAccount.getAccountAddress() }
            val eligibleTo = getEligibleAccounts().filter { it != layoutFromAccount.getAccountAddress() }

            layoutFromAccount.setSelectMoreAccount(eligibleFrom.size > 1)
            layoutFromAccount.invalidate()
            layoutToAccount.setSelectMoreAccount(eligibleTo.size > 1)
            layoutToAccount.invalidate()
        }
        initView()
    }

    private fun moveToken() {
        if (binding.btnMove.isProgressVisible()) {
            return
        }
        binding.btnMove.setProgressVisible(true)
        ioScope {
            val amount = binding.etAmount.text.ifBlank { "0" }.toString().toSafeDecimal()
            val coin = currentToken?.tokenInfo ?: return@ioScope

            val from = moveFromAddress
            val to = moveToAddress

            MixpanelManager.transferFT(
                from,
                to,
                coin.symbol,
                amount.toString(),
                coin.getFTIdentifier()
            )

            if (coin.isFlowCoin()) {
                EVMWalletManager.moveFlowToken(coin, amount, from, to) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            BalanceManager.getBalanceByCoin(FlowCoinListManager.getFlowCoinContractId())
                            result?.resume(true)
                            dismiss()
                        } else {
                            toast(R.string.move_flow_to_evm_failed)
                        }
                    }
                }
            } else if (coin.isCOABridgeCoin() || coin.canBridgeToCOA()) {
                EVMWalletManager.moveBridgeToken(coin, amount, from, to) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            if (WalletManager.isEVMAccountSelected()) {
                                BalanceManager.refresh()
                            } else {
                                BalanceManager.getBalanceByCoin(contractId)
                            }
                            result?.resume(true)
                            dismiss()
                        } else {
                            toast(R.string.move_flow_to_evm_failed)
                        }
                    }
                }
            } else {
                EVMWalletManager.transferToken(coin, to, amount) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            if (WalletManager.isEVMAccountSelected()) {
                                BalanceManager.refresh()
                            } else {
                                BalanceManager.getBalanceByCoin(contractId)
                            }
                            result?.resume(true)
                            dismiss()
                        } else {
                            toast(R.string.move_flow_to_evm_failed)
                        }
                    }
                }
            }
        }
    }

    private fun updateMoveFeeVisibility() {
        val isFlowToFlow = !EVMWalletManager.isEVMWalletAddress(moveFromAddress) &&
                !EVMWalletManager.isEVMWalletAddress(moveToAddress)
        binding.clMoveFee.visibility = if (isFlowToFlow) View.GONE else View.VISIBLE
        if (isFlowCoin.not()) {
            uiScope {
                binding.storageTip.setInsufficientTip(AccountInfoManager.validateOtherTransaction(isMove = isFlowToFlow.not()))
            }
        }
    }

    private fun initView() {
        with(binding) {
            layoutFromAccount.setAccountInfo(moveFromAddress)
            layoutToAccount.setAccountInfo(moveToAddress)
            tvBalance.text = ""
            etAmount.setText("")
            btnMove.isEnabled = false
        }
        updateTokenInfo()
        updateMoveFeeVisibility()
    }

    private fun updateTokenInfo() {
        ioScope {
            currentToken = if (currentToken != null) {
                getProvider(moveFromAddress).getMoveToken(currentToken!!.getTokenId(moveFromAddress), moveFromAddress)
            } else {
                getProvider(moveFromAddress).getMoveToken(contractId)
            }
            isFlowCoin = currentToken?.tokenInfo?.isFlowCoin()?: false
            fromBalance = currentToken?.tokenBalance?: BigDecimal.ZERO
            uiScope {
                currentToken?.tokenInfo?.let {
                    Glide.with(binding.ivTokenIcon).load(it.icon()).into(binding.ivTokenIcon)
                }
                binding.tvBalance.text = Env.getApp().getString(R.string.balance_value, fromBalance.format(8))
            }
        }
    }

    private fun String.decimal(): Int {
        return FlowCoinListManager.getCoinById(this)?.run {
            kotlin.math.min(decimal, 8)
        } ?: 8
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        result?.resume(false)
    }

    suspend fun showDialog(activity: FragmentActivity, contractId: String) = suspendCoroutine {
        this.result = it
        this.contractId = contractId
        show(activity.supportFragmentManager, "")
    }
}