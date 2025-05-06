package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.flowjvm.toAsArgument
import com.flowfoundation.wallet.manager.flowjvm.valueString
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.BASE_HOST
import com.flowfoundation.wallet.network.functions.FUNCTION_SIGN_AS_BRIDGE_PAYER
import com.flowfoundation.wallet.network.functions.FUNCTION_SIGN_AS_PAYER
import com.flowfoundation.wallet.network.functions.executeHttpFunction
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.InvalidKeyException
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.reportCadenceErrorToDebugView
import com.flowfoundation.wallet.utils.vibrateTransaction
import com.flowfoundation.wallet.wallet.toAddress
import com.instabug.library.Instabug
import com.flow.wallet.CryptoProvider
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.onflow.flow.models.*
import java.security.Provider
import java.security.Security
import com.ionspin.kotlin.bignum.integer.BigInteger
import org.onflow.flow.infrastructure.getTypeName

private const val TAG = "Transaction"

suspend fun sendTransaction(
    builder: TransactionBuilder.() -> Unit,
): String? {
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
        val txID = FlowCadenceApi.sendTransaction(tx).id
        logd(TAG, "transaction id:$${txID}")
        vibrateTransaction()
        if (txID != null) {
            MixpanelManager.cadenceTransactionSigned(
                cadence = voucher.cadence.orEmpty(),
                txId = txID,
                authorizers = tx.authorizers.map { it },
                proposer = tx.proposalKey.address,
                payer = tx.payer,
                isSuccess = true
            )
        }
        return txID
    } catch (e: Exception) {
        loge(e)
        MixpanelManager.cadenceTransactionSigned(
            cadence = transactionBuilder.script.orEmpty(), 
            txId = "",
            authorizers = emptyList(),
            proposer = transactionBuilder.walletAddress?.toAddress().orEmpty(),
            payer = transactionBuilder.payer ?: (if (isGasFree()) AppConfig.payer().address else transactionBuilder.walletAddress).orEmpty(),
            isSuccess = false
        )
        transactionBuilder.scriptId?.let {
            reportCadenceErrorToDebugView(it, e)
        }
        if (e is InvalidKeyException) {
            ErrorReporter.reportCriticalWithMixpanel(WalletError.QUERY_ACCOUNT_KEY_FAILED, e)
            Instabug.show()
        }
        return null
    }
}

suspend fun sendBridgeTransaction(
    builder: TransactionBuilder.() -> Unit,
): String? {
    val transactionBuilder = TransactionBuilder().apply { builder(this) }

    try {
        logd(TAG, "sendBridgeTransaction prepare")
        val voucher = prepare(transactionBuilder)

        logd(TAG, "sendBridgeTransaction build flow transaction")
        var tx = voucher.toFlowTransactionWithBridgePayer()
        tx = tx.addFreeBridgeFeeEnvelope()

        logd(TAG, "sendBridgeTransaction to flow chain")
        val txID = FlowCadenceApi.sendTransaction(tx).id
        logd(TAG, "transaction id:$${txID}")
        vibrateTransaction()
        if (txID != null) {
            MixpanelManager.cadenceTransactionSigned(
                cadence = voucher.cadence.orEmpty(),
                txId = txID,
                authorizers = tx.authorizers.map { it },
                proposer = tx.proposalKey.address,
                payer = tx.payer,
                isSuccess = true
            )
        }
        return txID
    } catch (e: Exception) {
        loge(e)
        MixpanelManager.cadenceTransactionSigned(
            cadence = transactionBuilder.script.orEmpty(), 
            txId = "",
            authorizers = emptyList(),
            proposer = transactionBuilder.walletAddress?.toAddress().orEmpty(),
            payer = transactionBuilder.payer ?: (if (isGasFree()) AppConfig.payer().address else transactionBuilder.walletAddress).orEmpty(),
            isSuccess = false
        )
        transactionBuilder.scriptId?.let {
            reportCadenceErrorToDebugView(it, e)
        }
        if (e is InvalidKeyException) {
            ErrorReporter.reportCriticalWithMixpanel(WalletError.QUERY_ACCOUNT_KEY_FAILED, e)
            Instabug.show()
        }
        return null
    }
}

