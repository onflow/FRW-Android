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
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalanceWithAddress
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2String
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
    private var isFundToEVM = true
    private var fromBalance = BigDecimal(0.001)
    private var result: Continuation<Boolean>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogMoveTokenBinding.inflate(inflater)
        return binding.rootView
    }

    private fun getMinBalance(): BigDecimal {
        return if (FlowCoinListManager.isFlowCoin(contractId)) {
            BigDecimal(0.001)
        } else {
            BigDecimal.ZERO
        }
    }

    private fun checkAmount() {
        val amount = binding.etAmount.text.ifBlank { "0" }.toString().toSafeDecimal()
        val isOutOfBalance = amount > (fromBalance - getMinBalance())
        if (isOutOfBalance && !binding.llErrorLayout.isVisible()) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Fade().apply { })
        } else if (!isOutOfBalance && binding.llErrorLayout.isVisible()) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Fade().apply { })
        }
        binding.llErrorLayout.setVisible(isOutOfBalance)
        binding.btnMove.isEnabled = verifyAmount() && !isOutOfBalance
    }

    private fun verifyAmount(): Boolean {
        val number = binding.etAmount.text.toString().toFloatOrNull()
        return number != null && number > 0
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isFundToEVM = WalletManager.isEVMAccountSelected().not()
        with(binding.etAmount) {
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
            ivArrow.setOnClickListener {
                isFundToEVM = isFundToEVM.not()
                initView()
            }
            tvMax.setOnClickListener {
                val amount = BigDecimal.ZERO.max(fromBalance - getMinBalance()).format(8)
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
            coinWrapper.setOnClickListener {
                uiScope {
                    SelectMoveTokenDialog().show(
                        selectedCoin = contractId,
                        disableCoin = null,
                        childFragmentManager,
                    )?.let {
                        contractId = it.contractId()
                        initView()
                    }
                }
            }
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
            val parent = WalletManager.wallet()?.walletAddress().orEmpty()
            val coin = FlowCoinListManager.getCoinById(contractId) ?: return@ioScope
            MixpanelManager.transferFT(
                if (isFundToEVM) parent else EVMWalletManager.getEVMAddress().orEmpty(),
                if (isFundToEVM) EVMWalletManager.getEVMAddress().orEmpty() else parent,
                coin.symbol,
                amount.toString(),
                coin.getFTIdentifier()
            )
            if (FlowCoinListManager.isFlowCoin(contractId)) {
                EVMWalletManager.moveFlowToken(amount, isFundToEVM) { isSuccess ->
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
            } else {
                EVMWalletManager.moveToken(coin, amount, isFundToEVM) { isSuccess ->
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

    @SuppressLint("SetTextI18n")
    private fun initView() {
        with(binding) {
            val walletAddress = WalletManager.wallet()?.walletAddress().orEmpty()
            val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
            if (isFundToEVM) {
                layoutFromAccount.setAccountInfo(walletAddress)
                layoutToAccount.setAccountInfo(evmAddress)
            } else {
                layoutFromAccount.setAccountInfo(evmAddress)
                layoutToAccount.setAccountInfo(walletAddress)
            }
            FlowCoinListManager.getCoinById(contractId)?.let {
                Glide.with(ivTokenIcon).load(it.icon()).into(ivTokenIcon)
            }
            tvBalance.text = ""
            etAmount.setText("")
            btnMove.isEnabled = false
            tvMoveFee.text = "0.001FLOW"
            tvMoveFeeTips.text = R.string.move_fee_tips.res2String()
        }
        fetchTokenBalance()
    }

    @SuppressLint("SetTextI18n")
    private fun fetchTokenBalance() {
        ioScope {
            fromBalance = if (isFundToEVM) {
                cadenceQueryTokenBalanceWithAddress(
                    FlowCoinListManager.getCoinById(contractId),
                    WalletManager.wallet()?.walletAddress()
                )
            } else {
                val coin = FlowCoinListManager.getCoinById(contractId)
                if (coin == null) {
                    BigDecimal.ZERO
                } else {
                    if (coin.isFlowCoin()) {
                        cadenceQueryCOATokenBalance()
                    } else {
                        BalanceManager.getEVMBalanceByCoin(coin.address)
                    }
                }
            } ?: BigDecimal.ZERO

            uiScope {
                binding.tvBalance.text = Env.getApp().getString(R.string.balance_value, fromBalance.format(8))
            }
        }
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

    companion object {
        private const val EXTRA_CONTRACT_ID = "extra_contract_id"
        fun show(activity: FragmentActivity, contractId: String) {
            MoveTokenDialog().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_CONTRACT_ID, contractId)
                }
            }.show(activity.supportFragmentManager, "")
        }
    }
}