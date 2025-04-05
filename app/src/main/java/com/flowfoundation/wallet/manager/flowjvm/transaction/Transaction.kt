package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.onflow.flow.models.DomainTag
import com.nftco.flow.sdk.bytesToHex
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.functions.FUNCTION_SIGN_AS_PAYER
import com.flowfoundation.wallet.network.functions.executeHttpFunction
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.vibrateTransaction
import com.flowfoundation.wallet.wallet.toAddress
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.outblock.wallet.CryptoProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.onflow.flow.infrastructure.Cadence
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.Transaction
import org.onflow.flow.models.TransactionSignature
import java.security.Provider
import java.security.Security
import java.util.Base64

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
        val txID = FlowCadenceApi.sendTransaction(tx).id ?: throw Exception("TX ID not found")
        logd(TAG, "transaction id:$${txID}")
        vibrateTransaction()
        MixpanelManager.cadenceTransactionSigned(
            cadence = voucher.cadence.orEmpty(), txId = txID, authorizers = tx.authorizers.map { it }.toList(),
            proposer = tx.proposalKey.address,
            payer = tx.payer,
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
    val account = FlowCadenceApi.getAccount(transBuilder.walletAddress?.toAddress().orEmpty())
    val restoreProposalKey = account.keys?.first { providers.first().getPublicKey() == it.publicKey }
        ?: throw IllegalArgumentException("No matching restoreProposalKey found")
    val voucher = prepareWithMultiSignature(
        walletAddress = FlowAddress(account.address).base16Value,
        restoreProposalKey = restoreProposalKey,
        builder = transBuilder,
    )

    logd(TAG, "sendTransaction build flow transaction")
    var tx = voucher.toFlowMultiTransaction()

    providers.forEach { cryptoProvider ->
        tx = tx.addPayloadSignature(
            tx.proposalKey.address,
            keyIndex = account.keys?.first { cryptoProvider.getPublicKey() == it.publicKey }?.index,
            cryptoProvider.getSigner()
        )
    }

    if (tx.envelopeSignatures.isEmpty() && isGasFree()) {
        logd(TAG, "sendTransaction request free gas envelope")
        tx = tx.addFreeGasEnvelope()
    }


    logd(TAG, "sendTransaction to flow chain")
    val txID = FlowCadenceApi.sendTransaction(tx).id ?: throw Exception("TX ID not found")
    logd(TAG, "transaction id:$${txID}")
    vibrateTransaction()
    return txID
}

private fun Transaction.addLocalSignatures(): Transaction {
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: throw Exception("Crypto Provider is null")
    try {
        // if user pay the gas, payer.address == proposal.address, payer.keyIndex == proposalKeyIndex
        return copy(payloadSignatures = emptyList()).addEnvelopeSignature(
            payer,
            keyIndex = proposalKey.keyIndex,
            cryptoProvider.getSigner()
        )
    } catch (e: Exception) {
        loge(e)
        throw e
    }
}

private suspend fun Transaction.addFreeGasEnvelope(): Transaction {
    val response = executeHttpFunction(FUNCTION_SIGN_AS_PAYER, buildPayerSignable())
    logd(TAG, "response:$response")

    val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs

    val signature = TransactionSignature(sign.address, sign.keyId, sign.sig)

    return addEnvelopeSignature(
        signature = signature
    )
}

private suspend fun prepare(builder: TransactionBuilder): Voucher {
    logd(TAG, "prepare builder:$builder")
    val account = FlowCadenceApi.getAccount(builder.walletAddress?.toAddress().orEmpty())
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("get account error")
    val currentKey = account.keys?.findLast { it.publicKey == cryptoProvider.getPublicKey() }
        ?: throw RuntimeException("get account key error")

    return Voucher(
        arguments = builder.arguments.map {
            AsArgument(
                type = it.type,
                value = it.value ?: ""
            )
        },
        cadence = builder.script,
        computeLimit = builder.limit ?: 9999,
        payer = builder.payer ?: (if (isGasFree()) AppConfig.payer().address else builder.walletAddress),
        proposalKey = ProposalKey(
            address = FlowAddress(account.address).base16Value,
            keyId = currentKey.index.toInt(),
            sequenceNum = currentKey.sequenceNumber.toInt(),
        ),
        refBlock = FlowCadenceApi.getBlockHeader(null).id,
    )
}

private suspend fun prepareWithMultiSignature(
    walletAddress: String,
    restoreProposalKey: AccountPublicKey,
    builder: TransactionBuilder
): Voucher {
    logd(TAG, "prepare builder:$builder")

    return Voucher(
        arguments = builder.arguments.map {
            AsArgument(
                type = it.type,
                value = it.value ?: ""
            )
        },
        cadence = builder.script,
        computeLimit = builder.limit ?: 9999,
        payer = builder.payer
            ?: (if (isGasFree()) AppConfig.payer().address else builder.walletAddress),
        proposalKey = ProposalKey(
            address = walletAddress,
            keyId = restoreProposalKey.index.toInt(),
            sequenceNum = restoreProposalKey.sequenceNumber.toInt(),
        ),
        refBlock = FlowCadenceApi.getBlockHeader(null).id,
    )
}

