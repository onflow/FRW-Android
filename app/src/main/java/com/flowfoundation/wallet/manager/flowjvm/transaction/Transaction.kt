package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.DomainTag
import com.nftco.flow.sdk.FlowAccountKey
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.FlowArgument
import com.nftco.flow.sdk.FlowId
import com.nftco.flow.sdk.FlowSignature
import com.nftco.flow.sdk.FlowTransaction
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.flowTransaction
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.flowjvm.FlowApi
import com.flowfoundation.wallet.manager.flowjvm.toAsArgument
import com.flowfoundation.wallet.manager.flowjvm.valueString
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.functions.FUNCTION_SIGN_AS_PAYER
import com.flowfoundation.wallet.network.functions.executeHttpFunction
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.vibrateTransaction
import com.flowfoundation.wallet.wallet.toAddress
import io.outblock.wallet.CryptoProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Security

private const val TAG = "FlowTransaction"

suspend fun sendTransaction(
    builder: TransactionBuilder.() -> Unit,
): String? {
//    updateSecurityProvider()
    val transactionBuilder = TransactionBuilder().apply { builder(this) }

    try {
        logd(TAG, "sendTransaction prepare")
        val voucher = prepare(transactionBuilder)

        logd(TAG, "sendTransaction build flow transaction")
        var tx = voucher.toFlowTransaction()

        if (tx.envelopeSignatures.isEmpty() && isGasFree()) {
            logd(TAG, "sendTransaction request free gas envelope")
            tx = tx.addFreeGasEnvelope()
        } else if (tx.envelopeSignatures.isEmpty()) {
            logd(TAG, "sendTransaction sign envelope")
            tx = tx.addLocalSignatures()
        }

        logd(TAG, "sendTransaction to flow chain")
        val txID = FlowApi.get().sendTransaction(tx).bytes.bytesToHex()
        logd(TAG, "transaction id:$${txID}")
        vibrateTransaction()
        MixpanelManager.cadenceTransactionSigned(
            cadence = voucher.cadence.orEmpty(), txId = txID, authorizers = tx.authorizers.map { it.formatted }.toList(),
            proposer = tx.proposalKey.address.formatted,
            payer = tx.payerAddress.formatted,
            isSuccess = true
        )
        return txID
    } catch (e: Exception) {
        loge(e)
        MixpanelManager.cadenceTransactionSigned(
            cadence = transactionBuilder.script.orEmpty(), txId = "",
            authorizers = emptyList(),
            proposer = transactionBuilder.walletAddress?.toAddress().orEmpty(),
            payer = transactionBuilder.payer ?: (if (isGasFree()) AppConfig.payer().address else transactionBuilder.walletAddress).orEmpty(),
            isSuccess = false
        )
        return null
    }
}

suspend fun sendTransactionWithMultiSignature(
    builder: TransactionBuilder.() -> Unit,
    providers: List<CryptoProvider>
): String {
    updateSecurityProvider()
    logd(TAG, "sendTransaction prepare")
    val transBuilder = TransactionBuilder().apply { builder(this) }
    val account = FlowApi.get().getAccountAtLatestBlock(FlowAddress(transBuilder.walletAddress?.toAddress().orEmpty())) ?: throw RuntimeException("get wallet account error")
    val restoreProposalKey = account.keys.first { providers.first().getPublicKey() == it.publicKey.base16Value }
    val voucher = prepareWithMultiSignature(
        walletAddress = account.address.base16Value,
        restoreProposalKey = restoreProposalKey,
        builder = transBuilder,
    )

    logd(TAG, "sendTransaction build flow transaction")
    var tx = voucher.toFlowMultiTransaction()

    providers.forEach { cryptoProvider ->
        tx = tx.addPayloadSignature(
            tx.proposalKey.address,
            keyIndex = account.keys.first { cryptoProvider.getPublicKey() == it.publicKey.base16Value }.id,
            cryptoProvider.getSigner()
        )
    }

    if (tx.envelopeSignatures.isEmpty() && isGasFree()) {
        logd(TAG, "sendTransaction request free gas envelope")
        tx = tx.addFreeGasEnvelope()
    }


    logd(TAG, "sendTransaction to flow chain")
    val txID = FlowApi.get().sendTransaction(tx)
    logd(TAG, "transaction id:$${txID.bytes.bytesToHex()}")
    vibrateTransaction()
    return txID.bytes.bytesToHex()
}

