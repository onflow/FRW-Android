package com.flowfoundation.wallet.page.profile.subpage.claimdomain

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.FlowArgument
import com.nftco.flow.sdk.FlowTransaction
import com.nftco.flow.sdk.FlowTransactionStatus
import com.nftco.flow.sdk.cadence.TYPE_STRING
import com.nftco.flow.sdk.flowTransaction
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.flowjvm.FlowApi
import com.flowfoundation.wallet.manager.flowjvm.transaction.AsArgument
import com.flowfoundation.wallet.manager.flowjvm.transaction.PayerSignable
import com.flowfoundation.wallet.manager.flowjvm.transaction.ProposalKey
import com.flowfoundation.wallet.manager.flowjvm.transaction.Singature
import com.flowfoundation.wallet.manager.flowjvm.transaction.Voucher
import com.flowfoundation.wallet.manager.flowjvm.transaction.encodeTransactionPayload
import com.flowfoundation.wallet.manager.flowjvm.transaction.updateSecurityProvider
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.ClaimDomainPrepare
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.toAddress

class ClaimDomainViewModel : ViewModel() {

    val usernameLiveData = MutableLiveData<String>()
    val claimTransactionIdLiveData = MutableLiveData<String?>()

    fun load() {
        viewModelIOScope(this) {
            AccountManager.userInfo()?.username?.let { usernameLiveData.postValue(it) }
        }
    }

    fun claim() {
        viewModelIOScope(this) {
            try {
                assert(usernameLiveData.value != null) { "username is null" }
                val prepare = retrofit().create(ApiService::class.java).claimDomainPrepare().data!!
                val signable = buildPayerSignable(prepare)
                val transaction = retrofit().create(ApiService::class.java).claimDomainSignature(signable).data!!
                watchTransactionState(transaction.txId!!)
                claimTransactionIdLiveData.postValue(transaction.txId)
            } catch (e: Exception) {
                e.printStackTrace()
                claimTransactionIdLiveData.postValue(null)
            }
        }
    }

    private fun buildPayerSignable(prepare: ClaimDomainPrepare): PayerSignable {
        updateSecurityProvider()
        val walletAddress = WalletManager.selectedWalletAddress().toAddress()
        val account = FlowApi.get().getAccountAtLatestBlock(FlowAddress(walletAddress))
            ?: throw RuntimeException("get wallet account error")
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
            ?: throw RuntimeException("get account error")
        val currentKey =
            account.keys.findLast { it.publicKey.base16Value == cryptoProvider.getPublicKey() }
                ?: throw RuntimeException("get account key error")
        return flowTransaction {
            script { prepare.cadence!! }

            val jsonObject = JsonObject()
            jsonObject.addProperty("type", TYPE_STRING)
            jsonObject.addProperty("value", usernameLiveData.value!!)
            arguments = mutableListOf(FlowArgument(jsonObject.toString().toByteArray()))

            referenceBlockId = FlowApi.get().getLatestBlockHeader().id

            gasLimit = 9999

            proposalKey {
                address = FlowAddress(walletAddress)
                keyIndex = currentKey.id
                sequenceNumber = currentKey.sequenceNumber
            }

            authorizers(listOf(walletAddress, prepare.lilicoServerAddress!!, prepare.flownsServerAddress!!).map { FlowAddress(it) }.toMutableList())

            payerAddress = FlowAddress(AppConfig.payer().address)

            addPayloadSignatures {
                signature(
                    FlowAddress(walletAddress),
                    currentKey.id,
                    cryptoProvider.getSigner(),
                )
            }
        }.buildPayerSignable()
    }

    private fun FlowTransaction.buildPayerSignable(): PayerSignable {
        val voucher = Voucher(
            cadence = script.stringValue,
            refBlock = referenceBlockId.base16Value,
            computeLimit = gasLimit.toInt(),
            arguments = arguments.map { it.jsonCadence }.map { AsArgument(it.type, it.value.toString()) },
            proposalKey = ProposalKey(
                address = proposalKey.address.base16Value.toAddress(),
                keyId = proposalKey.keyIndex,
                sequenceNum = proposalKey.sequenceNumber.toInt(),
            ),
            payer = payerAddress.base16Value.toAddress(),
            authorizers = authorizers.map { it.base16Value.toAddress() },
            payloadSigs = payloadSignatures.map {
                Singature(
                    address = it.address.base16Value.toAddress(),
                    keyId = it.keyIndex,
                    sig = it.signature.base16Value,
                )
            },
        )

        return PayerSignable(
            transaction = voucher,
            message = PayerSignable.Message(encodeTransactionPayload())
        )
    }

    private fun watchTransactionState(txId: String) {
        val transactionState = TransactionState(
            transactionId = txId,
            time = System.currentTimeMillis(),
            state = FlowTransactionStatus.UNKNOWN.num,
            type = TransactionState.TYPE_CLAIM_DOMAIN,
            data = "",
        )
        uiScope {
            if (TransactionStateManager.getTransactionStateById(txId) != null) return@uiScope
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
        }
    }
}