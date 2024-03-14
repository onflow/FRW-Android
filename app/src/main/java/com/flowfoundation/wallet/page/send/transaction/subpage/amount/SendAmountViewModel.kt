package com.flowfoundation.wallet.page.send.transaction.subpage.amount

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.Balance
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.account.OnBalanceUpdate
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.SendBalanceModel
import com.flowfoundation.wallet.utils.viewModelIOScope

class SendAmountViewModel : ViewModel(), OnBalanceUpdate, OnCoinRateUpdate {
    private lateinit var contact: AddressBookContact

    val balanceLiveData = MutableLiveData<SendBalanceModel>()

    val onCoinSwap = MutableLiveData<Boolean>()

    private var currentCoin = FlowCoin.SYMBOL_FLOW
    private var convertCoin = selectedCurrency().flag

    init {
        BalanceManager.addListener(this)
        CoinRateManager.addListener(this)
    }

    fun currentCoin() = currentCoin
    fun convertCoin() = convertCoin

    fun setContact(contact: AddressBookContact) {
        this.contact = contact
    }

    fun contact() = contact

    fun load() {
        viewModelIOScope(this) {
            val coin = FlowCoinListManager.getCoin(currentCoin) ?: return@viewModelIOScope
            balanceLiveData.postValue(SendBalanceModel(symbol = coin.symbol))
            BalanceManager.getBalanceByCoin(coin)
            CoinRateManager.fetchCoinRate(coin)
        }
    }

    fun swapCoin() {
        val temp = currentCoin
        currentCoin = convertCoin
        convertCoin = temp
        onCoinSwap.postValue(true)
    }

    fun changeCoin(coin: FlowCoin) {
        if (currentCoin == coin.symbol) {
            return
        }
        currentCoin = coin.symbol
        onCoinSwap.postValue(true)
        load()
    }

    override fun onBalanceUpdate(coin: FlowCoin, balance: Balance) {
        if (coin.symbol != currentCoin) {
            return
        }
        val data = balanceLiveData.value ?: SendBalanceModel(coin.symbol)
        balanceLiveData.value = data.copy(balance = balance.balance)
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: Float) {
        if (coin.symbol != currentCoin) {
            return
        }
        val data = balanceLiveData.value ?: SendBalanceModel(coin.symbol)
        balanceLiveData.value = data.copy(coinRate = price)
    }
}

