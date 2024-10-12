package com.flowfoundation.wallet.page.profile.subpage.wallet

import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.TokenData
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.queryChildAccountNFTCollectionID
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.queryChildAccountTokens
import com.flowfoundation.wallet.utils.ioScope

/**
 * Created by Mengxy on 9/14/23.
 */
object ChildAccountCollectionManager {

    private val tokenList = mutableListOf<TokenData>()
    private val collectionIdList = mutableListOf<String>()

    fun loadChildAccountTokenList() {
        if (WalletManager.isChildAccountSelected().not()) {
            return
        }
        ioScope {
            tokenList.clear()
            tokenList.addAll(queryChildAccountTokens(WalletManager.selectedWalletAddress()))
        }
    }

    fun loadChildAccountNFTCollectionList() {
        if (WalletManager.isChildAccountSelected().not()) {
            return
        }
        ioScope {
            collectionIdList.clear()
            collectionIdList.addAll(queryChildAccountNFTCollectionID(WalletManager.selectedWalletAddress()))
        }
    }

    fun isTokenAccessible(contractName: String, tokenAddress: String): Boolean {
        if (WalletManager.isChildAccountSelected().not()) {
            return true
        }
        return tokenList.firstOrNull {
            val idSplitList = it.id.split(".", ignoreCase = true, limit = 0)
            val name = idSplitList[2]
            val address = idSplitList[1]
            return contractName == name && tokenAddress == address
        } != null
    }

    fun isNFTCollectionAccessible(contractId: String): Boolean {
        if (WalletManager.isChildAccountSelected().not()) {
            return true
        }
        return collectionIdList.firstOrNull { it == contractId } != null
    }

    fun isNFTAccessible(collectionAddress: String, collectionContractName: String): Boolean {
        if (WalletManager.isChildAccountSelected().not()) {
            return true
        }
        val collection = NftCollectionConfig.get(collectionAddress, collectionContractName)

        return collectionIdList.firstOrNull { it == collection?.contractIdWithCollection() } != null
    }
}

