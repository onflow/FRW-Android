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
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.nft.move.SelectAccountDialog
import com.flowfoundation.wallet.page.swap.dialog.select.SelectTokenDialog
import com.flowfoundation.wallet.page.token.list.CadenceTokenListProvider
import com.flowfoundation.wallet.page.token.list.EVMTokenListProvider
import com.flowfoundation.wallet.page.token.list.TokenListProvider
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.MoveError
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setDecimalDigitsFilter
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.toSafeDecimal
import com.flowfoundation.wallet.utils.format
import com.flowfoundation.wallet.utils.getCurrentCodeLocation
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.findActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.math.BigDecimal
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class MoveTokenDialog : BottomSheetDialogFragment() {
    private var contractId: String = FungibleTokenListManager.getFlowTokenContractId()
    private lateinit var binding: DialogMoveTokenBinding
    private var fromBalance = BigDecimal.ZERO
    private var result: Continuation<Boolean>? = null
    private var isFlowCoin = false
    private var moveFromAddress: String = WalletManager.selectedWalletAddress()
    private var moveToAddress: String = if (EVMWalletManager.isEVMWalletAddress(moveFromAddress)) {
        WalletManager.wallet()?.walletAddress().orEmpty()
    } else {
        EVMWalletManager.getEVMAddress().orEmpty()
    }
    private var currentTokenProvider: TokenListProvider? = null
    private var currentToken: FungibleToken? = null
    private var availableTokens: List<FungibleToken> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogMoveTokenBinding.inflate(inflater)
        return binding.rootView
    }

    private fun getProvider(moveFromAddress: String): TokenListProvider {
        val isEVMAddress = EVMWalletManager.isEVMWalletAddress(moveFromAddress)

        // Always create a new provider if the address type doesn't match the current provider
        val existingProvider = currentTokenProvider

        if (existingProvider != null) {
            val providerCurrentAddress = existingProvider.getWalletAddress()

            if (providerCurrentAddress == moveFromAddress) {
                return if (isEVMAddress) {
                    if (existingProvider is EVMTokenListProvider) {
                        existingProvider
                    } else {
                        EVMTokenListProvider(moveFromAddress)
                    }
                } else {
                    if (existingProvider is CadenceTokenListProvider) {
                        existingProvider
                    } else {
                        CadenceTokenListProvider(moveFromAddress)
                    }
                }
            } else {
                return if (isEVMAddress) {
                    EVMTokenListProvider(moveFromAddress)
                } else {
                    CadenceTokenListProvider(moveFromAddress)
                }
            }
        } else {
            return if (isEVMAddress) {
                EVMTokenListProvider(moveFromAddress)
            } else {
                CadenceTokenListProvider(moveFromAddress)
            }
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
        val flowAddress = WalletManager.wallet()?.walletAddress().orEmpty()
        val childAccounts = WalletManager.childAccountList(flowAddress)
            ?.get()
            ?.map { it.address } ?: emptyList()
        val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
        return listOf(flowAddress) + childAccounts + listOf(evmAddress)
    }

    private fun verifyAmount(): Boolean {
        val number = binding.etAmount.text.toString().toFloatOrNull()
        return number != null && number > 0
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        loadTokens()
    }

    @SuppressLint("SetTextI18n")
    private fun setupViews() {
        // Initialize addresses based on whether EVM is selected
        moveToAddress = if (EVMWalletManager.isEVMWalletAddress(moveFromAddress)) {
            WalletManager.wallet()?.walletAddress().orEmpty()
        } else {
            EVMWalletManager.getEVMAddress().orEmpty()
        }

        with(binding) {
            etAmount.setDecimalDigitsFilter(contractId.decimal())
            etAmount.doOnTextChanged { _, _, _, _ ->
                checkAmount()
            }
            etAmount.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    if (verifyAmount() && !binding.llErrorLayout.isVisible()) moveToken()
                }
                return@setOnEditorActionListener false
            }

            coinWrapper.setOnClickListener {
                showTokenSelection()
            }

            tvMoveFee.text = "0.0001 FLOW"
            tvMoveFeeTips.text = R.string.move_fee_tips.res2String()
            ivArrow.setOnClickListener {
                val temp = moveFromAddress
                moveFromAddress = moveToAddress
                moveToAddress = temp
                // Clear current token selection and refresh token list for new from address
                currentToken = null
                loadTokens()
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
                        // Update to address based on new from address
                        moveToAddress = if (EVMWalletManager.isEVMWalletAddress(selected)) {
                            WalletManager.wallet()?.walletAddress().orEmpty()
                        } else {
                            EVMWalletManager.getEVMAddress().orEmpty()
                        }
                        binding.layoutToAccount.setAccountInfo(moveToAddress)
                        tvBalance.text = ""
                        etAmount.setText("")
                        btnMove.isEnabled = false
                        // Refresh token list with new from address
                        loadTokens()
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

    private fun loadTokens() {
        ioScope {
            val provider = getProvider(moveFromAddress)
            currentTokenProvider = provider

            // Get fresh token list with updated balances
            availableTokens = provider.getTokenList(moveFromAddress)

            if (availableTokens.isNotEmpty()) {
                // If we have a current token, try to find it in the new list
                val token = if (currentToken != null) {
                    availableTokens.find { it.contractId() == currentToken!!.contractId() }
                } else {
                    // If no current token, use the contract ID or first available token with non-zero balance
                    availableTokens.firstOrNull { it.contractId() == contractId && it.tokenBalance() > BigDecimal.ZERO }
                        ?: availableTokens.firstOrNull { it.tokenBalance() > BigDecimal.ZERO }
                        ?: availableTokens.first()
                }

                uiScope {
                    if (token != null) {
                        updateSelectedToken(token)
                    }
                }
            } else {
                // If no tokens available, clear the current selection
                uiScope {
                    currentToken = null
                    with(binding) {
                        tvBalance.text = ""
                        etAmount.setText("")
                        btnMove.isEnabled = false
                    }
                }
            }
        }
    }

    private fun showTokenSelection() {
        ioScope {
            val dialog = SelectTokenDialog()

            val provider = getProvider(moveFromAddress)
            currentTokenProvider = provider

            // Get fresh token list with updated balances
            availableTokens = provider.getFungibleTokenListSnapshot()

            // Convert MoveTokens to FlowCoins for the dialog, filtering out zero balances
            val coinsWithBalance = availableTokens
                .filter { it.tokenBalance() > BigDecimal.ZERO }
                .map { it }

            val selectedToken = dialog.show(
                selectedCoin = currentToken?.contractId(),
                disableCoin = null,
                fragmentManager = childFragmentManager,
                moveFromAddress = moveFromAddress,
                availableCoins = coinsWithBalance,
                initialTokens = availableTokens
            )
            if (selectedToken != null) {
                uiScope {
                    // Find the selected token in the updated list
                    val moveToken = availableTokens.find { it.contractId() == selectedToken.contractId() }
                    moveToken?.let {
                        updateSelectedToken(it)
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSelectedToken(token: FungibleToken) {
        currentToken = token
        contractId = token.contractId()
        isFlowCoin = token.isFlowToken()
        fromBalance = token.tokenBalance()

        with(binding) {
            etAmount.setDecimalDigitsFilter(contractId.decimal())
            Glide.with(ivTokenIcon).load(token.tokenIcon()).into(ivTokenIcon)
            tvBalance.text = "${token.tokenBalance().toPlainString()} ${token.symbol.uppercase()}"
            etAmount.setText("")
            btnMove.isEnabled = false
            updateMoveFeeVisibility()
            checkAmount()
        }
    }

    private fun moveToken() {
        if (binding.btnMove.isProgressVisible()) {
            return
        }
        binding.btnMove.setProgressVisible(true)
        ioScope {
            val amount = binding.etAmount.text.ifBlank { "0" }.toString().toSafeDecimal()
            val token = currentToken
            if (token == null) {
                ErrorReporter.reportWithMixpanel(MoveError.LOAD_TOKEN_INFO_FAILED, getCurrentCodeLocation())
                return@ioScope
            }

            val from = moveFromAddress
            val to = moveToAddress

            MixpanelManager.transferFT(
                from,
                to,
                token.symbol,
                amount.toString(),
                token.tokenIdentifier()
            )

            if (token.isFlowToken()) {
                EVMWalletManager.moveFlowToken(token, amount, from, to) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            FungibleTokenListManager.updateTokenList()
                            result?.resume(true)
                            
                            // Navigate back to wallet tab after successful move
                            val activity = findActivity(binding.root)
                            if (activity != null) {
                                MainActivity.launch(activity, HomeTab.WALLET)
                            }
                            
                            dismiss()
                        } else {
                            toast(R.string.move_flow_to_evm_failed)
                        }
                    }
                }
            } else if (token.canBridgeToEVM() || token.canBridgeToCadence()) {
                EVMWalletManager.moveBridgeToken(token, amount, from, to) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            FungibleTokenListManager.updateTokenList()
                            result?.resume(true)
                            
                            // Navigate back to wallet tab after successful move
                            val activity = findActivity(binding.root)
                            if (activity != null) {
                                MainActivity.launch(activity, HomeTab.WALLET)
                            }
                            
                            dismiss()
                        } else {
                            toast(R.string.move_flow_to_evm_failed)
                        }
                    }
                }
            } else {
                EVMWalletManager.transferToken(token, to, amount) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            FungibleTokenListManager.updateTokenList()
                            result?.resume(true)
                            
                            // Navigate back to wallet tab after successful move
                            val activity = findActivity(binding.root)
                            if (activity != null) {
                                MainActivity.launch(activity, HomeTab.WALLET)
                            }
                            
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
                getProvider(moveFromAddress).getTokenById(currentToken!!.contractId())
            } else {
                getProvider(moveFromAddress).getTokenById(contractId)
            }
            isFlowCoin = currentToken?.isFlowToken() ?: false
            fromBalance = currentToken?.tokenBalance() ?: BigDecimal.ZERO
            uiScope {
                currentToken?.let {
                    Glide.with(binding.ivTokenIcon).load(it.tokenIcon()).into(binding.ivTokenIcon)
                }
                binding.tvBalance.text = Env.getApp().getString(R.string.balance_value, fromBalance.format(8))
            }
        }
    }

    private fun String.decimal(): Int {
        return FungibleTokenListManager.getTokenById(this)?.run {
            kotlin.math.min(tokenDecimal(), 8)
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