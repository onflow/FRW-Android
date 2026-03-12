package com.flowfoundation.wallet.network

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.network.model.ManualAddressRequest
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import org.onflow.flow.ChainId

private const val TAG = "AddAccountUtils"

/**
 * Add a new Flow address to the current account by calling POST /v2/user/manualaddress.
 * Waits for blockchain confirmation via fetchAccountByCreationTxId.
 *
 * @return the new formatted address (with 0x prefix), or null on failure
 */
suspend fun addNewFlowAccount(): String? {
    return try {
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        if (cryptoProvider == null) {
            loge(TAG, "Cannot get current crypto provider")
            return null
        }

        val publicKey = cryptoProvider.getPublicKey()
        val hashAlgorithm = cryptoProvider.getHashAlgorithm().cadenceIndex
        val signatureAlgorithm = cryptoProvider.getSignatureAlgorithm().cadenceIndex

        logd(TAG, "Adding new account with publicKey: $publicKey, hashAlgo: $hashAlgorithm, signAlgo: $signatureAlgorithm")

        val service = retrofit().create(ApiService::class.java)
        val request = ManualAddressRequest(
            hashAlgorithm = hashAlgorithm,
            publicKey = publicKey,
            signatureAlgorithm = signatureAlgorithm,
            weight = 1000
        )

        val response = service.createManualAddress(request)
        val txId = response.data?.txid
        if (txId.isNullOrBlank()) {
            loge(TAG, "No txId returned from createManualAddress")
            return null
        }

        logd(TAG, "Got txId: $txId, waiting for blockchain confirmation...")

        val chainId = when (chainNetWorkString()) {
            "mainnet" -> ChainId.Mainnet
            "testnet" -> ChainId.Testnet
            else -> ChainId.Mainnet
        }

        val walletForSDK = WalletManager.wallet()
        if (walletForSDK == null) {
            loge(TAG, "No wallet available from WalletManager")
            return null
        }

        logd(TAG, "Calling fetchAccountByCreationTxId with txId: $txId on chainId: $chainId")
        val fetchedAccount = walletForSDK.fetchAccountByCreationTxId(txId, chainId)
        val createdAddress = fetchedAccount.address

        logd(TAG, "New account fetched at address: $createdAddress")

        val formattedAddress = if (createdAddress.startsWith("0x")) createdAddress else "0x$createdAddress"
        val emojiInfo = AccountEmojiManager.getEmojiByAddress(formattedAddress)

        val flowWallet = FlowWallet(
            address = formattedAddress,
            name = emojiInfo.emojiName,
            emojiId = emojiInfo.emojiId,
            chainIdString = chainNetWorkString(),
            linkedWallets = emptyList()
        )

        AccountManager.updateCurrentAccount { account ->
            account.copy(walletNodes = account.walletNodes + flowWallet)
        }

        logd(TAG, "New FlowWallet added to account: $formattedAddress")
        formattedAddress
    } catch (e: Exception) {
        loge(TAG, "Failed to add new Flow account: ${e.message}")
        e.printStackTrace()
        null
    }
}
