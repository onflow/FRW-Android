package com.flowfoundation.wallet.manager.flowjvm.transaction

import com.flow.wallet.CryptoProvider
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
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import org.onflow.flow.models.*
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.getFlowAddress
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import org.onflow.flow.infrastructure.getTypeName

private const val TAG = "Transaction"

suspend fun sendTransaction(
    builder: TransactionBuilder.() -> Unit,
): String? {
    val transactionBuilder = TransactionBuilder().apply { builder(this) }

    try {
        logd(TAG, "sendTransaction prepare")
        var tx = prepare(transactionBuilder)
        logd(TAG, "Prepared and signed Tx: $tx")

        // For free gas transactions, we still need to add the payer's envelope signature
        if (tx.envelopeSignatures.isEmpty() && isGasFree()) {
            logd(TAG, "sendTransaction request free gas envelope")
            tx = tx.addFreeGasEnvelope()
        } else if (tx.envelopeSignatures.isEmpty()) {
            logd(TAG, "sendTransaction sign local envelope")
            // For non-free gas, we need to add the local wallet's envelope signature
            tx = tx.addLocalEnvelopeSignatures()
        }
        logd(TAG, "Tx after envelope signatures: $tx")

        // Sanitize all signatures to ensure signerIndex uses the default (-1) to be omitted by serializer
        tx = tx.copy(
            payloadSignatures = tx.payloadSignatures.map {
                TransactionSignature(address = it.address, keyIndex = it.keyIndex, signature = it.signature /* default signerIndex = -1 */)
            }.distinctBy { "${it.address}-${it.keyIndex}-${it.signature}" } // Ensure distinctness after re-mapping
             .sortedWith(compareBy<TransactionSignature> { it.address }.thenBy { it.keyIndex }), // Apply KMM sorting
            envelopeSignatures = tx.envelopeSignatures.map {
                TransactionSignature(address = it.address, keyIndex = it.keyIndex, signature = it.signature /* default signerIndex = -1 */)
            }.distinctBy { "${it.address}-${it.keyIndex}-${it.signature}" } // Ensure distinctness after re-mapping
             .sortedWith(compareBy<TransactionSignature> { it.address }.thenBy { it.keyIndex }) // Apply KMM sorting
        )
        logd(TAG, "Tx after final sanitization of signerIndex: $tx")

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
    logd(TAG, "sendTransactionWithMultiSignature prepare")
    val appBuilder = TransactionBuilder().apply { builder(this) }

    // prepareAndSignWithMultiSignature will use KMM's buildAndSign(), handling payload and local payer envelope.
    var tx = prepareAndSignWithMultiSignature(
        appBuilder = appBuilder,
        providers = providers
    )

    // If gas is free (external payer), we still need to add the free gas envelope signature externally.
    // buildAndSign would not have handled this as the payer isn't in the local providers list.
    if (isGasFree()) {
        // Ensure payer in tx matches the expected free gas payer before adding its signature
        val expectedFreeGasPayer = AppConfig.payer().address.removeHexPrefix()
        if (tx.payer.removeHexPrefix() == expectedFreeGasPayer) {
            logd(TAG, "sendTransactionWithMultiSignature: Adding free gas envelope as payer is external.")
            tx = tx.addFreeGasEnvelope()
        } else {
            logd(TAG, "sendTransactionWithMultiSignature: Gas is free, but tx.payer (${tx.payer}) doesn't match AppConfig payer ($expectedFreeGasPayer). Skipping addFreeGasEnvelope.")
        }
    } else {
        // If not gas-free, and the local payer's envelope was somehow not added by buildAndSign 
        // (e.g. if payer was specified but not part of `providers`), this would be a fallback.
        // However, with buildAndSign, this should ideally not be needed if the local payer is among the signers.
        if (tx.envelopeSignatures.none { it.address.removeHexPrefix() == tx.payer.removeHexPrefix() }) {
            val localPayerAddress = appBuilder.walletAddress?.toAddress()?.removeHexPrefix()
            if (tx.payer.removeHexPrefix() == localPayerAddress) {
                 logd(TAG, "sendTransactionWithMultiSignature: Payer is local but no envelope signature found from buildAndSign. Attempting addLocalEnvelopeSignatures.")
                 // This implies the payer's CryptoProvider might not have been in `providers` or buildAndSign didn't cover it.
                 // This situation needs careful review of how local payer is handled in `providers` for multi-sig.
                 // For now, retain the local signature attempt as a fallback.
                 tx = tx.addLocalEnvelopeSignatures() 
            } else {
                logd(TAG, "sendTransactionWithMultiSignature: Payer (${tx.payer}) is not the local wallet address ($localPayerAddress) and not free gas. No envelope added.")
            }
        }
    }

    logd(TAG, "sendTransactionWithMultiSignature to flow chain")
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

// Renamed from prepareWithMultiSignature and refactored
suspend fun prepareAndSignWithMultiSignature(
    appBuilder: TransactionBuilder, // The app-level builder
    providers: List<CryptoProvider> // The list of local crypto providers for signing
): Transaction {
    logd(TAG, "prepareAndSignWithMultiSignature builder: $appBuilder")

    val proposerAddress = appBuilder.walletAddress?.toAddress()
        ?: throw IllegalArgumentException("Wallet address (proposer) is required for multi-signature.")
    
    val flowAccount = FlowCadenceApi.getAccount(proposerAddress)
    val accountKeys = flowAccount.keys 
        ?: throw InvalidKeyException("On-chain account $proposerAddress has no keys")

    // Determine the proposal key. For multi-sig, this is often the first provider's key or a designated one.
    // Here, we try to find a key on-chain that matches the first provider.
    // This logic might need to be more flexible depending on exact multi-sig key rotation/management.
    val firstProviderPublicKey = providers.firstOrNull()?.getPublicKey()?.ensureHexFormat()
        ?: throw IllegalArgumentException("At least one crypto provider is required for multi-signature proposal key selection.")

    val designatedProposalKey = accountKeys.findLast { accKey ->
        val accPubRaw = accKey.publicKey.removeHexPrefix().lowercase()
        val providerPubRaw = firstProviderPublicKey.removeHexPrefix().lowercase()
        val providerPubStripped = if (providerPubRaw.startsWith("04") && providerPubRaw.length == 130) providerPubRaw.substring(2) else providerPubRaw
        accPubRaw == providerPubRaw || accPubRaw == providerPubStripped
    } ?: throw InvalidKeyException("Proposal key matching first provider ($firstProviderPublicKey) not found on account $proposerAddress")
    
    logd(TAG, "Designated proposal key for multi-sig: index ${designatedProposalKey.index}, seqNo ${designatedProposalKey.sequenceNumber}")

    // Create KMM signers for all payload providers
    val kmmSigners = providers.mapNotNull { cryptoProviderInstance -> // Renamed to avoid conflict with outer cryptoProvider if any
        val providerPublicKey = cryptoProviderInstance.getPublicKey().ensureHexFormat()
        val onChainKey = accountKeys.findLast { accKey ->
            val accPubRaw = accKey.publicKey.removeHexPrefix().lowercase()
            val currentProviderPubRaw = providerPublicKey.removeHexPrefix().lowercase()
            val currentProviderPubStripped = if (currentProviderPubRaw.startsWith("04") && currentProviderPubRaw.length == 130) currentProviderPubRaw.substring(2) else currentProviderPubRaw
            accPubRaw == currentProviderPubRaw || accPubRaw == currentProviderPubStripped
        }
        if (onChainKey == null) {
            loge(TAG, "Key for crypto provider ${cryptoProviderInstance.getPublicKey()} not found on account $proposerAddress. Skipping this signer.")
            null
        } else {
            val keyOnChainHashingAlgorithm = onChainKey.hashingAlgorithm // KMM HashingAlgorithm
            logd(TAG, "Using KMM hashing algorithm ${keyOnChainHashingAlgorithm.name} for multi-sig provider ${cryptoProviderInstance.getPublicKey()} (key index ${onChainKey.index}) on account $proposerAddress")
            
            // cryptoProviderInstance is com.flow.wallet.CryptoProvider
            val signerInstance: org.onflow.flow.models.Signer = cryptoProviderInstance.getSigner(keyOnChainHashingAlgorithm)
            signerInstance.address = proposerAddress.removeHexPrefix()
            signerInstance.keyIndex = onChainKey.index.toInt()
            signerInstance // Return the configured KMM Signer
        }
    }.distinctBy { "${it.address}-${it.keyIndex}" } // `it` here is org.onflow.flow.models.Signer

    if (kmmSigners.isEmpty() && providers.isNotEmpty()) {
        throw InvalidKeyException("None of the provided crypto providers have a matching key on account $proposerAddress.")
    } else if (kmmSigners.isEmpty() && providers.isEmpty()) {
        throw IllegalArgumentException("At least one crypto provider is required for multi-signature.")
    }

    val actualPayerAddress = (appBuilder.payer?.removeHexPrefix()
        ?: (if (isGasFree()) AppConfig.payer().address.removeHexPrefix() else proposerAddress.removeHexPrefix())).orEmpty()

    val authorizers = when {
        appBuilder.isBridgePayer -> listOf(proposerAddress.removeHexPrefix(), actualPayerAddress)
        appBuilder.authorizers.isNullOrEmpty() -> listOf(proposerAddress.removeHexPrefix())
        else -> appBuilder.authorizers?.map { it.removeHexPrefix() } ?: listOf(proposerAddress.removeHexPrefix())
    }.distinct() // Ensure authorizers are distinct

    logd(TAG, "prepareAndSignWithMultiSignature: proposer=$proposerAddress, payer=$actualPayerAddress, authorizers=$authorizers")
    logd(TAG, "prepareAndSignWithMultiSignature: KMM Signers for buildAndSign (${kmmSigners.size}): ${kmmSigners.joinToString { it.address + "-" + it.keyIndex }}")

    // Use Flow KMM's TransactionBuilder and its buildAndSign() method
    return org.onflow.flow.models.TransactionBuilder(
        script = appBuilder.script.orEmpty(),
        arguments = appBuilder.arguments,
        gasLimit = com.ionspin.kotlin.bignum.integer.BigInteger.fromLong(appBuilder.limit?.toLong() ?: 9999L)
    ).apply {
        withReferenceBlockId(FlowCadenceApi.getBlockHeader(null).id.removeHexPrefix())
        withPayer(actualPayerAddress)
        withProposalKey(
            address = proposerAddress.removeHexPrefix(),
            keyIndex = designatedProposalKey.index.toInt(),
            sequenceNumber = com.ionspin.kotlin.bignum.integer.BigInteger.fromInt(designatedProposalKey.sequenceNumber.toInt())
        )
        withAuthorizers(authorizers)
        withSigners(kmmSigners) // Pass all KMM-compatible signers
    }.buildAndSign()
}

suspend fun Transaction.send(): Transaction {
    logd(TAG, "Sending transaction: $this")

    val submittedTxId: String = try {
        // This call might throw MissingFieldException if KMM SDK tries to parse
        // the Access Node's POST /transactions response as a full Transaction object.
        val responseTransaction = FlowCadenceApi.sendTransaction(this)
        responseTransaction.id ?: throw RuntimeException("Transaction ID was null in response from sendTransaction. Full response: $responseTransaction")
    } catch (e: kotlinx.serialization.MissingFieldException) {
        logd(TAG, "MissingFieldException while parsing sendTransaction response. This indicates KMM SDK may not expect the Access Node's response format for POST /transactions. The transaction might have succeeded or failed with the node. The original error from the node, if any, is often in the cause or message.")
        // The actual error from the node (like "invalid signature") is often in e.cause's message.
        val rawErrorMessage = e.cause?.message ?: e.message ?: "Unknown error during sendTransaction response parsing"

        if (rawErrorMessage.contains(""""code": 400""") && rawErrorMessage.contains("invalid signature")) {
            throw RuntimeException("Flow Access Node rejected transaction: Invalid Signature. Raw response: $rawErrorMessage", e)
        }
        
        // Attempt to gracefully handle by extracting ID if possible, otherwise rethrow a more informative error.
        // This is speculative, as the transaction might have failed.
        // A more robust solution would involve FlowCadenceApi.sendTransaction returning a dedicated response type.
        loge(TAG, "Transaction submission status uncertain due to response parsing error. Raw error: $rawErrorMessage")
        throw RuntimeException("Failed to parse Flow Access Node response after sending transaction. The transaction may or may not have been processed. Raw error: $rawErrorMessage", e)
        
    } catch (e: RuntimeException) {
        // Catch other runtime exceptions, specifically looking for the "invalid signature" message
        // that might be directly thrown if KMM's error handling passes it through.
        if (e.message?.contains("Invalid Flow argument: invalid transaction: invalid signature") == true) {
            logd(TAG, "Transaction rejected by Flow Access Node: Invalid Signature. Details: ${e.message}")
            // Re-throw with a clear message, keeping the cause
            throw RuntimeException("Flow Access Node rejected transaction: Invalid Signature. Details: ${e.message}", e)
        }
        // Re-throw other RuntimeExceptions
        throw e
    }

    logd(TAG, "Transaction submitted or ID extracted, assumed ID: $submittedTxId")

    // Wait for seal using the extracted ID
    val seal = FlowCadenceApi.waitForSeal(submittedTxId)
    logd(TAG, "Transaction sealed. Status=${seal.status}, Execution=${seal.execution}")

    logd(TAG, "Fetching full transaction $submittedTxId after seal")
    // Fetch the canonical transaction details from the network
    val fullTx = FlowCadenceApi.getTransaction(submittedTxId)
    logd(TAG, "Retrieved full transaction: $fullTx")
    return fullTx
}

suspend fun Transaction.waitForSeal(): TransactionResult {
    return FlowCadenceApi.waitForSeal(id ?: throw RuntimeException("Transaction has no ID"))
}

suspend fun Transaction.addLocalEnvelopeSignatures(): Transaction {
    // `this` is the transaction object.
    // It has `this.payer`, `this.proposalKey.address`, and `this.proposalKey.keyIndex`.

    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("Current crypto provider is null for local envelope signing.")

    val signingAddress = this.payer // The envelope is signed by the payer.
    val keyIndexForSigning: Int
    val hashingAlgorithmForSigning: org.onflow.flow.models.HashingAlgorithm

    logd(TAG, "addLocalEnvelopeSignatures: Tx Payer=${this.payer}, Tx ProposalAddress=${this.proposalKey.address}, Tx ProposalKeyIndex=${this.proposalKey.keyIndex}")

    // Check if the payer of this transaction is also its proposer.
    // In non-gas-free scenarios, this should typically be true.
    if (this.payer.removeHexPrefix().equals(this.proposalKey.address.removeHexPrefix(), ignoreCase = true)) {
        // Payer is the Proposer. The envelope signature MUST cover the transaction's designated proposal key.
        keyIndexForSigning = this.proposalKey.keyIndex
        logd(TAG, "Payer is Proposer. Using tx.proposalKey.keyIndex ($keyIndexForSigning) for envelope signature.")

        // Verify that the current cryptoProvider can actually sign for this specific proposal key index
        // on the payer's (proposer's) account, and get its hashing algorithm.
        val payerAccount = FlowCadenceApi.getAccount(this.payer) // Payer is proposer here
        val payerAccountKeys = payerAccount.keys
            ?: throw InvalidKeyException("Payer account ${this.payer} has no keys for proposal key verification.")

        val targetProposalKeyOnAccount = payerAccountKeys.find { it.index.toInt() == keyIndexForSigning }
            ?: throw InvalidKeyException(
                "Transaction's proposal key (index $keyIndexForSigning) not found on payer/proposer account ${this.payer}. " +
                "On-chain keys: ${payerAccountKeys.joinToString { accKey -> "idx:${accKey.index} pub:${accKey.publicKey.take(10)}..." }}"
            )

        // Compare the current app crypto provider's public key with the on-chain public key of the target proposal key.
        val providerPublicKeyFromApp = cryptoProvider.getPublicKey().ensureHexFormat()
        val providerPubRawLower = providerPublicKeyFromApp.removeHexPrefix().lowercase()
        // KMM AccountKey.publicKey is raw (no 0x) and for P256, often without the leading "04".
        // Provider's key might have "04" if it's uncompressed P256.
        val providerPubStrippedLower = if (providerPubRawLower.startsWith("04") && providerPubRawLower.length == 130) {
            providerPubRawLower.substring(2)
        } else {
            providerPubRawLower
        }

        val onChainProposalKeyPubRawLower = targetProposalKeyOnAccount.publicKey.lowercase() // KMM stores it raw

        val providerMatchesTargetKey = (onChainProposalKeyPubRawLower == providerPubRawLower ||
                                        onChainProposalKeyPubRawLower == providerPubStrippedLower)

        if (!providerMatchesTargetKey) {
            throw InvalidKeyException(
                "Current crypto provider (pubKey raw: $providerPubRawLower, stripped: $providerPubStrippedLower) does not match the public key " +
                "of the transaction's proposal key (account: ${this.payer}, keyIndex: $keyIndexForSigning, on-chain pubKey raw: $onChainProposalKeyPubRawLower). " +
                "Cannot sign envelope for proposal key."
            )
        }
        hashingAlgorithmForSigning = targetProposalKeyOnAccount.hashingAlgorithm
        logd(TAG, "Verified current provider can sign for tx.proposalKey. Using on-chain hashing algorithm: ${hashingAlgorithmForSigning.name}")

    } else {
        // Payer is different from Proposer.
        // This is an unusual state for addLocalEnvelopeSignatures if isGasFree() is false,
        // as it implies an external, non-gas-station entity is paying, but we're trying to sign with the local user's key.
        // This branch is kept for structural completeness but might indicate a misconfiguration if hit in a typical local-payer flow.
        logd(TAG, "Payer (${this.payer}) is different from Proposer (${this.proposalKey.address}) in addLocalEnvelopeSignatures. This is unusual for a non-gas-free transaction.")
        logd(TAG, "Attempting to find current local provider's key on the specified payer's account (${this.payer}).")

        val externalPayerAccount = FlowCadenceApi.getAccount(this.payer)
        val externalPayerAccountKeys = externalPayerAccount.keys ?: throw InvalidKeyException("Specified payer account ${this.payer} has no keys.")
        val providerPublicKey = cryptoProvider.getPublicKey().ensureHexFormat()
        val providerPubRaw = providerPublicKey.removeHexPrefix().lowercase()
        val providerPubStripped = if (providerPubRaw.startsWith("04") && providerPubRaw.length == 130) providerPubRaw.substring(2) else providerPubRaw

        val currentLocalProvidersKeyOnExternalPayerAccount = externalPayerAccountKeys.findLast { accKey ->
            val accPubRaw = accKey.publicKey.removeHexPrefix().lowercase()
            accPubRaw == providerPubRaw || accPubRaw == providerPubStripped
        } ?: throw InvalidKeyException("Current local crypto provider's key ($providerPublicKey) not found on the specified (external) payer account ${this.payer}.")

        keyIndexForSigning = currentLocalProvidersKeyOnExternalPayerAccount.index.toInt()
        hashingAlgorithmForSigning = currentLocalProvidersKeyOnExternalPayerAccount.hashingAlgorithm
        logd(TAG, "Found current local provider's key on the specified payer account ${this.payer}: keyIndex=$keyIndexForSigning, hashAlgo=${hashingAlgorithmForSigning.name}")
    }

    logd(TAG, "Final KMM Signer for local envelope: address=${signingAddress.removeHexPrefix()}, keyIndex=$keyIndexForSigning, hashAlgo=${hashingAlgorithmForSigning.name}")

    val kmmSigner: org.onflow.flow.models.Signer = cryptoProvider.getSigner(hashingAlgorithmForSigning).apply {
        this.address = signingAddress.removeHexPrefix() // Payer address
        this.keyIndex = keyIndexForSigning
    }

    // This will sign the envelope. If the KMM SDK's signEnvelope clears payload signatures,
    // this envelope signature (now correctly targeted at the proposalKey if payer==proposer)
    // will be the one covering the proposal requirement.
    return this.signEnvelope(listOf(kmmSigner))
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
            TransactionSignature(
                address = sig.address.removeHexPrefix(),
                keyIndex = sig.keyIndex,
                signature = sig.signature.removeHexPrefix()
            )
        },
        envelopeSignatures = envelopeSignatures.map { sig ->
            TransactionSignature(
                address = sig.address.removeHexPrefix(),
                keyIndex = sig.keyIndex,
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
            TransactionSignature(
                address = sig.address.removeHexPrefix(),
                keyIndex = sig.keyIndex,
                signature = sig.signature.removeHexPrefix()
            )
        },
        envelopeSignatures = envelopeSignatures.map { sig ->
            TransactionSignature(
                address = sig.address.removeHexPrefix(),
                keyIndex = sig.keyIndex,
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

private fun String.ensureHexFormat(): String {
    return if (startsWith("0x")) this else "0x$this"
}

private fun String.removeHexPrefix(): String {
    return if (startsWith("0x")) substring(2) else this
}

suspend fun prepare(builder: TransactionBuilder): Transaction {
    // Log inputs from the app-level TransactionBuilder first
    logd(TAG, "--- prepare() called by sendTransaction/sendBridgeTransaction ---")
    logd(TAG, "App-level builder input - scriptId: ${builder.scriptId}")
    logd(TAG, "App-level builder input - script content: [${builder.script?.take(100)?.replace("\n", "<NL>")}...]") // Log first 100 chars of script
    logd(TAG, "App-level builder input - arguments (${builder.arguments.size}): ${builder.arguments.map { it.getTypeName() + ":" + it.value?.toString()?.take(60) }}")
    logd(TAG, "App-level builder input - walletAddress: ${builder.walletAddress}")
    logd(TAG, "App-level builder input - payer: ${builder.payer}")
    logd(TAG, "App-level builder input - limit: ${builder.limit}")

    // Get wallet address and account info
    val walletAddress = builder.walletAddress?.toAddress().orEmpty()
    logd(TAG, "prepare target walletAddress (from builder.walletAddress): $walletAddress")
    
    val flowAccount = FlowCadenceApi.getAccount(walletAddress)

    val currentNetworkName = chainNetWorkString()
    logd(TAG, "Current network for account lookup: $currentNetworkName. Target transaction address: $walletAddress")

    // Find local account instance
    val localAccountInstance = AccountManager.list().find { acc ->
        val accFlowAddress = acc.getFlowAddress(currentNetworkName, TAG)?.toAddress()
        logd(TAG, "Iterating AccountManager.list(): Checking local account '${acc.userInfo.username}', its address for $currentNetworkName is '$accFlowAddress'")
        accFlowAddress == walletAddress
    } ?: throw RuntimeException("Could not find local Account instance for address $walletAddress on network $currentNetworkName.")

    logd(TAG, "Successfully found local account ${localAccountInstance.userInfo.username} for address $walletAddress. Generating CryptoProvider.")
    
    // Get crypto provider
    val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(localAccountInstance)
        ?: throw RuntimeException("Could not generate CryptoProvider for local account ${localAccountInstance.userInfo.username} (address $walletAddress)")
    
    // Get account keys and find matching key
    val accountKeys = flowAccount.keys ?: throw InvalidKeyException("On-chain account $walletAddress has no keys")
    logd(TAG, "On-chain keys for $walletAddress: $accountKeys")
    logd(TAG, "Provider public key from local account ${localAccountInstance.userInfo.username} (for $walletAddress): ${cryptoProvider.getPublicKey()}")
    
    val providerPublicKey = cryptoProvider.getPublicKey().ensureHexFormat()
    logd(TAG, "Normalized provider public key: $providerPublicKey")

    val providerPubRaw = providerPublicKey.removeHexPrefix().lowercase()
    val providerPubStripped = if (providerPubRaw.startsWith("04") && providerPubRaw.length == 130) {
        providerPubRaw.substring(2)
    } else {
        providerPubRaw
    }

    val currentKey = accountKeys.findLast { accKey ->
        val accPubRaw = accKey.publicKey.removeHexPrefix().lowercase()
        accPubRaw == providerPubRaw || accPubRaw == providerPubStripped
    } ?: throw InvalidKeyException(
        "Get account key error: Provider key $providerPublicKey (raw: $providerPubRaw, stripped: $providerPubStripped) " +
        "from local account ${localAccountInstance.userInfo.username} (for $walletAddress) " +
        "not found among on-chain keys for $walletAddress: ${accountKeys.map { it.publicKey + " (raw: " + it.publicKey.removeHexPrefix().lowercase() + ")" } }"
    )

    logd(TAG, "Found matching key for $walletAddress on-chain: index ${currentKey.index}, seqNo ${currentKey.sequenceNumber}, hashAlgo: ${currentKey.hashingAlgorithm.name}")

    // Determine the correct hashing algorithm from the on-chain key
    val keyOnChainHashingAlgorithm = currentKey.hashingAlgorithm // This is org.onflow.flow.models.HashingAlgorithm
    logd(TAG, "Using KMM hashing algorithm ${keyOnChainHashingAlgorithm.name} for key ${currentKey.index} on account $walletAddress")

    // Determine payer and authorizers
    val payer = builder.payer?.removeHexPrefix() ?: (if (isGasFree()) AppConfig.payer().address.removeHexPrefix() else builder.walletAddress?.removeHexPrefix()).orEmpty()
    val authorizers = when {
        builder.isBridgePayer -> {
            listOf(flowAccount.address.removeHexPrefix(), payer)
        }
        builder.authorizers.isNullOrEmpty() -> {
            listOf(flowAccount.address.removeHexPrefix())
        }
        else -> {
            builder.authorizers?.map { it.removeHexPrefix() } ?: listOf(flowAccount.address.removeHexPrefix())
        }
    }

    // Get reference block ID
    val referenceBlockId = FlowCadenceApi.getBlockHeader(null).id.removeHexPrefix()
    val script = builder.script.orEmpty()
    logd(TAG, "Cadence script for TransactionBuilder: $script")

    // Log each argument and its encoded form
    logd(TAG, "--- Verifying argument encoding before passing to KMM TransactionBuilder ---")
    builder.arguments.forEachIndexed { index, arg ->
        val encodedValue = try {
            arg.encode()
        } catch (e: Exception) {
            loge(TAG, "ERROR encoding argument $index  value: ${arg.value}): ${e.message}")
            "ERROR_ENCODING"
        }
        logd(TAG, "Arg $index: , Value=${arg.value}, EncodedForm='${encodedValue}'")
        if (encodedValue.isEmpty()) {
            logd(TAG, "CRITICAL: Argument $index  encoded to an EMPTY STRING!")
        }
    }
    logd(TAG, "--- End of argument encoding verification ---")

    // Get the KMM Signer directly, configured with the correct hashing algorithm
    logd(TAG, "Getting KMM Signer from CryptoProvider with hashing algorithm: $keyOnChainHashingAlgorithm")
    
    // Add detailed logging about the CryptoProvider type
    logd(TAG, "CryptoProvider instance type: ${cryptoProvider.javaClass.name}")
    logd(TAG, "CryptoProvider getHashAlgorithm(): ${cryptoProvider.getHashAlgorithm()}")
    logd(TAG, "CryptoProvider getSignatureAlgorithm(): ${cryptoProvider.getSignatureAlgorithm()}")
    
    val kmmSigner = cryptoProvider.getSigner(keyOnChainHashingAlgorithm).apply {
        logd(TAG, "Setting signer address to: ${flowAccount.address.removeHexPrefix()}")
        this.address = flowAccount.address.removeHexPrefix()
        logd(TAG, "Setting signer keyIndex to: ${currentKey.index.toInt()}")
        this.keyIndex = currentKey.index.toInt()
        logd(TAG, "KMM Signer configured - address: $address, keyIndex: $keyIndex")
        
        // Log the Signer type
        logd(TAG, "KMM Signer instance type: ${this.javaClass.name}")
        logd(TAG, "KMM Signer superclass: ${this.javaClass.superclass?.name}")
        logd(TAG, "KMM Signer interfaces: ${this.javaClass.interfaces.map { it.name }}")
    }
    
    // Test the signer directly before using it in Transaction.sign()
    logd(TAG, "Testing Signer directly with sample data...")
    try {
        val testData = "test123".toByteArray()
        logd(TAG, "Calling kmmSigner.sign() directly with test data...")
        val testSignature = kmmSigner.sign(testData)
        logd(TAG, "Direct signer test successful. Signature length: ${testSignature.size}")
    } catch (e: Exception) {
        loge(TAG, "Direct signer test failed: ${e.message}")
        loge(TAG, "Stack trace: ${e.stackTraceToString()}")
    }

    // Use Flow KMM's TransactionBuilder and its buildAndSign() method
    logd(TAG, "Building transaction using Flow KMM TransactionBuilder and buildAndSign()")
    logd(TAG, "Pre-buildAndSign transaction details:")
    logd(TAG, "  - Script length: ${script.length} chars")
    logd(TAG, "  - Arguments count: ${builder.arguments.size}")
    logd(TAG, "  - Gas limit: ${builder.limit}")
    logd(TAG, "  - Reference block ID: $referenceBlockId")
    logd(TAG, "  - Payer: $payer")
    logd(TAG, "  - Proposal key: address=${flowAccount.address.removeHexPrefix()}, keyIndex=${currentKey.index.toInt()}, seqNum=${currentKey.sequenceNumber.toInt()}")
    logd(TAG, "  - Authorizers: $authorizers")
    logd(TAG, "  - Signer: address=${kmmSigner.address}, keyIndex=${kmmSigner.keyIndex}")
    
    val result = try {
        logd(TAG, "Calling KMM TransactionBuilder.buildAndSign()...")
        
        // Instead of using buildAndSign(), build manually and sign with our controlled signers
        logd(TAG, "Building transaction manually to ensure our CryptoProvider signing is used...")
        val builtTransaction = org.onflow.flow.models.TransactionBuilder(
            script = script,
            arguments = builder.arguments,
            gasLimit = com.ionspin.kotlin.bignum.integer.BigInteger.fromInt(builder.limit!!)
        ).apply {
            withReferenceBlockId(referenceBlockId)
            withPayer(payer)
            withProposalKey(
                address = flowAccount.address.removeHexPrefix(),
                keyIndex = currentKey.index.toInt(),
                sequenceNumber = com.ionspin.kotlin.bignum.integer.BigInteger.fromInt(currentKey.sequenceNumber.toInt())
            )
            withAuthorizers(authorizers)
        }.build() // Build but don't sign yet
        
        logd(TAG, "Built transaction, now manually signing with our CryptoProvider signer...")
        
        // Manually sign using our controlled signer to ensure proper algorithm usage
        logd(TAG, "About to call Transaction.sign() with signers: [${kmmSigner.address}:${kmmSigner.keyIndex}]")
        logd(TAG, "Transaction before signing - payloadSignatures: ${builtTransaction.payloadSignatures.size}, envelopeSignatures: ${builtTransaction.envelopeSignatures.size}")
        
        val signedTransaction = builtTransaction.sign(listOf(kmmSigner))
        
        logd(TAG, "Transaction.sign() completed")
        logd(TAG, "Transaction after signing - payloadSignatures: ${signedTransaction.payloadSignatures.size}, envelopeSignatures: ${signedTransaction.envelopeSignatures.size}")
        
        signedTransaction
    } catch (e: Exception) {
        loge(TAG, "ERROR in manual build and sign: ${e.javaClass.simpleName}: ${e.message}")
        loge(TAG, "Stack trace: ${e.stackTraceToString()}")
        if (e.cause != null) {
            loge(TAG, "Caused by: ${e.cause?.javaClass?.simpleName}: ${e.cause?.message}")
        }
        throw e
    }
    
    logd(TAG, "buildAndSign() completed successfully")
    logd(TAG, "Result transaction details:")
    logd(TAG, "  - ID: ${result.id}")
    logd(TAG, "  - Payload signatures: ${result.payloadSignatures.size}")
    logd(TAG, "  - Envelope signatures: ${result.envelopeSignatures.size}")
    result.payloadSignatures.forEachIndexed { index, sig ->
        logd(TAG, "  - Payload sig $index: address=${sig.address}, keyIndex=${sig.keyIndex}, signature=${sig.signature.take(20)}...")
    }
    result.envelopeSignatures.forEachIndexed { index, sig ->
        logd(TAG, "  - Envelope sig $index: address=${sig.address}, keyIndex=${sig.keyIndex}, signature=${sig.signature.take(20)}...")
    }
    
    return result
}

suspend fun Transaction.addFreeGasEnvelope(): Transaction {
    val signable = buildPayerSignable()
    logd(TAG, "Building payer signable: $signable")
    val response = executeHttpFunction(FUNCTION_SIGN_AS_PAYER, signable)
    logd(TAG, "Received envelope signature response: $response")

    val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs
    logd(TAG, "Parsed envelope signature: $sign")

    // Create envelope signature without signerIndex (following Flow KMM pattern)
    val newEnvelopeSignature = TransactionSignature(
        address = sign.address,
        keyIndex = sign.keyId,
        signature = sign.sig
        // signerIndex defaults to -1 but won't be serialized if we use kotlinx.serialization
    )
    logd(TAG, "Created new envelope signature: $newEnvelopeSignature")

    return copy(envelopeSignatures = envelopeSignatures + newEnvelopeSignature)
}

suspend fun Transaction.addFreeBridgeFeeEnvelope(): Transaction {
    val response = executeHttpFunction(FUNCTION_SIGN_AS_BRIDGE_PAYER, buildBridgeFeePayerSignable(), BASE_HOST)
    logd(TAG, "response:$response")

    val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs

    // Create envelope signature without signerIndex (following Flow KMM pattern)
    val newEnvelopeSignature = TransactionSignature(
        address = sign.address,
        keyIndex = sign.keyId,
        signature = sign.sig
        // signerIndex defaults to -1 but won't be serialized if we use kotlinx.serialization
    )

    return copy(envelopeSignatures = envelopeSignatures + newEnvelopeSignature)
}