private fun FlowTransaction.addLocalSignatures(): FlowTransaction {
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: throw Exception("Crypto Provider is null")
    try {
        // if user pay the gas, payer.address == proposal.address, payer.keyIndex == proposalKeyIndex
        return copy(payloadSignatures = emptyList()).addEnvelopeSignature(
            payerAddress,
            keyIndex = proposalKey.keyIndex,
            cryptoProvider.getSigner()
        )
    } catch (e: Exception) {
        loge(e)
        throw e
    }
}

private suspend fun FlowTransaction.addFreeGasEnvelope(): FlowTransaction {
    val response = executeHttpFunction(FUNCTION_SIGN_AS_PAYER, buildPayerSignable())
    logd(TAG, "response:$response")

    val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs

    return addEnvelopeSignature(
        FlowAddress(sign.address),
        keyIndex = sign.keyId,
        signature = FlowSignature(sign.sig)
    )
}

private suspend fun prepare(builder: TransactionBuilder): Voucher {
    logd(TAG, "prepare builder:$builder")
    val account = FlowApi.get().getAccountAtLatestBlock(FlowAddress(builder.walletAddress?.toAddress().orEmpty()))
        ?: throw RuntimeException("get wallet account error")
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("get account error")
    val currentKey = account.keys.findLast { it.publicKey.base16Value == cryptoProvider.getPublicKey() }
        ?: throw RuntimeException("get account key error")

    return Voucher(
        arguments = builder.arguments.map { AsArgument(it.type, it.valueString()) },
        cadence = builder.script,
        computeLimit = builder.limit ?: 9999,
        payer = builder.payer ?: (if (isGasFree()) AppConfig.payer().address else builder.walletAddress),
        proposalKey = ProposalKey(
            address = account.address.base16Value,
            keyId = currentKey.id,
            sequenceNum = currentKey.sequenceNumber,
        ),
        refBlock = FlowApi.get().getLatestBlockHeader().id.base16Value,
    )
}

private suspend fun prepareWithMultiSignature(
    walletAddress: String,
    restoreProposalKey: FlowAccountKey,
    builder: TransactionBuilder
): Voucher {
    logd(TAG, "prepare builder:$builder")

    return Voucher(
        arguments = builder.arguments.map { AsArgument(it.type, it.valueString()) },
        cadence = builder.script,
        computeLimit = builder.limit ?: 9999,
        payer = builder.payer
            ?: (if (isGasFree()) AppConfig.payer().address else builder.walletAddress),
        proposalKey = ProposalKey(
            address = walletAddress,
            keyId = restoreProposalKey.id,
            sequenceNum = restoreProposalKey.sequenceNumber,
        ),
        refBlock = FlowApi.get().getLatestBlockHeader().id.base16Value,
    )
}

fun FlowTransaction.buildPayerSignable(): PayerSignable? {
    val payerAccount = FlowApi.get().getAccountAtLatestBlock(payerAddress) ?: return null
    val voucher = Voucher(
        cadence = script.stringValue,
        refBlock = referenceBlockId.base16Value,
        computeLimit = gasLimit.toInt(),
        arguments = arguments.map { it.toAsArgument() },
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
        envelopeSigs = listOf(
            Singature(
                address = AppConfig.payer().address.toAddress(),
                keyId = payerAccount.keys.first().id,
            )
        ),
    )

    return PayerSignable(
        transaction = voucher,
        message = PayerSignable.Message(
            (DomainTag.TRANSACTION_DOMAIN_TAG + canonicalAuthorizationEnvelope).bytesToHex()
        )
    )
}

fun FlowTransaction.encodeTransactionPayload(): String {
    return (DomainTag.TRANSACTION_DOMAIN_TAG + canonicalPayload).bytesToHex()
}

