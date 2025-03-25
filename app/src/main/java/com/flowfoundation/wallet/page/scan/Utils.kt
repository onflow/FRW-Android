package com.flowfoundation.wallet.page.scan

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.component.deeplinking.getWalletConnectUri
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountActivity
import com.flowfoundation.wallet.utils.addressPattern
import com.flowfoundation.wallet.utils.evmAddressPattern
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.wallet.toAddress

const val METAMASK_ETH_SCHEME = "ethereum:"
fun dispatchScanResult(context: Context, str: String) {
    val text = str.trim()
    if (text.isBlank()) {
        return
    }

    if ((text.startsWith("wc:") || text.startsWith("lilico://wc?")
                || text.startsWith("frw://wc?") || text.startsWith("fw://wc?"))
        && AppConfig.walletConnectEnable()
    ) {
        if (WalletManager.isChildAccountSelected()) {
            return
        }
        val wcUri = if (text.startsWith("wc:")) {
            text
        } else {
            getWalletConnectUri(Uri.parse(text))
        } ?: return
        logd("wc", "wcUri: $wcUri")
        WalletConnect.get().pair(wcUri)
    } else if (text.startsWith(METAMASK_ETH_SCHEME)) {
        val addressText = Uri.parse(text).schemeSpecificPart
        if (evmAddressPattern.matches(addressText).not()) {
            return
        }
        if (WalletManager.isChildAccountSelected()) {
            return
        }
        if (EVMWalletManager.haveEVMAddress().not()) {
            return
        }
        SendAmountActivity.launch(
            context as Activity,
            AddressBookContact(address = addressText.toAddress()),
            FlowCoinListManager.getFlowCoinContractId()
        )
    } else if (addressPattern.matches(text) || evmAddressPattern.matches(text)) {
        if (WalletManager.isChildAccountSelected()) {
            return
        }
        SendAmountActivity.launch(
            context as Activity,
            AddressBookContact(address = text.toAddress()),
            FlowCoinListManager.getFlowCoinContractId()
        )
    } else if (URLUtil.isValidUrl(text.httpPrefix())) {
        openBrowser(context as Activity, text.httpPrefix())
    }
}

private fun String.httpPrefix(): String {
    if (startsWith("http")) return this

    return "https://$this"
}