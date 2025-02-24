package com.flowfoundation.wallet.page.nft.nftdetail.widget

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.nft.nftdetail.NftDetailViewModel
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.video
import com.flowfoundation.wallet.page.nft.nftlist.websiteUrl
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.popupMenu
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.popup.PopupListView

class NftMorePopupMenu(
    private val activity: FragmentActivity,
    private val nft: Nft,
    private val view: View,
    private val color: Int,
    private val onDownloadRequest: (String) -> Unit,
) {
    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[NftDetailViewModel::class.java] }

    fun show() {
        uiScope {
            popupMenu(
                view,
                items = listOf(
                    PopupListView.ItemData(R.string.download.res2String(), iconRes = R.drawable.ic_download, iconTint = color),
                    PopupListView.ItemData(R.string.view_on_web.res2String(), iconRes = R.drawable.ic_web, iconTint = color),
                ),
                selectListener = { _, text -> onMenuItemClick(text) },
            ).show()
        }
    }

    private fun onMenuItemClick(text: String): Boolean {
        when (text) {
            R.string.download.res2String() -> downloadNftMedia()
            R.string.view_on_web.res2String() -> openNftWebsite()
        }
        return true
    }

    private fun downloadNftMedia() {
        val media = nft.video() ?: nft.cover()
        media?.let {
            onDownloadRequest(it)
        }
    }

    private fun openNftWebsite() {
        ioScope {
            val address = WalletManager.selectedWalletAddress().orEmpty()
            uiScope {
                openBrowser(
                    findActivity(view)!!,
                    url = nft.websiteUrl(address)
                )
            }
        }
    }
}
