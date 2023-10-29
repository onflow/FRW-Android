package io.outblock.lilico.page.profile.subpage.wallet

import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.page.profile.subpage.wallet.childaccountdetail.NFTCollectionIDData
import io.outblock.lilico.page.profile.subpage.wallet.childaccountdetail.TokenData
import io.outblock.lilico.page.profile.subpage.wallet.childaccountdetail.queryChildAccountNFTCollectionID
import io.outblock.lilico.page.profile.subpage.wallet.childaccountdetail.queryChildAccountTokens
import io.outblock.lilico.utils.ioScope

/**
 * Created by Mengxy on 9/14/23.
 */
object ChildAccountCollectionManager {

    private val tokenList = mutableListOf<TokenData>()
    private val collectionList = mutableListOf<NFTCollectionIDData>()

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
            collectionList.clear()
            collectionList.addAll(queryChildAccountNFTCollectionID(WalletManager.selectedWalletAddress()))
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

    fun isNFTCollectionAccessible(id: String): Boolean {
        if (WalletManager.isChildAccountSelected().not()) {
            return true
        }
        return collectionList.firstOrNull { it.id == id } != null
    }

    fun isNFTAccessible(id: String): Boolean {
        if (WalletManager.isChildAccountSelected().not()) {
            return true
        }
        return collectionList.firstOrNull { it.idList.contains(id) } != null
    }
}

