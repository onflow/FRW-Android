package com.flowfoundation.wallet.page.staking.providers

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.page.staking.providers.model.ProviderTitleModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope

class StakeProviderViewModel : ViewModel() {

    val data = MutableLiveData<List<Any>>()

    fun load() {
        ioScope {
            val providers = StakingManager.providers()
            val lilico = providers.firstOrNull { it.name == "Lilico" }
            val list = mutableListOf<Any>().apply {
                add(ProviderTitleModel(R.string.staking_provider.res2String()))
                addAll(providers.filter { it != lilico })
            }
            data.postValue(list)
        }
    }
}