suspend fun Transaction.buildPayerSignable(): PayerSignable {
    val payerAccount = FlowCadenceApi.getAccount(payer)
    val voucher = Voucher(
        cadence = script,
        refBlock = referenceBlockId,
        computeLimit = gasLimit.intValue(),
        arguments = arguments.map {
            AsArgument(
                type = it.getTypeName(),
                value = it.value ?: ""
            )
        },
        proposalKey = ProposalKey(
            address = proposalKey.address,
            keyId = proposalKey.keyIndex,
            sequenceNum = proposalKey.sequenceNumber.intValue(),
        ),
        payer = payer,
        authorizers = authorizers.map { it },
        payloadSigs = payloadSignatures.map {
            Signature(
                address = it.address,
                keyId = it.keyIndex,
                sig = it.signature // check signature return format
            )
        },
        envelopeSigs = listOf(
            Signature(
                address = AppConfig.payer().address.toAddress(),
                keyId = payerAccount.keys?.first()?.index?.toInt(),
            )
        ),
    )

    return PayerSignable(
        transaction = voucher,
        message = PayerSignable.Message(
            (DomainTag.Transaction.bytes + canonicalAuthorizationEnvelope).bytesToHex()
        )
    )
}

fun Transaction.encodeTransactionPayload(): String {
    return (DomainTag.Transaction.bytes + canonicalPayload).bytesToHex()
}

fun Voucher.toFlowMultiTransaction(): Transaction {
    return Transaction(
        // The script field should be Base64 encoded.
        script = if (!this.cadence.isNullOrEmpty())
            Base64.getEncoder().encodeToString(this.cadence.toByteArray())
        else "",
        arguments = this.arguments?.map { it.toCadenceValue() } ?: emptyList(),
        referenceBlockId = this.refBlock.orEmpty(),
        // Convert the compute limit to a BigInteger, defaulting to 9999.
        gasLimit = this.computeLimit?.let { BigInteger.fromLong(it.toLong()) }
            ?: BigInteger.fromInt(9999),
        // Use the voucher's payer address.
        payer = this.payer.orEmpty(),
        // Build the proposal key from the voucher.
        proposalKey = org.onflow.flow.models.ProposalKey(
            address = this.proposalKey?.address.orEmpty(),
            keyIndex = this.proposalKey?.keyId ?: 0,
            sequenceNumber = BigInteger.fromInt(this.proposalKey.sequenceNum ?: 0)
        ),
        // Use the provided authorizers, or default to the proposal key address if none are provided.
        authorizers = if (this.authorizers.isNullOrEmpty()) {
            listOf(this.proposalKey?.address.orEmpty())
        } else {
            this.authorizers
        },
        // Multi-transaction may not have signatures initially.
        payloadSignatures = emptyList(),
        envelopeSignatures = emptyList(),
    )
}

fun Voucher.toFlowTransaction(): Transaction {
    return Transaction(
        // The script is expected to be Base64 encoded.
        script = if (!this.cadence.isNullOrEmpty())
            Base64.getEncoder().encodeToString(this.cadence.toByteArray())
        else "",
        arguments = this.arguments?.map { it.toCadenceValue() } ?: emptyList(),
        referenceBlockId = this.refBlock.orEmpty(),
        gasLimit = this.computeLimit?.let { BigInteger.fromLong(it.toLong()) }
            ?: BigInteger.fromInt(9999),
        payer = this.payer.orEmpty(),
        proposalKey = org.onflow.flow.models.ProposalKey(
            address = this.proposalKey.address.orEmpty(),
            keyIndex = this.proposalKey.keyId ?: 0,
            sequenceNumber = this.proposalKey.sequenceNum?.let { BigInteger.fromInt(it) } ?: throw Exception("No sequence number")
        ),
        authorizers = if (this.authorizers.isNullOrEmpty())
            listOf(this.proposalKey?.address.orEmpty())
        else
            this.authorizers,
        // Map payload signatures if available.
        payloadSignatures = this.payloadSigs.orEmpty().mapNotNull { sig ->
            if (!sig.sig.isNullOrBlank()) {
                TransactionSignature(
                    address = sig.address,
                    keyIndex = sig.keyId ?: 0,
                    signature = sig.sig,
                )
            } else null
        },
        // Map envelope signatures if available.
        envelopeSignatures = this.envelopeSigs.orEmpty().mapNotNull { sig ->
            if (!sig.sig.isNullOrBlank()) {
                TransactionSignature(
                    address = sig.address,
                    keyIndex = sig.keyId ?: 0,
                    signature = sig.sig,
                )
            } else null
        }
    )
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
