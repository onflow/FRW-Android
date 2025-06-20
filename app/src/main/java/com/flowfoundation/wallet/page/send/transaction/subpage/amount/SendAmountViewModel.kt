package com.flowfoundation.wallet.page.send.transaction.subpage.amount

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.SendBalanceModel
import com.flowfoundation.wallet.utils.viewModelIOScope

class SendAmountViewModel : ViewModel(), FungibleTokenUpdateListener {
    private lateinit var contact: AddressBookContact

    val balanceLiveData = MutableLiveData<SendBalanceModel>()
    private var initialAmount: String? = null

    val onCoinSwap = MutableLiveData<Boolean>()

    private var currentCoin = FungibleTokenListManager.getFlowToken()?.contractId().orEmpty()
    private var convertCoin = selectedCurrency().flag

    init {
        FungibleTokenListManager.addTokenUpdateListener(this)
    }

    fun currentCoin() = currentCoin
    fun convertCoin() = convertCoin

    fun setContact(contact: AddressBookContact) {
        this.contact = contact
    }

    fun contact() = contact

    fun setInitialAmount(amount: String?) {
        this.initialAmount = amount
    }

    fun getInitialAmount() = initialAmount

    fun load() {
        viewModelIOScope(this) {
            val coin = FungibleTokenListManager.getTokenById(currentCoin) ?: return@viewModelIOScope
            balanceLiveData.postValue(SendBalanceModel(contractId = coin.contractId()))
            FungibleTokenListManager.updateTokenInfo(currentCoin)
        }
    }

    fun swapCoin() {
        val temp = currentCoin
        currentCoin = convertCoin
        convertCoin = temp
        onCoinSwap.postValue(true)
    }

    fun changeToken(token: FungibleToken) {
        if (currentCoin == token.contractId()) {
            return
        }
        currentCoin = token.contractId()
        onCoinSwap.postValue(true)
        load()
    }

    override fun onTokenUpdated(token: FungibleToken) {
        if (token.isSameToken(currentCoin)) {
            val data = balanceLiveData.value ?: SendBalanceModel(token.contractId())
            balanceLiveData.value = data.copy(
                coinRate = token.tokenPrice(),
                balance = token.tokenBalance()
            )
        }
    }
}
