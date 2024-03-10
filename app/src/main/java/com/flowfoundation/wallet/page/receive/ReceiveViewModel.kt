package com.flowfoundation.wallet.page.receive

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.receive.model.ReceiveData
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.toQRBitmap
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.toAddress

class ReceiveViewModel : ViewModel() {

    private val size by lazy { ScreenUtils.getScreenWidth() - (30 * 2).dp2px().toInt() }

    val qrcodeLiveData = MutableLiveData<Bitmap>()
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
            val wallet = WalletManager.wallet()?.wallet() ?: return@viewModelIOScope
            walletLiveData.postValue(ReceiveData(walletName = name, address = address))

            val bitmap = address.toAddress().toQRBitmap(width = size, height = size) ?: return@viewModelIOScope
            qrcodeLiveData.postValue(bitmap)
        }
    }

}