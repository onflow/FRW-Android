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
import com.flowfoundation.wallet.databinding.DialogSelectTokenBinding
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SelectTokenDialog : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "SelectTokenDialog"
        private var isShowing = false
    }

    private var selectedCoin: String? = null
    private var disableCoin: String? = null
    private var result: Continuation<FungibleToken?>? = null
    private var currentSearchKeyword: String = ""
    private var moveFromAddress: String? = null
    private var availableTokens: List<FungibleToken> = emptyList()
    private var lastClickTime: Long = 0
    private val CLICK_DEBOUNCE_TIME = 500L // 500ms debounce time
    private var initialAvailableCoins: List<FungibleToken>? = null
    private var pendingUpdate: Boolean = false
    private var lastUpdateTime: Long = 0
    private val UI_UPDATE_DEBOUNCE_TIME = 100L // 100ms debounce for UI updates

    private lateinit var binding: DialogSelectTokenBinding

    private val adapter by lazy {
        TokenListAdapter(selectedCoin, disableCoin) { token ->
            if (!isClickValid()) return@TokenListAdapter
            result?.resume(token)
            dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isShowing = false
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        isShowing = false
    }

    private fun scheduleUiUpdate(tokens: List<FungibleToken>) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < UI_UPDATE_DEBOUNCE_TIME) {
            pendingUpdate = true
            return
        }
        
        lastUpdateTime = currentTime
        pendingUpdate = false
        adapter.setNewDiffData(tokens)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSelectTokenBinding.inflate(inflater)
        return binding.root
    }

    private fun isClickValid(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_TIME) {
            return false
        }
        lastClickTime = currentTime
        return true
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
            if (!isClickValid()) return@setOnClickListener
            onSearchFocusChange(false)
            binding.searchInput.hideKeyboard()
            binding.searchInput.setText("")
            binding.searchInput.clearFocus()
            clearSearch()
            result?.resume(null)
            dismiss()
        }

        // Show initial data from availableTokens
        val tokensToShow = initialAvailableCoins?.let { coins ->
            availableTokens.filter { token ->
                coins.any { coin -> coin.contractId() == token.contractId() }
            }
        } ?: availableTokens
        adapter.setNewDiffData(tokensToShow)
    }

    private fun onSearchFocusChange(hasFocus: Boolean) {
        binding.tokenListLabel.setVisible(!hasFocus)
    }

    private fun loadTokens() {
        ioScope {
            try {
                // Only update prices for existing tokens
                if (availableTokens.isNotEmpty()) {
                    uiScope {
                        // If we have initialAvailableCoins, filter the tokens before showing
                        val tokensToShow = initialAvailableCoins?.let { coins ->
                            availableTokens.filter.filter { token ->
                                coins.any { coin -> coin.contractId() == token.contractId() }
                            }
                        } ?: availableTokens.filter
                        scheduleUiUpdate(tokensToShow)
                    }
                }
            } catch (e: Exception) {
                logd("SelectTokenDialog", "Error updating token prices: ${e.message}")
            }
        }
    }

    fun search(keyword: String) {
        currentSearchKeyword = keyword
        val filteredTokens = if (keyword.isBlank()) {
            availableTokens
        } else {
            availableTokens.filter { token ->
                token.name.lowercase().contains(keyword.lowercase()) || token.symbol.lowercase().contains(keyword.lowercase())
            }
        }
        scheduleUiUpdate(filteredTokens)
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
        availableCoins: List<FungibleToken>? = null,
        initialTokens: List<FungibleToken>? = null
    ) = suspendCoroutine { result ->
        // Check if dialog is already showing or if it's too soon after last show
        if (isShowing || fragmentManager.findFragmentByTag(TAG) != null) {
            result.resume(null)
            return@suspendCoroutine
        }
        
        isShowing = true
        this.selectedCoin = selectedCoin
        this.disableCoin = disableCoin
        this.result = result
        this.moveFromAddress = moveFromAddress
        this.initialAvailableCoins = availableCoins
        this.availableTokens = initialTokens ?: emptyList()
        adapter.updateSelectedCoin(selectedCoin)

        // Show dialog immediately
        show(fragmentManager, TAG)

        // Update prices in background
        loadTokens()
    }

    override fun onResume() {
        if (result == null) {
            dismiss()
        }
        super.onResume()
    }
}