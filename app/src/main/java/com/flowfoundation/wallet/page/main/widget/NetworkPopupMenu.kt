package com.flowfoundation.wallet.page.main.widget

import android.view.View
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_TESTNET
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.NETWORK_MAINNET
import com.flowfoundation.wallet.utils.NETWORK_TESTNET
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.networkPopupMenu
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import kotlinx.coroutines.delay

class NetworkPopupMenu (
    private val view: View,
) {

    private var isClicking = false

    fun show() {
        uiScope {
            networkPopupMenu(
                view,
                items = listOf(
                    NetworkPopupListView.ItemData(NETWORK_NAME_MAINNET),
                    NetworkPopupListView.ItemData(NETWORK_NAME_TESTNET),
                ),
                selectListener = { _, text -> onMenuItemClick(text) },
            ).show()
        }
    }

    private fun onMenuItemClick(text: String): Boolean {
        if (isClicking) {
            return true
        }
        isClicking = true
        when (text) {
            NETWORK_NAME_MAINNET -> changeNetwork(NETWORK_MAINNET)
            NETWORK_NAME_TESTNET -> changeNetwork(NETWORK_TESTNET)
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
                    isClicking = false
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