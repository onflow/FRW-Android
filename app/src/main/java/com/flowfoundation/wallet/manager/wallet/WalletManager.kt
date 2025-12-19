package com.flowfoundation.wallet.manager.wallet

import com.flow.wallet.wallet.Wallet
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.walletdata.ChildWallet
import com.flowfoundation.wallet.manager.walletdata.EOAWallet
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.manager.walletdata.MainWallet
import com.flowfoundation.wallet.utils.getSelectedWalletAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.updateSelectedWalletAddress
import com.flowfoundation.wallet.wallet.toAddress
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

object WalletManager {
    private val TAG = WalletManager::class.java.simpleName
    private val selectedWalletAddressRef = AtomicReference(getSelectedWalletAddress())
    private var currentWallet: Wallet? = null
    private var lastAddressCheck = 0L
    private const val ADDRESS_CACHE_DURATION = 100L // Cache duration in milliseconds
    private val initializationLock = Object()

    private var _isEoaDisabled = false

    fun isEoaDisabled() = _isEoaDisabled
    fun setEoaDisabled(disabled: Boolean) { _isEoaDisabled = disabled }

    /**
     * Get EOA address
     */
    fun getEOAAddress(): String? {
        val eoaWallet = AccountManager.walletNodes()
            ?.filterIsInstance<EOAWallet>()
            ?.firstOrNull()

        if (eoaWallet != null) {
            logd(TAG, "getEOAAddress() - found EOA address in walletNodes: ${eoaWallet.address}")
            return eoaWallet.address
        }

        return null
    }


    private fun initializeWallet(): Boolean {
        logd(TAG, "initializeWallet() called")

        val account = AccountManager.get() ?: run {
            logd(TAG, "No current account available")
            return false
        }

        // Use WalletCreationHelper to create wallet from account
        val newWallet = runBlocking {
            WalletCreationHelper.createWalletFromAccount(account)
        } ?: run {
            logd(TAG, "Failed to create wallet from account")
            return false
        }

        currentWallet = newWallet
        logd(TAG, "Wallet created successfully: ${getCurrentFlowWalletAddress()}")

        val address = getCurrentFlowWalletAddress() ?: run {
            // For hardware-backed keys, try to get address from account data
            val walletData = account.wallet?.wallets?.firstOrNull()
            val blockchainData = walletData?.blockchain?.firstOrNull()
            blockchainData?.address
        }

        // Only set the address if no address is currently selected (avoid overriding user selections)
        if (!address.isNullOrBlank() && selectedWalletAddressRef.get().isBlank()) {
            selectWalletAddress(address)
            logd(TAG, "Selected initial wallet address: $address")
        } else if (!address.isNullOrBlank()) {
            logd(TAG, "Skipping address selection - user has already selected: ${selectedWalletAddressRef.get()}")
        } else {
            logd(TAG, "No wallet address found to select (this may be normal for hardware-backed keys)")
        }

        return true
    }

    fun wallet(): Wallet? = synchronized(initializationLock) {
        // Ensure wallet is initialized
        if (currentWallet == null) {
            logd(TAG, "wallet() called - attempting synchronous initialization")
            initializeWallet() // This call will set currentWallet if successful
        }

        val currentNetwork = chainNetWorkString()

        // Get the account for the current network
        currentWallet?.let { wallet ->
            val walletNodes = AccountManager.walletNodes()
            val currentNetworkFlowWallet = walletNodes
                ?.filterIsInstance<FlowWallet>()
                ?.firstOrNull { it.chainIdString == currentNetwork }

            if (currentNetworkFlowWallet != null) {
                val currentSelected = selectedWalletAddressRef.get()

                // Check if currentSelected exists in walletNodes
                val isOverallValidSelection = walletNodes.any { mainNode ->
                  mainNode.address.equals(currentSelected, ignoreCase = true) ||
                    (mainNode is FlowWallet && mainNode.linkedWallets.any { it.address.equals(currentSelected, ignoreCase = true) })
                }

                if (!isOverallValidSelection) {
                    logd(TAG, "No valid address selected or selection is of unknown type ($currentSelected), setting network account: ${currentNetworkFlowWallet.address}")
                    selectedWalletAddressRef.set(currentNetworkFlowWallet.address)
                    updateSelectedWalletAddress(currentNetworkFlowWallet.address)
                } else {
                    // Check if currentSelected is a Flow Main account but NOT the one for the current network
                    val isSelectedFlowMain = walletNodes.filterIsInstance<FlowWallet>()
                        .any { it.address.equals(currentSelected, ignoreCase = true) }

                    if (isSelectedFlowMain) {
                        if (!currentNetworkFlowWallet.address.equals(currentSelected, ignoreCase = true)) {
                            selectedWalletAddressRef.set(currentNetworkFlowWallet.address)
                            updateSelectedWalletAddress(currentNetworkFlowWallet.address)
                        }
                    }
                }
            }
        }
        currentWallet
    }