fun Voucher.toFlowMultiTransaction(): FlowTransaction {
    val transaction = this
    return flowTransaction {
        script { transaction.cadence.orEmpty() }

        arguments = transaction.arguments.orEmpty().map { it.toBytes() }.map { FlowArgument(it) }.toMutableList()

        referenceBlockId = FlowId(transaction.refBlock.orEmpty())

        gasLimit = computeLimit ?: 9999

        proposalKey {
            address = FlowAddress(transaction.proposalKey.address.orEmpty())
            keyIndex = transaction.proposalKey.keyId ?: 0
            sequenceNumber = transaction.proposalKey.sequenceNum ?: 0
        }

        if (transaction.authorizers.isNullOrEmpty()) {
            authorizers(mutableListOf(FlowAddress(transaction.proposalKey.address.orEmpty())))
        } else {
            authorizers(transaction.authorizers.map { FlowAddress(it) }.toMutableList())
        }

        payerAddress = FlowAddress(transaction.payer.orEmpty())
    }
}

fun Voucher.toFlowTransaction(): FlowTransaction {
    val transaction = this
    var tx = flowTransaction {
        script { transaction.cadence.orEmpty() }

        arguments = transaction.arguments.orEmpty().map { it.toBytes() }.map { FlowArgument(it) }.toMutableList()

        referenceBlockId = FlowId(transaction.refBlock.orEmpty())

        gasLimit = computeLimit ?: 9999

        proposalKey {
            address = FlowAddress(transaction.proposalKey.address.orEmpty())
            keyIndex = transaction.proposalKey.keyId ?: 0
            sequenceNumber = transaction.proposalKey.sequenceNum ?: 0
        }

        if (transaction.authorizers.isNullOrEmpty()) {
            authorizers(mutableListOf(FlowAddress(transaction.proposalKey.address.orEmpty())))
        } else {
            authorizers(transaction.authorizers.map { FlowAddress(it) }.toMutableList())
        }

        payerAddress = FlowAddress(transaction.payer.orEmpty())

        addPayloadSignatures {
            payloadSigs?.forEach { sig ->
                if (!sig.sig.isNullOrBlank()) {
                    signature(
                        FlowAddress(sig.address),
                        sig.keyId ?: 0,
                        FlowSignature(sig.sig)
                    )
                }
            }
        }

        addEnvelopeSignatures {
            envelopeSigs?.forEach { sig ->
                if (!sig.sig.isNullOrBlank()) {
                    signature(
                        FlowAddress(sig.address),
                        sig.keyId ?: 0,
                        FlowSignature(sig.sig)
                    )
                }
            }
        }
    }

    if (tx.payloadSignatures.isEmpty()) {
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return tx

        tx = tx.addPayloadSignature(
            FlowAddress(proposalKey.address.orEmpty()),
            keyIndex = proposalKey.keyId ?: 0,
            cryptoProvider.getSigner(),
        )
    }

    return tx
}

/**
 * fix: java.security.NoSuchAlgorithmException: no such algorithm: ECDSA for provider BC
 */
fun updateSecurityProvider() {
    // Web3j will set up the provider lazily when it's first used.
    val provider: Provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) ?: return
    if (provider.javaClass == BouncyCastleProvider::class.java) {
        // BC with same package name, shouldn't happen in real life.
        return
    }
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
    Security.insertProviderAt(BouncyCastleProvider(), 1)
}

/**
 * fix: java.security.NoSuchProviderException: no such provider: BC
 */
fun checkSecurityProvider() {
    val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
    if (provider == null) {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}

private fun AsArgument.toBytes(): ByteArray {
    return if (isObjectValue()) {
        """{"type":"$type","value":${value}}""".toByteArray()
    } else Gson().toJson(mapOf("type" to type, "value" to "$value")).toByteArray()
}

private fun AsArgument.isObjectValue(): Boolean {
    // is map or list
    return runCatching {
        Gson().fromJson<Map<String, Any>>(
            value.toString(),
            object : TypeToken<Map<String, Any>>() {}.type
        )
    }.getOrNull() != null || runCatching {
        Gson().fromJson<List<Any>>(value.toString(), object : TypeToken<List<Any>>() {}.type)
    }.getOrNull() != null
}
