package com.flowfoundation.wallet.page.profile.subpage.currency

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import com.flowfoundation.wallet.page.profile.subpage.currency.model.CurrencyItemModel
import com.flowfoundation.wallet.utils.getCurrencyFlag
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.updateCurrencyFlag

class CurrencyViewModel : ViewModel() {

    val dataLiveData = MutableLiveData<List<CurrencyItemModel>>()

    private var flag = ""

    fun load() {
        ioScope {
            flag = flag.ifEmpty { getCurrencyFlag() }
            dataLiveData.postValue(Currency.entries.map { CurrencyItemModel(it, isSelected = it.flag == flag) })
        }
    }

    fun updateFlag(flag: String) {
        ioScope {
            this.flag = flag
            updateCurrencyFlag(flag)
            load()
            CurrencyManager.updateCurrency(flag)
        }
    }

}