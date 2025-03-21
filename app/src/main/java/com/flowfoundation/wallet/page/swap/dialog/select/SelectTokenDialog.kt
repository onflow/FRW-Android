package com.flowfoundation.wallet.page.swap.dialog.select

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogSelectTokenBinding
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.token.detail.model.MoveToken
import com.flowfoundation.wallet.page.token.detail.provider.EVMAccountTokenProvider
import com.flowfoundation.wallet.page.token.detail.provider.FlowAccountTokenProvider
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.math.BigDecimal

class SelectTokenDialog : BottomSheetDialogFragment(), OnCoinRateUpdate {

    companion object {
        private const val TAG = "SelectTokenDialog"
    }

    private var selectedCoin: String? = null
    private var disableCoin: String? = null
    private var result: Continuation<FlowCoin?>? = null
    private var currentSearchKeyword: String = ""
    private var moveFromAddress: String? = null
    private var availableTokens: List<MoveToken> = emptyList()

    private lateinit var binding: DialogSelectTokenBinding

    private val adapter by lazy {
        TokenListAdapter(selectedCoin, disableCoin) { token ->
            result?.resume(token.tokenInfo)
            dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoinRateManager.addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        CoinRateManager.removeListener(this)
    }

    private fun calculateDollarValue(token: MoveToken): BigDecimal? {
        val rate = CoinRateManager.coinRate(token.tokenInfo.contractId())
        return when {
            // If we have a rate, use it
            rate != null && rate > BigDecimal.ZERO -> {
                token.tokenBalance * rate
            }
            // For USDC tokens, use 1:1 rate
            token.tokenInfo.symbol.contains("USDC", ignoreCase = true) -> {
                token.tokenBalance
            }
            // For WFLOW and stFlow, try to use FLOW rate
            token.tokenInfo.symbol.equals("WFLOW", ignoreCase = true) || 
            token.tokenInfo.symbol.equals("stFlow", ignoreCase = true) -> {
                val flowRate = CoinRateManager.coinRate("A.1654653399040a61.FlowToken")
                if (flowRate != null && flowRate > BigDecimal.ZERO) {
                    token.tokenBalance * flowRate
                } else null
            }
            // No rate available
            else -> null
        }
    }

    private fun updateTokensWithDollarValues(tokens: List<MoveToken>): List<MoveToken> {
        return tokens.map { token ->
            val dollarValue = calculateDollarValue(token)
            token.copy(dollarValue = dollarValue)
        }
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: BigDecimal, quoteChange: Float) {
        // When rates update, recalculate dollar values and refresh the adapter
        uiScope {
            val updatedTokens = updateTokensWithDollarValues(availableTokens)
            availableTokens = updatedTokens
            adapter.setNewDiffData(updatedTokens)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSelectTokenBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        result ?: return
        with(binding.tokenList) {
            adapter = this@SelectTokenDialog.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt())
            )
        }
        with(binding.searchInput) {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    search(text.toString().trim())
                    clearFocus()
                }
                return@setOnEditorActionListener false
            }
            doOnTextChanged { text, _, _, _ ->
                search(text.toString().trim())
            }
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> onSearchFocusChange(hasFocus) }
        }

        binding.closeButton.setOnClickListener {
            onSearchFocusChange(false)
            binding.searchInput.hideKeyboard()
            binding.searchInput.setText("")
            binding.searchInput.clearFocus()
            clearSearch()
            result?.resume(null)
            dismiss()
        }

        loadTokens()
    }

    private fun onSearchFocusChange(hasFocus: Boolean) {
        binding.tokenListLabel.setVisible(!hasFocus)
    }

    private fun loadTokens() {
        ioScope {
            val fromAddress = moveFromAddress ?: WalletManager.selectedWalletAddress()

            try {
                val provider = if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
                    EVMAccountTokenProvider()
                } else {
                    FlowAccountTokenProvider()
                }

                // Get token list with balances
                val tokens = provider.getMoveTokenList(fromAddress)
                    .filter { it.tokenBalance > BigDecimal.ZERO }
                    .sortedByDescending { it.tokenBalance }

                if (tokens.isNotEmpty()) {
                    // Fetch rates for all tokens at once
                    CoinRateManager.fetchCoinListRate(tokens.map { it.tokenInfo })
                    
                    // Calculate dollar values and update the list
                    availableTokens = updateTokensWithDollarValues(tokens)

                    // Update UI with the new token list
                    uiScope {
                        adapter.setNewDiffData(availableTokens)
                    }
                }
            } catch (e: Exception) {
                logd("SelectTokenDialog", "Error loading token list: ${e.message}")
            }
        }
    }

    fun search(keyword: String) {
        currentSearchKeyword = keyword
        val filteredTokens = if (keyword.isBlank()) {
            availableTokens
        } else {
            availableTokens.filter { token ->
                token.tokenInfo.name.lowercase().contains(keyword.lowercase()) || 
                token.tokenInfo.symbol.lowercase().contains(keyword.lowercase())
            }
        }
        adapter.setNewDiffData(filteredTokens)
    }

    fun clearSearch() {
        search("")
    }

    override fun onCancel(dialog: DialogInterface) {
        result?.resume(null)
    }

    suspend fun show(
        selectedCoin: String?,
        disableCoin: String?,
        fragmentManager: FragmentManager,
        moveFromAddress: String?,
        availableCoins: List<FlowCoin>? = null
    ) = suspendCoroutine { result ->
        // Dismiss any existing instance of the dialog
        (fragmentManager.findFragmentByTag(TAG) as? SelectTokenDialog)?.dismiss()
        
        this.selectedCoin = selectedCoin
        this.disableCoin = disableCoin
        this.result = result
        this.moveFromAddress = moveFromAddress
        adapter.updateSelectedCoin(selectedCoin)
        show(fragmentManager, TAG)
    }

    override fun onResume() {
        if (result == null) {
            dismiss()
        }
        super.onResume()
    }
}