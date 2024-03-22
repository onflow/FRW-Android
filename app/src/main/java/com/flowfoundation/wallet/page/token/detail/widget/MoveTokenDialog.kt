package com.flowfoundation.wallet.page.token.detail.widget

import android.annotation.SuppressLint
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
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogMoveTokenBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalanceWithAddress
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.toSafeFloat
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class MoveTokenDialog : BottomSheetDialogFragment() {
    private lateinit var binding: DialogMoveTokenBinding
    private var isFundToEVM = true
    private var fromBalance = 0.001f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogMoveTokenBinding.inflate(inflater)
        return binding.rootView
    }

    private fun checkAmount() {
        val amount = binding.etAmount.text.ifBlank { "0" }.toString().toSafeFloat()
        val isOutOfBalance = amount > (fromBalance - 0.001f)
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
            ivSwitch.setOnClickListener {
                isFundToEVM = isFundToEVM.not()
                initView()
            }
            tvMax.setOnClickListener {
                val amount = (fromBalance - 0.001f).formatNum()
                etAmount.setText(amount)
                etAmount.setSelection(etAmount.text.length)
            }
            btnMove.setOnClickListener {
                moveToken()
            }
            ivClose.setOnClickListener {
                dismiss()
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
            EVMWalletManager.moveFlowToken(amount, isFundToEVM) { isSuccess ->
                uiScope {
                    binding.btnMove.setProgressVisible(false)
                    if (isSuccess) {
                        BalanceManager.getBalanceByCoin(FlowCoin.SYMBOL_FLOW)
                        dismiss()
                    } else {
                        toast(R.string.move_flow_to_evm_failed)
                    }
                }
            }
        }
    }

    private fun initView() {
        with(binding) {
            val userInfo = AccountManager.userInfo()
            val walletAddress = WalletManager.wallet()?.walletAddress()
            val evmAddress = EVMWalletManager.getEVMAddress()
            if (isFundToEVM) {
                if (userInfo == null || userInfo.avatar.isBlank()) {
                    ivFromAvatar.setImageResource(R.drawable.ic_switch_vm_cadence)
                } else {
                    ivFromAvatar.loadAvatar(userInfo.avatar)
                }
                tvFromName.text = userInfo?.username ?: R.string.cadence.res2String()
                tvFromAddress.text = walletAddress ?: ""
                ivToAvatar.setImageResource(R.drawable.ic_switch_vm_evm)
                tvToName.text = R.string.flow_evm.res2String()
                tvToAddress.text = evmAddress
            } else {
                ivFromAvatar.setImageResource(R.drawable.ic_switch_vm_evm)
                tvFromName.text = R.string.flow_evm.res2String()
                tvFromAddress.text = evmAddress

                if (userInfo == null || userInfo.avatar.isBlank()) {
                    ivToAvatar.setImageResource(R.drawable.ic_switch_vm_cadence)
                } else {
                    ivToAvatar.loadAvatar(userInfo.avatar)
                }
                tvToName.text = userInfo?.username ?: R.string.cadence.res2String()
                tvToAddress.text = walletAddress ?: ""
            }
            tvBalance.text = ""
            etAmount.setText("")
            btnMove.isEnabled = false
        }
        fetchTokenBalance()
    }

    @SuppressLint("SetTextI18n")
    private fun fetchTokenBalance() {
        ioScope {
            fromBalance = if (isFundToEVM) {
                cadenceQueryTokenBalanceWithAddress(
                    FlowCoinListManager.getCoin(FlowCoin.SYMBOL_FLOW),
                    WalletManager.wallet()?.walletAddress()
                )
            } else {
                cadenceQueryCOATokenBalance()
            } ?: 0f

            uiScope {
                binding.tvBalance.text = "Balance $ $fromBalance"
            }
        }
    }

    companion object {
        fun show(activity: FragmentActivity) {
            MoveTokenDialog().show(activity.supportFragmentManager, "")
        }
    }
}