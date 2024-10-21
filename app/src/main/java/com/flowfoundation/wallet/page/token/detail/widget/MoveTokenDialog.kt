package com.flowfoundation.wallet.page.token.detail.widget

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.res.ColorStateList
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
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalanceWithAddress
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.toSafeFloat
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class MoveTokenDialog : BottomSheetDialogFragment() {
    private var symbol: String = FlowCoin.SYMBOL_FLOW
    private lateinit var binding: DialogMoveTokenBinding
    private var isFundToEVM = true
    private var fromBalance = 0.001f
    private var result: Continuation<Boolean>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogMoveTokenBinding.inflate(inflater)
        return binding.rootView
    }

    private fun getMinBalance(): Float {
        return if (symbol == FlowCoin.SYMBOL_FLOW) {
            0.001f
        } else {
            0f
        }
    }

    private fun checkAmount() {
        val amount = binding.etAmount.text.ifBlank { "0" }.toString().toSafeFloat()
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
                val amount = (fromBalance - getMinBalance()).formatNum()
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
                        selectedCoin = symbol,
                        disableCoin = null,
                        childFragmentManager,
                    )?.let {
                        symbol = it.symbol.lowercase()
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
            val amount = binding.etAmount.text.ifBlank { "0" }.toString().toSafeFloat()
            if (symbol == FlowCoin.SYMBOL_FLOW) {
                EVMWalletManager.moveFlowToken(amount, isFundToEVM) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            BalanceManager.getBalanceByCoin(FlowCoin.SYMBOL_FLOW)
                            result?.resume(true)
                            dismiss()
                        } else {
                            toast(R.string.move_flow_to_evm_failed)
                        }
                    }
                }
            } else {
                val coin = FlowCoinListManager.getCoin(symbol) ?: return@ioScope
                EVMWalletManager.moveToken(coin, amount, isFundToEVM) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            if (WalletManager.isEVMAccountSelected()) {
                                BalanceManager.refresh()
                            } else {
                                BalanceManager.getBalanceByCoin(symbol)
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
            FlowCoinListManager.getCoin(symbol)?.let {
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
                    FlowCoinListManager.getCoin(symbol),
                    WalletManager.wallet()?.walletAddress()
                )
            } else {
                if (symbol == FlowCoin.SYMBOL_FLOW) {
                    cadenceQueryCOATokenBalance()
                } else {
                    BalanceManager.getEVMBalanceByCoin(symbol)
                }
            } ?: 0f

            uiScope {
                binding.tvBalance.text = Env.getApp().getString(R.string.balance_value, fromBalance)
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        result?.resume(false)
    }

    suspend fun showDialog(activity: FragmentActivity, symbol: String) = suspendCoroutine {
        this.result = it
        this.symbol = symbol.lowercase()
        show(activity.supportFragmentManager, "")
    }

    companion object {
        private const val EXTRA_SYMBOL = "extra_symbol"
        fun show(activity: FragmentActivity, symbol: String) {
            MoveTokenDialog().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_SYMBOL, symbol)
                }
            }.show(activity.supportFragmentManager, "")
        }
    }
}