    fun isEVMAccountSelected(): Boolean {
        return selectedWalletAddress().toAddress().equals(EVMWalletManager.getEVMAddress()?.toAddress(), ignoreCase = true) || selectedWalletAddress().toAddress().equals(getEOAAddress(), ignoreCase = true)
    }

    fun isSelfFlowAddress(address: String): Boolean {
        if (address.isBlank()) return false

        val walletNodes = AccountManager.walletNodes() ?: return false

        for (mainNode in walletNodes) {
            if (mainNode is FlowWallet) {
                // Check if it's the FlowWallet itself
                if (mainNode.address.equals(address, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    fun isChildAccountSelected(): Boolean {
        return isChildAccount(selectedWalletAddress())
    }

    fun haveChildAccount(): Boolean {
        val firstParentAddress = getCurrentFlowWalletAddress()
        return firstParentAddress != null && childAccountList(firstParentAddress).isNotEmpty()
    }

    fun childAccountList(walletAddress: String? = null): List<ChildAccount> {
        val targetAddress = walletAddress ?: getCurrentFlowWalletAddress() ?: return emptyList()
        val walletNodes = AccountManager.walletNodes() ?: return emptyList()

        for (mainNode in walletNodes) {
            if (mainNode is FlowWallet && mainNode.address.equals(targetAddress, ignoreCase = true)) {
                return mainNode.linkedWallets
                    .filterIsInstance<ChildWallet>()
                    .map { childWallet ->
                        ChildAccount(
                            address = childWallet.address,
                            name = childWallet.name,
                            icon = childWallet.icon,
                            pinTime = childWallet.pinTime
                        )
                    }
            }
        }
        return emptyList()
    }

    fun childAccount(childAddress: String): ChildAccount? {
        if (childAddress.isBlank()) return null

        val walletNodes = AccountManager.walletNodes() ?: return null

        for (mainNode in walletNodes) {
            if (mainNode is FlowWallet) {
                mainNode.linkedWallets.forEach { linkedWallet ->
                    if (linkedWallet is ChildWallet && linkedWallet.address.equals(childAddress, ignoreCase = true)) {
                        return ChildAccount(address = linkedWallet.address, name = linkedWallet.name, icon = linkedWallet.icon, pinTime = linkedWallet.pinTime)
                    }
                }
            }
        }
        return null
    }

    fun isChildAccount(address: String): Boolean {
        val result = childAccount(address) != null
        return result
    }

    fun togglePin(childAccount: ChildAccount) {
        ioScope {
            AccountManager.updateCurrentAccount { currentAccount ->
                var accountModified = false

                val updatedWalletNodes = currentAccount.walletNodes.map { mainNode ->
                    if (mainNode is FlowWallet) {
                        val updatedLinkedWallets = mainNode.linkedWallets.map { linked ->
                            if (linked is ChildWallet && linked.address == childAccount.address) {
                                accountModified = true
                                linked.copy(pinTime = if (linked.pinTime > 0) 0 else System.currentTimeMillis())
                            } else {
                                linked
                            }
                        }
                        mainNode.copy(linkedWallets = updatedLinkedWallets)
                    } else {
                        mainNode
                    }
                }

                if (accountModified) {
                    logd(TAG, "Toggled pin status for child account: ${childAccount.address}")
                    currentAccount.copy(walletNodes = updatedWalletNodes)
                } else {
                    logd(TAG, "Child account ${childAccount.address} not found for toggling pin.")
                    currentAccount
                }
            }
        }
    }

    fun changeNetwork() {
        val currentNetwork = chainNetWorkString()
        logd(TAG, "Changing network to: $currentNetwork")

        val networkAddress = AccountManager.walletNodes()
            ?.filterIsInstance<FlowWallet>()
            ?.firstOrNull { it.chainIdString.equals(currentNetwork, ignoreCase = true) }
            ?.address

        networkAddress?.let { address ->
            logd(TAG, "Selecting network account: $address")
            selectWalletAddress(address)
        }
    }

    fun selectWalletAddress(address: String): String {
        logd(TAG, "selectWalletAddress called with: '$address'")

        if (address.isBlank()) {
            logd(TAG, "WARNING: Attempting to select blank address")
        }

        if (selectedWalletAddressRef.get().equals(address, ignoreCase = true)) {
            logd(TAG, "Address already selected, returning current network")
            return chainNetWorkString()
        }

        logd(TAG, "Setting selected address to: '$address'")
        selectedWalletAddressRef.set(address)
        updateSelectedWalletAddress(address)

        val walletNodes = AccountManager.walletNodes() ?: return chainNetWorkString()

        for (node in walletNodes) {
            // Check if it's the MainWallet itself
            if (node.address.equals(address, ignoreCase = true)) {
                return when (node) {
                    is FlowWallet -> node.chainIdString
                    is EOAWallet -> NETWORK_NAME_MAINNET
                }
            }

            // Check if it's a LinkedWallet
            if (node is FlowWallet) {
                val isLinked = node.linkedWallets.any { it.address.equals(address, ignoreCase = true) }
                if (isLinked) {
                    return node.chainIdString
                }
            }
        }

        return chainNetWorkString()
    }

    fun selectedWalletAddress(): String {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAddressCheck < ADDRESS_CACHE_DURATION) {
            return selectedWalletAddressRef.get()
        }

        lastAddressCheck = currentTime
        val pref = selectedWalletAddressRef.get().toAddress()

        if (pref.isBlank()) {
            logd(TAG, "Selected address is blank")
        }

        val walletNodes = AccountManager.walletNodes()

        // Check if 'pref' exists in walletNodes (either as a MainWallet address or a LinkedWallet address)
        val isExistInNodes = walletNodes?.any { mainWallet ->
            mainWallet.address.equals(pref, ignoreCase = true) ||
            (mainWallet is FlowWallet && mainWallet.linkedWallets.any { linkedWallet ->
                linkedWallet.address.equals(pref, ignoreCase = true)
            })
        } ?: false

        if (isExistInNodes) {
            // If the preference exists in the new structure, return it
            return pref
        }

        // Fallback to the first MainWallet address if the preferred address is not found
        val defaultAddress = walletNodes?.firstOrNull()?.address.orEmpty()
        if (defaultAddress.isNotBlank()) {
            selectedWalletAddressRef.set(defaultAddress)
            updateSelectedWalletAddress(defaultAddress)
            logd(TAG, "Selected address not found in walletNodes. Falling back to default: $defaultAddress")
            return defaultAddress
        }

        // If no nodes exist or default is blank, return whatever is in selectedWalletAddressRef (might be blank)
        logd(TAG, "No valid selected address or default address found. Returning current ref: ${selectedWalletAddressRef.get()}")
        return selectedWalletAddressRef.get()
    }

    fun getCurrentFlowWalletAddress(): String? {
        val selectedAddress = selectedWalletAddress()
        val walletNodes = AccountManager.walletNodes() ?: return null // Handle null walletNodes early

        if (selectedAddress.isBlank()) {
            logd(TAG, "Selected address is blank. Falling back to first FlowWallet for current network.")
            return findFirstFlowWalletAddressForCurrentNetwork(walletNodes)
        }

        // First, check if selectedAddress is an EOA address
        val isSelectedEOA = walletNodes.filterIsInstance<EOAWallet>().any { it.address.equals(selectedAddress, ignoreCase = true) }

        if (isSelectedEOA) {
            logd(TAG, "Selected address is EOA. Falling back to first FlowWallet for current network.")
            return findFirstFlowWalletAddressForCurrentNetwork(walletNodes)
        }

        // If not EOA, proceed with existing logic to find associated FlowWallet
        for (node in walletNodes) {
            if (node is FlowWallet) {
                if (node.address.equals(selectedAddress, ignoreCase = true) || node.linkedWallets.any { it.address.equals(selectedAddress, ignoreCase = true) }) {
                  logd(TAG, "Selected address is FlowWallet or its linked wallet, returning FlowWallet address: ${node.address}")
                  return node.address.toAddress()
                }
            }
        }
        logd(TAG, "Selected address is not EOA, FlowWallet, or its linked wallet. Falling back to first FlowWallet for current network.")
        return findFirstFlowWalletAddressForCurrentNetwork(walletNodes)
    }

    private fun findFirstFlowWalletAddressForCurrentNetwork(walletNodes: List<MainWallet>): String? {
        val currentNetwork = chainNetWorkString()
        val firstCurrentNetworkFlowWallet = walletNodes.filterIsInstance<FlowWallet>()
            .firstOrNull { it.chainIdString.equals(currentNetwork, ignoreCase = true) }

        if (firstCurrentNetworkFlowWallet != null) {
            logd(TAG, "Falling back to first FlowWallet address for current network: ${firstCurrentNetworkFlowWallet.address}")
            return firstCurrentNetworkFlowWallet.address.toAddress()
        } else {
            logd(TAG, "No FlowWallet found for current network. Returning null.")
            return null
        }
    }

    fun clear() {
        synchronized(initializationLock) {
            selectedWalletAddressRef.set("")
            currentWallet = null
            lastAddressCheck = 0
        }
    }
}
