package com.flowfoundation.wallet.page.receive

import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.receive.model.ReceiveData
import com.flowfoundation.wallet.utils.toQRDrawable
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.toAddress

class ReceiveViewModel : ViewModel() {

    val qrcodeLiveData = MutableLiveData<Drawable>()
    val walletLiveData = MutableLiveData<ReceiveData>()

    fun load() {
        viewModelIOScope(this) {

            val (address, name) = if (WalletManager.isChildAccountSelected()) {
                val account = WalletManager.childAccount(WalletManager.selectedWalletAddress())
                account?.address.orEmpty() to account?.name.orEmpty()
            } else {
                val wallet = WalletManager.wallet()?.wallet() ?: return@viewModelIOScope
                wallet.address().orEmpty() to wallet.name
            }
            WalletManager.wallet()?.wallet() ?: return@viewModelIOScope
            walletLiveData.postValue(ReceiveData(walletName = name, address = address))

            val drawable = address.toAddress().toQRDrawable()
            qrcodeLiveData.postValue(drawable)
        }
    }

}