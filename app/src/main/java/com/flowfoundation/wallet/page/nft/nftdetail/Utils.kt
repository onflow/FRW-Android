package com.flowfoundation.wallet.page.nft.nftdetail

import android.view.ViewGroup
import androidx.core.view.drawToBitmap
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftdetail.widget.NftShareView
import com.flowfoundation.wallet.utils.CACHE_PATH
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.saveToFile
import com.flowfoundation.wallet.utils.uiScope
import kotlinx.coroutines.delay
import java.io.File

fun shareNft(wrapper: ViewGroup, nft: Nft, onReady: (file: File) -> Unit) {
    ioScope {
        val userInfo = AccountManager.userInfo() ?: return@ioScope
        uiScope {
            val view = NftShareView(wrapper.context, userInfo, nft)
            wrapper.removeAllViews()
            wrapper.addView(view)
            view.setup {
                ioScope {
                    delay(1000)
                    logd("shareNft", "drawToBitmap start")
                    val bitmap = it.drawToBitmap()
                    val file = File(CACHE_PATH, "nft_share.jpg")
                    bitmap.saveToFile(file)
                    uiScope { onReady.invoke(file) }
                    logd("shareNft", "shareNft save bitmap finish")
                }
            }
        }
    }
}
