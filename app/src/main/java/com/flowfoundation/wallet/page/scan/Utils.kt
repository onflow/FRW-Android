package com.flowfoundation.wallet.page.scan

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.component.deeplinking.UriHandler
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountActivity
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.utils.addressPattern
import com.flowfoundation.wallet.utils.evmAddressPattern
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress

const val METAMASK_ETH_SCHEME = "ethereum:"
fun dispatchScanResult(context: Context, result: String, sourceTab: HomeTab? = null) {
    ioScope {
        val text = result.trim()
        if (text.isBlank()) {
            return@ioScope
        }

        if ((text.startsWith("wc:") || text.startsWith("lilico://wc?")
                    || text.startsWith("frw://wc?") || text.startsWith("fw://wc?"))
            && AppConfig.walletConnectEnable()
        ) {
            if (WalletManager.isChildAccountSelected()) {
                return@ioScope
            }
            val wcUri = if (text.startsWith("wc:")) {
                text
            } else {
                UriHandler.extractWalletConnectUri(Uri.parse(text))
            } ?: return@ioScope
            logd("wc", "wcUri: $wcUri")
            WalletConnect.get().pair(wcUri)
        } else if (text.startsWith(METAMASK_ETH_SCHEME)) {
            val addressText = Uri.parse(text).schemeSpecificPart
            if (evmAddressPattern.matches(addressText).not()) {
                return@ioScope
            }
            if (WalletManager.isChildAccountSelected()) {
                return@ioScope
            }
            if (EVMWalletManager.haveEVMAddress().not()) {
                return@ioScope
            }
            uiScope {
                SendAmountActivity.launch(
                    context,
                    AddressBookContact(address = addressText.toAddress()),
                    FungibleTokenListManager.getFlowTokenContractId(),
                    sourceTab = sourceTab
                )
            }
        } else if (addressPattern.matches(text) || evmAddressPattern.matches(text)) {
            if (WalletManager.isChildAccountSelected()) {
                return@ioScope
            }
            uiScope {
                SendAmountActivity.launch(
                    context,
                    AddressBookContact(address = text.toAddress()),
                    FungibleTokenListManager.getFlowTokenContractId(),
                    sourceTab = sourceTab
                )
            }
        } else if (URLUtil.isValidUrl(text.httpPrefix())) {
            openBrowser(context as Activity, text.httpPrefix())
        } else if (text.startsWith("https://frw-link.lilico.app")) {
            val uri = Uri.parse(text)
            val address = uri.getQueryParameter("address")
            val amount = uri.getQueryParameter("amount")?.toBigDecimalOrNull()
            if (!address.isNullOrBlank()) {
                uiScope {
                    SendAmountActivity.launch(
                        context,
                        AddressBookContact(address = address.toAddress()),
                        FlowCoinListManager.getFlowCoinContractId(),
                        amount?.toString(),
                        sourceTab = sourceTab
                    )
                }
                return@ioScope
            }
        }
    }
}

private fun String.httpPrefix(): String {
    if (startsWith("http")) return this

    return "https://$this"
}