suspend fun sendTransactionWithMultiSignature(
    builder: TransactionBuilder.() -> Unit,
    providers: List<CryptoProvider>
): String {
    logd(TAG, "sendTransaction prepare")
    val transBuilder = TransactionBuilder().apply { builder(this) }
    val account = FlowCadenceApi.getAccount(transBuilder.walletAddress?.toAddress().orEmpty())
    val accountKeys = account.keys ?: throw InvalidKeyException("Account has no keys")
    val restoreProposalKey = accountKeys.firstOrNull { providers.first().getPublicKey() == it.publicKey } 
        ?: throw InvalidKeyException("get account key error")
    val voucher = prepareWithMultiSignature(
        walletAddress = account.address,
        restoreProposalKey = restoreProposalKey,
        builder = transBuilder,
    )

    logd(TAG, "sendTransaction build flow transaction")
    var tx = voucher.toFlowMultiTransaction()

    providers.forEach { cryptoProvider ->
        tx = tx.addPayloadSignature(
            tx.proposalKey.address,
            keyIndex = accountKeys.first { cryptoProvider.getPublicKey() == it.publicKey }.index,
            cryptoProvider.getSigner()
        )
    }

    if (tx.envelopeSignatures.isEmpty() && isGasFree()) {
        logd(TAG, "sendTransaction request free gas envelope")
        tx = tx.addFreeGasEnvelope()
    }

    logd(TAG, "sendTransaction to flow chain")
    val txID = FlowCadenceApi.sendTransaction(tx).id
    logd(TAG, "transaction id:$${txID}")
    vibrateTransaction()
    return txID
}

