package io.outblock.lilico.page.receive

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.page.receive.model.ReceiveData
import io.outblock.lilico.utils.ScreenUtils
import io.outblock.lilico.utils.extensions.dp2px
import io.outblock.lilico.utils.toQRBitmap
import io.outblock.lilico.utils.viewModelIOScope
import io.outblock.lilico.wallet.toAddress

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