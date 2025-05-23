package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.google.gson.Gson
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.config.isGasFree
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
import org.onflow.flow.infrastructure.Cadence

private const val TAG = "Transaction"

suspend fun sendTransaction(
    builder: TransactionBuilder.() -> Unit,
): String? {
    val transactionBuilder = TransactionBuilder().apply { builder(this) }

    try {
        logd(TAG, "sendTransaction prepare")
        var tx = prepare(transactionBuilder)

        if (tx.envelopeSignatures.isEmpty() && isGasFree()) {
            logd(TAG, "sendTransaction request free gas envelope")
            tx = tx.addFreeGasEnvelope()
        } else if (tx.envelopeSignatures.isEmpty()) {
            logd(TAG, "sendTransaction sign envelope")
            tx = tx.addLocalSignatures()
        }

        logd(TAG, "sendTransaction to flow chain")
        val result = tx.send()
        val txID = result.id
        logd(TAG, "transaction id:$${txID}")
        vibrateTransaction()

        if (txID != null) {
            val txResult = result.waitForSeal()
            val isSuccess = when (txResult.status) {
                TransactionStatus.SEALED -> txResult.execution == TransactionExecution.success
                TransactionStatus.EXPIRED -> false
                TransactionStatus.EXECUTED -> txResult.execution == TransactionExecution.success
                else -> false
            }

            MixpanelManager.cadenceTransactionSigned(
                cadence = tx.script,
                txId = txID,
                authorizers = tx.authorizers,
                proposer = tx.proposalKey.address,
                payer = tx.payer,
                isSuccess = isSuccess
            )
        }
        return txID
    } catch (e: Exception) {
        loge(e)
        val errorMessage = when (e) {
            is InvalidKeyException -> "Invalid key: ${e.message}"
            is RuntimeException -> "Transaction error: ${e.message}"
            else -> "Unknown error: ${e.message}"
        }
        logd(TAG, "Transaction failed: $errorMessage")
        
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
    val transactionBuilder = TransactionBuilder().apply { 
        builder(this)
        isBridgePayer(true)
    }

    try {
        logd(TAG, "sendBridgeTransaction prepare")
        var tx = prepare(transactionBuilder)
        tx = tx.addFreeBridgeFeeEnvelope()

        logd(TAG, "sendBridgeTransaction to flow chain")
        val result = tx.send()
        val txID = result.id
        logd(TAG, "transaction id:$${txID}")
        vibrateTransaction()

        if (txID != null) {
            val txResult = result.waitForSeal()
            val isSuccess = when (txResult.status) {
                TransactionStatus.SEALED -> txResult.execution == TransactionExecution.success
                TransactionStatus.EXPIRED -> false
                TransactionStatus.EXECUTED -> txResult.execution == TransactionExecution.success
                else -> false
            }

            MixpanelManager.cadenceTransactionSigned(
                cadence = tx.script,
                txId = txID,
                authorizers = tx.authorizers,
                proposer = tx.proposalKey.address,
                payer = tx.payer,
                isSuccess = isSuccess
            )
        }
        return txID
    } catch (e: Exception) {
        loge(e)
        val errorMessage = when (e) {
            is InvalidKeyException -> "Invalid key: ${e.message}"
            is RuntimeException -> "Transaction error: ${e.message}"
            else -> "Unknown error: ${e.message}"
        }
        logd(TAG, "Bridge transaction failed: $errorMessage")
        
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
    var tx = prepareWithMultiSignature(
        walletAddress = account.address,
        restoreProposalKey = restoreProposalKey,
        builder = transBuilder,
    )

    // Sign with each provider
    providers.forEach { cryptoProvider ->
        val signer = createSigner(
            address = tx.proposalKey.address,
            keyIndex = accountKeys.first { cryptoProvider.getPublicKey() == it.publicKey }.index.toInt(),
            signer = cryptoProvider.getSigner()
        )
        tx = tx.signPayload(listOf(signer))
    }

    // Add envelope signatures if needed
    if (tx.envelopeSignatures.isEmpty()) {
        tx = if (isGasFree()) {
            logd(TAG, "sendTransaction request free gas envelope")
            tx.addFreeGasEnvelope()
        } else {
            logd(TAG, "sendTransaction sign envelope")
            tx.addLocalEnvelopeSignatures()
        }
    }

    logd(TAG, "sendTransaction to flow chain")
    val result = tx.send()
    val txID = result.id
    logd(TAG, "transaction id:$${txID}")
    vibrateTransaction()

    if (txID != null) {
        val txResult = result.waitForSeal()
        val isSuccess = when (txResult.status) {
            TransactionStatus.SEALED -> txResult.execution == TransactionExecution.success
            TransactionStatus.EXPIRED -> false
            TransactionStatus.EXECUTED -> txResult.execution == TransactionExecution.success
            else -> false
        }

        MixpanelManager.cadenceTransactionSigned(
            cadence = tx.script,
            txId = txID,
            authorizers = tx.authorizers,
            proposer = tx.proposalKey.address,
            payer = tx.payer,
            isSuccess = isSuccess
        )
    }

    return txID ?: throw RuntimeException("Failed to get transaction ID")
}

private fun createSigner(
    address: String,
    keyIndex: Int,
    signer: Signer
): Signer {
    return object : Signer {
        override var address: String = address
        override var keyIndex: Int = keyIndex

        override suspend fun sign(transaction: Transaction?, bytes: ByteArray): ByteArray {
            return signer.sign(bytes)
        }

        override suspend fun sign(bytes: ByteArray): ByteArray = signer.sign(bytes)
    }
}

suspend fun Transaction.send(): Transaction {
    val result = FlowCadenceApi.sendTransaction(this)
    return result.id?.let { FlowCadenceApi.getTransaction(it) } ?: throw RuntimeException("Failed to get transaction ID")
}

suspend fun Transaction.waitForSeal(): TransactionResult {
    return FlowCadenceApi.waitForSeal(id ?: throw RuntimeException("Transaction has no ID"))
}

suspend fun Transaction.addLocalSignatures(): Transaction {
    val account = FlowCadenceApi.getAccount(proposalKey.address)
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

suspend fun Transaction.buildPayerSignable(): PayerSignable? {
    val payerAccount = FlowCadenceApi.getAccount(payer)
    payerAccount.keys ?: return null

    // Ensure all hex strings in the transaction have the 0x prefix removed
    val formattedTx = copy(
        referenceBlockId = referenceBlockId.removeHexPrefix(),
        payer = payer.removeHexPrefix(),
        proposalKey = proposalKey.copy(
            address = proposalKey.address.removeHexPrefix()
        ),
        authorizers = authorizers.map { it.removeHexPrefix() },
        payloadSignatures = payloadSignatures.map { sig ->
            sig.copy(
                address = sig.address.removeHexPrefix(),
                signature = sig.signature.removeHexPrefix()
            )
        },
        envelopeSignatures = envelopeSignatures.map { sig ->
            sig.copy(
                address = sig.address.removeHexPrefix(),
                signature = sig.signature.removeHexPrefix()
            )
        }
    )

    return PayerSignable(
        transaction = formattedTx,
        message = PayerSignable.Message(
            formattedTx.payloadMessage().toHexString()
        )
    )
}

suspend fun Transaction.buildBridgeFeePayerSignable(): PayerSignable? {
    val payerAccount = FlowCadenceApi.getAccount(payer)
    payerAccount.keys ?: return null

    // Ensure all hex strings in the transaction have the 0x prefix removed
    val formattedTx = copy(
        referenceBlockId = referenceBlockId.removeHexPrefix(),
        payer = payer.removeHexPrefix(),
        proposalKey = proposalKey.copy(
            address = proposalKey.address.removeHexPrefix()
        ),
        authorizers = authorizers.map { it.removeHexPrefix() },
        payloadSignatures = payloadSignatures.map { sig ->
            sig.copy(
                address = sig.address.removeHexPrefix(),
                signature = sig.signature.removeHexPrefix()
            )
        },
        envelopeSignatures = envelopeSignatures.map { sig ->
            sig.copy(
                address = sig.address.removeHexPrefix(),
                signature = sig.signature.removeHexPrefix()
            )
        }
    )

    return PayerSignable(
        transaction = formattedTx,
        message = PayerSignable.Message(
            formattedTx.payloadMessage().toHexString()
        )
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

private fun String.ensureHexFormat(): String {
    return if (startsWith("0x")) this else "0x$this"
}

private fun String.removeHexPrefix(): String {
    return if (startsWith("0x")) substring(2) else this
}

suspend fun prepare(builder: TransactionBuilder): Transaction {
    logd(TAG, "prepare builder:$builder")
    val account = FlowCadenceApi.getAccount(builder.walletAddress?.toAddress().orEmpty())
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("get account error")
    val accountKeys = account.keys ?: throw InvalidKeyException("Account has no keys")
    logd(TAG, accountKeys)
    logd(TAG, cryptoProvider.getPublicKey())
    
    // Normalize the crypto provider's public key by adding 0x prefix if missing
    val providerPublicKey = cryptoProvider.getPublicKey().ensureHexFormat()
    logd(TAG, providerPublicKey)

    val currentKey = accountKeys.findLast { it.publicKey == providerPublicKey }
        ?: throw InvalidKeyException("Get account key error")

    val payer = builder.payer?.removeHexPrefix() ?: (if (isGasFree()) AppConfig.payer().address.removeHexPrefix() else builder.walletAddress?.removeHexPrefix()).orEmpty()
    val authorizers = when {
        builder.isBridgePayer -> {
            // For bridge payer transactions, we need both the proposer and payer as authorizers
            listOf(account.address.removeHexPrefix(), payer)
        }
        builder.authorizers.isNullOrEmpty() -> {
            listOf(account.address.removeHexPrefix())
        }
        else -> {
            builder.authorizers?.map { it.removeHexPrefix() } ?: listOf(account.address.removeHexPrefix())
        }
    }

    return TransactionBuilder(
        script = builder.script.orEmpty(),
        arguments = builder.arguments.map { Cadence.string(it.toString()) },
        gasLimit = BigInteger.fromLong(builder.limit?.toLong() ?: 9999L)
    ).apply {
        withReferenceBlockId(FlowCadenceApi.getBlockHeader(null).id.removeHexPrefix())
        withPayer(payer)
        withProposalKey(
            address = account.address.removeHexPrefix(),
            keyIndex = currentKey.index.toInt(),
            sequenceNumber = BigInteger.fromInt(currentKey.sequenceNumber.toInt())
        )
        withAuthorizers(authorizers)
    }.build()
}

suspend fun prepareWithMultiSignature(
    walletAddress: String,
    restoreProposalKey: AccountPublicKey,
    builder: TransactionBuilder
): Transaction {
    logd(TAG, "prepare builder:$builder")

    val payer = builder.payer?.removeHexPrefix() ?: (if (isGasFree()) AppConfig.payer().address.removeHexPrefix() else builder.walletAddress?.removeHexPrefix()).orEmpty()
    val authorizers = when {
        builder.isBridgePayer -> {
            // For bridge payer transactions, we need both the proposer and payer as authorizers
            listOf(walletAddress.removeHexPrefix(), payer)
        }
        builder.authorizers.isNullOrEmpty() -> {
            listOf(walletAddress.removeHexPrefix())
        }
        else -> {
            builder.authorizers?.map { it.removeHexPrefix() } ?: listOf(walletAddress.removeHexPrefix())
        }
    }

    return TransactionBuilder(
        script = builder.script.orEmpty(),
        arguments = builder.arguments.map { Cadence.string(it.toString()) },
        gasLimit = BigInteger.fromLong(builder.limit?.toLong() ?: 9999L)
    ).apply {
        withReferenceBlockId(FlowCadenceApi.getBlockHeader(null).id.removeHexPrefix())
        withPayer(payer)
        withProposalKey(
            address = walletAddress.removeHexPrefix(),
            keyIndex = restoreProposalKey.index.toInt(),
            sequenceNumber = BigInteger.fromInt(restoreProposalKey.sequenceNumber.toInt())
        )
        withAuthorizers(authorizers)
    }.build()
}

suspend fun Transaction.addFreeGasEnvelope(): Transaction {
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

suspend fun Transaction.addFreeBridgeFeeEnvelope(): Transaction {
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