private fun Transaction.addLocalSignatures(): Transaction {
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: throw Exception("Crypto Provider is null")
    try {
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

    return addEnvelopeSignature(
        FlowAddress(sign.address),
        keyIndex = sign.keyId,
        signature = sign.sig
    )
}

private suspend fun Transaction.addFreeBridgeFeeEnvelope(): Transaction {
    val response = executeHttpFunction(FUNCTION_SIGN_AS_BRIDGE_PAYER, buildBridgeFeePayerSignable(), BASE_HOST)
    logd(TAG, "response:$response")

    val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs

    return addEnvelopeSignature(
        FlowAddress(sign.address),
        keyIndex = sign.keyId,
        signature = sign.sig
    )
}

private suspend fun prepare(builder: TransactionBuilder): Voucher {
    logd(TAG, "prepare builder:$builder")
    val account = FlowCadenceApi.getAccount(builder.walletAddress?.toAddress().orEmpty())
        ?: throw RuntimeException("get wallet account error")
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("get account error")
    val accountKeys = account.keys ?: throw InvalidKeyException("Account has no keys")
    val currentKey = accountKeys.findLast { it.publicKey == cryptoProvider.getPublicKey() }
        ?: throw InvalidKeyException("get account key error")

    return Voucher(
        arguments = builder.arguments.map { AsArgument(it.type, it.valueString()) },
        cadence = builder.script,
        computeLimit = builder.limit ?: 9999,
        payer = builder.payer ?: (if (isGasFree()) AppConfig.payer().address else builder.walletAddress),
        proposalKey = ProposalKey(
            address = account.address,
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
        arguments = builder.arguments.map { AsArgument(it.type, it.valueString()) },
        cadence = builder.script,
        computeLimit = builder.limit ?: 9999,
        payer = builder.payer ?: (if (isGasFree()) AppConfig.payer().address else builder.walletAddress),
        proposalKey = ProposalKey(
            address = walletAddress,
            keyId = restoreProposalKey.index.toInt(),
            sequenceNum = restoreProposalKey.sequenceNumber.toInt(),
        ),
        refBlock = FlowCadenceApi.getBlockHeader(null).id,
    )
}

suspend fun Transaction.buildBridgeFeePayerSignable(): PayerSignable? {
    val payerAccount = FlowCadenceApi.getAccount(payer) ?: return null
    val accountKeys = payerAccount.keys ?: return null
    val voucher = Voucher(
        cadence = script,
        refBlock = referenceBlockId,
        computeLimit = gasLimit.intValue(),
        arguments = arguments.map {
            AsArgument(
                type = it.getTypeName(),
                value = it.value ?: "" // to-do : is empty string ok here?
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
            Singature(
                address = it.address,
                keyId = it.keyIndex,
                sig = it.signature,
            )
        },
        envelopeSigs = listOf(
            Singature(
                address = AppConfig.bridgeFeePayer().address,
                keyId = accountKeys.firstOrNull()?.index?.toInt() ?: 0,
            )
        ),
    )

    return PayerSignable(
        transaction = voucher,
        message = PayerSignable.Message(
            (DomainTag.Transaction + canonicalAuthorizationEnvelope).bytesToHex()
        )
    )
}

suspend fun Transaction.buildPayerSignable(): PayerSignable? {
    val payerAccount = FlowCadenceApi.getAccount(payer) ?: return null
    val accountKeys = payerAccount.keys ?: return null
    val voucher = Voucher(
        cadence = script,
        refBlock = referenceBlockId,
        computeLimit = gasLimit.intValue(),
        arguments = arguments.map { it },
        proposalKey = ProposalKey(
            address = proposalKey.address,
            keyId = proposalKey.keyIndex,
            sequenceNum = proposalKey.sequenceNumber.intValue(),
        ),
        payer = payer,
        authorizers = authorizers.map { it },
        payloadSigs = payloadSignatures.map {
            Singature(
                address = it.address,
                keyId = it.keyIndex,
                sig = it.signature,
            )
        },
        envelopeSigs = listOf(
            Singature(
                address = AppConfig.payer().address,
                keyId = accountKeys.firstOrNull()?.index?.toInt() ?: 0,
            )
        ),
    )

    return PayerSignable(
        transaction = voucher,
        message = PayerSignable.Message(
            (DomainTag.Transaction + canonicalAuthorizationEnvelope).bytesToHex()
        )
    )
}

fun Transaction.encodeTransactionPayload(): String {
    return (DomainTag.Transaction + canonicalPayload).bytesToHex()
}

fun Voucher.toFlowMultiTransaction(): Transaction {
    val transaction = this
    return Transaction(
        script = transaction.cadence.orEmpty(),
        arguments = transaction.arguments.map { it },
        referenceBlockId = transaction.refBlock.orEmpty(),
        gasLimit = BigInteger.fromLong(transaction.computeLimit?.toLong() ?: 9999L),
        proposalKey = ProposalKey(
            address = transaction.proposalKey.address.orEmpty(),
            keyIndex = transaction.proposalKey.keyId ?: 0,
            sequenceNumber = BigInteger.fromInt(this.proposalKey.sequenceNum ?: 0)
        ),
        authorizers = if (transaction.authorizers.isNullOrEmpty()) {
            listOf(transaction.proposalKey.address.orEmpty())
        } else {
            transaction.authorizers
        },
        payer = transaction.payer.orEmpty()
    )
}

fun Voucher.toFlowTransaction(): Transaction {
    val transaction = this
    return Transaction(
        script = transaction.cadence.orEmpty(),
        arguments = transaction.arguments.orEmpty().map { it.toBytes() },
        referenceBlockId = transaction.refBlock.orEmpty(),
        gasLimit = BigInteger.fromLong(transaction.computeLimit?.toLong() ?: 9999L),
        proposalKey = org.onflow.flow.models.ProposalKey(
            address = transaction.proposalKey.address.orEmpty(),
            keyIndex = transaction.proposalKey.keyId ?: 0,
            sequenceNumber = transaction.proposalKey.sequenceNum ?: 0
        ),
        authorizers = if (transaction.authorizers.isNullOrEmpty()) {
            listOf(transaction.proposalKey.address.orEmpty())
        } else {
            transaction.authorizers
        },
        payer = transaction.payer.orEmpty(),
        payloadSignatures = transaction.payloadSigs?.map {
            TransactionSignature(
                address = it.address,
                keyIndex = it.keyId ?: 0,
                signature = it.sig.orEmpty()
            )
        } ?: emptyList(),
        envelopeSignatures = transaction.envelopeSigs?.map {
            TransactionSignature(
                address = it.address,
                keyIndex = it.keyId ?: 0,
                signature = it.sig.orEmpty()
            )
        } ?: emptyList()
    )
}

fun Voucher.toFlowTransactionWithBridgePayer(): Transaction {
    val transaction = this
    return Transaction(
        script = transaction.cadence.orEmpty(),
        arguments = transaction.arguments.orEmpty().map { it.toBytes() },
        referenceBlockId = transaction.refBlock.orEmpty(),
        gasLimit = BigInteger.fromLong(transaction.computeLimit?.toLong() ?: 9999L),
        proposalKey = org.onflow.flow.models.ProposalKey(
            address = transaction.proposalKey.address.orEmpty(),
            keyIndex = transaction.proposalKey.keyId ?: 0,
            sequenceNumber = transaction.proposalKey.sequenceNum ?: 0
        ),
        authorizers = listOf(
            transaction.proposalKey.address.orEmpty(),
            transaction.payer.orEmpty()
        ),
        payer = transaction.payer.orEmpty()
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
