package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.config.isGasFree
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
import com.ionspin.kotlin.bignum.integer.toBigInteger

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
        val signer = createSigner(
            address = tx.proposalKey.address,
            keyIndex = accountKeys.first { cryptoProvider.getPublicKey() == it.publicKey }.index.toInt(),
            signer = cryptoProvider.getSigner()
        )
        tx = tx.signPayload(listOf(signer))
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

suspend fun Transaction.addLocalSignatures(): Transaction {
    val account = FlowCadenceApi.getAccount(proposalKey.address)
        ?: throw RuntimeException("get wallet account error")
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("get account error")
    val accountKeys = account.keys ?: throw InvalidKeyException("Account has no keys")
    val currentKey = accountKeys.findLast { it.publicKey == cryptoProvider.getPublicKey() }
        ?: throw InvalidKeyException("get account key error")

    val signer = createSigner(
        address = proposalKey.address,
        keyIndex = currentKey.index.toInt(),
        signer = cryptoProvider.getSigner()
    )

    return signPayload(listOf(signer))
}

suspend fun Transaction.addLocalEnvelopeSignatures(): Transaction {
    val account = FlowCadenceApi.getAccount(payer)
        ?: throw RuntimeException("get wallet account error")
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("get account error")
    val accountKeys = account.keys ?: throw InvalidKeyException("Account has no keys")
    val currentKey = accountKeys.findLast { it.publicKey == cryptoProvider.getPublicKey() }
        ?: throw InvalidKeyException("get account key error")

    val signer = createSigner(
        address = payer,
        keyIndex = currentKey.index.toInt(),
        signer = cryptoProvider.getSigner()
    )

    return signEnvelope(listOf(signer))
}

suspend fun Transaction.sendTransactionWithMultiSignature(
    walletAddress: String,
    restoreProposalKey: AccountPublicKey,
    builder: TransactionBuilder
): TransactionResult {
    val transaction = prepareWithMultiSignature(walletAddress, restoreProposalKey, builder)
    val account = FlowCadenceApi.getAccount(walletAddress)
        ?: throw RuntimeException("get wallet account error")
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("get account error")
    val accountKeys = account.keys ?: throw InvalidKeyException("Account has no keys")
    val currentKey = accountKeys.findLast { it.publicKey == cryptoProvider.getPublicKey() }
        ?: throw InvalidKeyException("get account key error")

    val signer = createSigner(
        address = walletAddress,
        keyIndex = currentKey.index.toInt(),
        signer = cryptoProvider.getSigner()
    )

    val signedTransaction = transaction.signPayload(listOf(signer))

    return if (isGasFree()) {
        signedTransaction.addFreeGasEnvelope()
    } else {
        signedTransaction.addLocalEnvelopeSignatures()
    }.send()
}

suspend fun Transaction.send(): TransactionResult {
    return FlowCadenceApi.sendTransaction(this)
}

private fun createSigner(
    address: String,
    keyIndex: Int,
    signer: Signer
): Signer {
    return object : Signer {
        override var address: String = address
        override var keyIndex: Int = keyIndex
        override suspend fun sign(bytes: ByteArray): ByteArray = signer.sign(bytes)
    }
}

private fun Transaction.addFreeGasEnvelope(): Transaction {
    val response = executeHttpFunction(FUNCTION_SIGN_AS_PAYER, buildPayerSignable())
    logd(TAG, "response:$response")

    val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs

    return copy(
        envelopeSignatures = envelopeSignatures + TransactionSignature(
            address = sign.address,
            keyIndex = sign.keyId,
            signature = sign.sig
        )
    )
}

private suspend fun Transaction.addFreeBridgeFeeEnvelope(): Transaction {
    val response = executeHttpFunction(FUNCTION_SIGN_AS_BRIDGE_PAYER, buildBridgeFeePayerSignable(), BASE_HOST)
    logd(TAG, "response:$response")

    val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs

    return copy(
        envelopeSignatures = envelopeSignatures + TransactionSignature(
            address = sign.address,
            keyIndex = sign.keyId,
            signature = sign.sig
        )
    )
}

suspend fun prepare(builder: TransactionBuilder): Transaction {
    logd(TAG, "prepare builder:$builder")
    val account = FlowCadenceApi.getAccount(builder.walletAddress?.toAddress().orEmpty())
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("get account error")
    val accountKeys = account.keys ?: throw InvalidKeyException("Account has no keys")
    val currentKey = accountKeys.findLast { it.publicKey == cryptoProvider.getPublicKey() }
        ?: throw InvalidKeyException("get account key error")

    val payer = builder.payer ?: (if (isGasFree()) AppConfig.payer().address else builder.walletAddress).orEmpty()
    val authorizers = if (builder.isBridgePayer) {
        // For bridge payer transactions, we need both the proposer and payer as authorizers
        listOf(account.address, payer)
    } else if (builder.authorizers.isNullOrEmpty()) {
        listOf(account.address)
    } else {
        builder.authorizers
    }

    return Transaction(
        script = builder.script.orEmpty(),
        arguments = builder.arguments.map { it.toBytes() },
        referenceBlockId = FlowCadenceApi.getBlockHeader(null).id,
        gasLimit = BigInteger.fromLong(builder.limit?.toLong() ?: 9999L),
        payer = payer,
        proposalKey = ProposalKey(
            address = account.address,
            keyIndex = currentKey.index.toInt(),
            sequenceNumber = BigInteger.fromInt(currentKey.sequenceNumber.toInt())
        ),
        authorizers = authorizers
    )
}

suspend fun prepareWithMultiSignature(
    walletAddress: String,
    restoreProposalKey: AccountPublicKey,
    builder: TransactionBuilder
): Transaction {
    logd(TAG, "prepare builder:$builder")

    val payer = builder.payer ?: (if (isGasFree()) AppConfig.payer().address else builder.walletAddress).orEmpty()
    val authorizers = if (builder.isBridgePayer) {
        // For bridge payer transactions, we need both the proposer and payer as authorizers
        listOf(walletAddress, payer)
    } else if (builder.authorizers.isNullOrEmpty()) {
        listOf(walletAddress)
    } else {
        builder.authorizers
    }

    return Transaction(
        script = builder.script.orEmpty(),
        arguments = builder.arguments.map { it.toBytes() },
        referenceBlockId = FlowCadenceApi.getBlockHeader(null).id,
        gasLimit = BigInteger.fromLong(builder.limit?.toLong() ?: 9999L),
        payer = payer,
        proposalKey = ProposalKey(
            address = walletAddress,
            keyIndex = restoreProposalKey.index.toInt(),
            sequenceNumber = BigInteger.fromInt(restoreProposalKey.sequenceNumber.toInt())
        ),
        authorizers = authorizers
    )
}

suspend fun Transaction.buildBridgeFeePayerSignable(): PayerSignable? {
    val payerAccount = FlowCadenceApi.getAccount(payer) ?: return null
    val accountKeys = payerAccount.keys ?: return null

    return PayerSignable(
        transaction = this,
        message = PayerSignable.Message(
            payloadMessage().toHexString()
        )
    )
}

suspend fun Transaction.buildPayerSignable(): PayerSignable? {
    val payerAccount = FlowCadenceApi.getAccount(payer)
    val accountKeys = payerAccount.keys ?: return null

    return PayerSignable(
        transaction = this,
        message = PayerSignable.Message(
            payloadMessage().toHexString()
        )
    )
}

fun Transaction.encodeTransactionPayload(): String {
    return payloadMessage().toHexString()
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
