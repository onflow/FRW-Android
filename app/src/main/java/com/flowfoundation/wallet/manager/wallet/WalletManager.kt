package com.flowfoundation.wallet.manager.wallet

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.ChildAccountList
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.utils.getSelectedWalletAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.updateSelectedWalletAddress
import com.flowfoundation.wallet.wallet.toAddress

object WalletManager {

    private var childAccountMap = mutableMapOf<String, ChildAccountList>()

    private var selectedWalletAddress: String = ""

    fun init() {
        ioScope {
            selectedWalletAddress = getSelectedWalletAddress().orEmpty()
        }
    }

    fun walletUpdate() {
        wallet()?.let { refreshChildAccount(it) }
    }

    fun wallet() = AccountManager.get()?.wallet

    fun isEVMAccountSelected(): Boolean {
        return selectedWalletAddress.toAddress().equals(EVMWalletManager.getEVMAddress()?.toAddress(), ignoreCase = true)
    }

    fun isSelfFlowAddress(address: String): Boolean {
        return wallet()?.walletAddress() == address
    }

    fun isChildAccountSelected(): Boolean {
        val wallets = wallet()?.wallets
        if (wallets.isNullOrEmpty()) {
            return false
        }
        return wallets.firstOrNull { it.address().equals(selectedWalletAddress, ignoreCase = true) } == null
                && isEVMAccountSelected().not()
    }

    fun haveChildAccount(): Boolean {
        val accountList = childAccountList(wallet()?.walletAddress())
        return accountList != null && accountList.get().isNotEmpty()
    }

    fun childAccountList(walletAddress: String? = null): ChildAccountList? {
        val address = (walletAddress ?: wallet()?.walletAddress()) ?: return null
        return childAccountMap[address]
    }

    fun childAccount(childAddress: String): ChildAccount? {
        return childAccountMap.toMap().values.flatMap { it.get() }.firstOrNull {
            it.address.equals(childAddress, ignoreCase = true)
        }
    }

    fun isChildAccount(address: String): Boolean {
        return childAccount(address) != null
    }

    fun refreshChildAccount() {
        childAccountMap.values.forEach { it.refresh() }
    }

    fun changeNetwork() {
        wallet()?.walletAddress()?.let { selectWalletAddress(it) }
    }

    // @return network
    fun selectWalletAddress(address: String): String {
        if (selectedWalletAddress.equals(address, ignoreCase = true)) return chainNetWorkString()

        selectedWalletAddress = address
        updateSelectedWalletAddress(address)

        val walletData = wallet()?.wallets?.firstOrNull { it.address().equals(address, ignoreCase = true) }
        val networkStr = if (walletData == null) {
            val walletAddress = childAccountMap.values
                .firstOrNull { child -> child.get().any { it.address.equals(address, true) } }?.address

            val data = wallet()?.wallets?.firstOrNull { it.address().equals(walletAddress, ignoreCase = true) }

            data?.network()
        } else walletData.network()

        return networkStr ?: chainNetWorkString()
    }

    fun selectedWalletAddress(): String {
        val pref = selectedWalletAddress.toAddress()
        val isExist = childAccountMap.keys.contains(pref) || childAccount(pref) != null || EVMWalletManager.isEVMWalletAddress(pref)
        if (isExist) {
            return pref
        }

        return wallet()?.walletAddress().orEmpty().apply {
            if (isNotBlank()) {
                selectedWalletAddress = this
                updateSelectedWalletAddress(this)
            }
        }
    }

    fun clear() {
        selectedWalletAddress = ""
        childAccountMap.clear()
    }

    private fun refreshChildAccount(wallet: WalletListData) {
//        To be optimized getAllWalletChildAccount with each chain id
//        childAccountMap = wallet.wallets?.associate {
//            it.address().orEmpty() to ChildAccountList(it.address().orEmpty())
//        }.orEmpty().filter {
//            it.key.isNotBlank()
//        }
        // get current wallet child account
        wallet.walletAddress()?.let {
            if (childAccountMap.contains(it)) {
                childAccountMap[it]?.refresh()
            } else {
                childAccountMap[it] = ChildAccountList(it)
            }
        }
    }
}