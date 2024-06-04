package com.flowfoundation.wallet.page.main.widget

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.NETWORK_MAINNET
import com.flowfoundation.wallet.utils.NETWORK_PREVIEWNET
import com.flowfoundation.wallet.utils.NETWORK_TESTNET
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.networkPopupMenu
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import kotlinx.coroutines.delay


class NetworkPopupMenu (
    private val view: View,
) {
    fun show() {
        uiScope {
            networkPopupMenu(
                view,
                items = if (WalletManager.isPreviewnetWalletCreated()) {
                    listOf(
                        NetworkPopupListView.ItemData(R.string.mainnet.res2String()),
                        NetworkPopupListView.ItemData(R.string.testnet.res2String()),
                        NetworkPopupListView.ItemData(R.string.previewnet.res2String()),
                    )
                } else {
                    listOf(
                        NetworkPopupListView.ItemData(R.string.mainnet.res2String()),
                        NetworkPopupListView.ItemData(R.string.testnet.res2String()),
                    )
                },
                selectListener = { _, text -> onMenuItemClick(text) },
            ).show()
        }
    }

    private fun onMenuItemClick(text: String): Boolean {
        when (text) {
            R.string.mainnet.res2String() -> changeNetwork(NETWORK_MAINNET)
            R.string.testnet.res2String() -> changeNetwork(NETWORK_TESTNET)
            R.string.previewnet.res2String() -> changeNetwork(NETWORK_PREVIEWNET)
        }
        return true
    }

    private fun changeNetwork(network: Int) {
        updateChainNetworkPreference(network) {
            ioScope {
                delay(200)
                refreshChainNetworkSync()
                doNetworkChangeTask()
                uiScope {
                    FlowLoadingDialog(view.context).show()
                    delay(200)
                    WalletManager.changeNetwork()
                    clearUserCache()
                    MainActivity.relaunch(view.context)
                }
            }
        }
    }
}