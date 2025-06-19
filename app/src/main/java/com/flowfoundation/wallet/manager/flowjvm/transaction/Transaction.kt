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
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.reportCadenceErrorToDebugView
import com.flowfoundation.wallet.utils.safeRunSuspend
import com.flowfoundation.wallet.utils.vibrateTransaction
import com.flowfoundation.wallet.wallet.toAddress
import com.instabug.library.Instabug
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import org.onflow.flow.models.*
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.getFlowAddress
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import org.onflow.flow.infrastructure.getTypeName
import org.onflow.flow.infrastructure.removeHexPrefix
import com.flowfoundation.wallet.manager.key.MultiRestoreCryptoProvider

private const val TAG = "Transaction"

suspend fun sendTransaction(
    builder: TransactionBuilder.() -> Unit,
): String? {
    val transactionBuilder = TransactionBuilder().apply { builder(this) }

    try {
        logd(TAG, "sendTransaction prepare")
        
        // Check if this account requires multi-signature (multi-restore account)
        val walletAddress = transactionBuilder.walletAddress?.toAddress() 
            ?: throw RuntimeException("No wallet address specified")
        
        val currentNetworkName = chainNetWorkString()
        
        // Find local account instance (same logic as in prepare function)
        val localAccountInstance = AccountManager.list().find { acc ->
            val accFlowAddress = acc.getFlowAddress(currentNetworkName, TAG)?.toAddress()
            accFlowAddress == walletAddress
        } ?: throw RuntimeException("Could not find local Account instance for address $walletAddress on network $currentNetworkName.")
            
        val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(localAccountInstance)
            ?: throw RuntimeException("Could not generate CryptoProvider for local account ${localAccountInstance.userInfo.username}")

        // Check if this is a multi-restore account that needs multi-signature
        if (cryptoProvider is MultiRestoreCryptoProvider) {
            logd(TAG, "Detected multi-restore account with ${cryptoProvider.getAllProviders().size} providers (total weight: ${cryptoProvider.getKeyWeight()})")
            logd(TAG, "Routing to multi-signature transaction flow")
            
            // Use multi-signature transaction flow for multi-restore accounts
            return sendTransactionWithMultiSignature(
                providers = cryptoProvider.getAllProviders(),
                builder = builder // Pass the original builder
            )
        }

        // Use single-signature transaction flow for regular accounts
        logd(TAG, "Using single-signature transaction flow for regular account")
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

        logd(TAG, "sendTransaction to flow chain")
        val txID = tx.submitOnly() // Use submitOnly to get ID immediately
        logd(TAG, "transaction id:$${txID}")
        vibrateTransaction()

        ioScope {
            safeRunSuspend {
                try {
                    val txResult = FlowCadenceApi.waitForSeal(txID)
                    val isSuccess = when (txResult.status) {
                        TransactionStatus.FINALIZED -> txResult.execution == TransactionExecution.success
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
                } catch (e: Exception) {
                    logd(TAG, "Transaction monitoring failed for $txID: ${e.message}")
                    MixpanelManager.cadenceTransactionSigned(
                        cadence = tx.script,
                        txId = txID,
                        authorizers = tx.authorizers,
                        proposer = tx.proposalKey.address,
                        payer = tx.payer,
                        isSuccess = false
                    )
                }
            }
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
        val txID = tx.submitOnly() // Use submitOnly to get ID immediately
        logd(TAG, "transaction id:$${txID}")
        vibrateTransaction()

        ioScope {
            safeRunSuspend {
                try {
                    val txResult = FlowCadenceApi.waitForSeal(txID)
                    val isSuccess = when (txResult.status) {
                        TransactionStatus.FINALIZED -> txResult.execution == TransactionExecution.success
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
                } catch (e: Exception) {
                    logd(TAG, "Bridge transaction monitoring failed for $txID: ${e.message}")
                    MixpanelManager.cadenceTransactionSigned(
                        cadence = tx.script,
                        txId = txID,
                        authorizers = tx.authorizers,
                        proposer = tx.proposalKey.address,
                        payer = tx.payer,
                        isSuccess = false
                    )
                }
            }
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

    // Use KMM's buildAndSign() which handles payload and envelope signatures properly
    var tx = prepareAndSignWithMultiSignature(
        appBuilder = appBuilder,
        providers = providers
    )

    // If gas is free (external payer), add the free gas envelope signature
    if (isGasFree()) {
        val expectedFreeGasPayer = AppConfig.payer().address.removeHexPrefix()
        if (tx.payer.removeHexPrefix() == expectedFreeGasPayer) {
            logd(TAG, "sendTransactionWithMultiSignature: Adding free gas envelope as payer is external.")
            tx = tx.addFreeGasEnvelope()
        } else {
            logd(TAG, "sendTransactionWithMultiSignature: Gas is free, but tx.payer (${tx.payer}) doesn't match AppConfig payer ($expectedFreeGasPayer). Skipping addFreeGasEnvelope.")
        }
    }

    logd(TAG, "sendTransactionWithMultiSignature to flow chain")
    val txID = tx.submitOnly() // Use submitOnly to get ID immediately
    logd(TAG, "transaction id:$${txID}")
    vibrateTransaction()

    ioScope {
        safeRunSuspend {
            try {
                val txResult = FlowCadenceApi.waitForSeal(txID)
                val isSuccess = when (txResult.status) {
                    TransactionStatus.FINALIZED -> txResult.execution == TransactionExecution.success
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
            } catch (e: Exception) {
                logd(TAG, "Multi-signature transaction monitoring failed for $txID: ${e.message}")
                MixpanelManager.cadenceTransactionSigned(
                    cadence = tx.script,
                    txId = txID,
                    authorizers = tx.authorizers,
                    proposer = tx.proposalKey.address,
                    payer = tx.payer,
                    isSuccess = false
                )
            }
        }
    }

    return txID
}

suspend fun prepareAndSignWithMultiSignature(
    appBuilder: TransactionBuilder,
    providers: List<CryptoProvider>
): Transaction {
    logd(TAG, "prepareAndSignWithMultiSignature builder: $appBuilder")

    val proposerAddress = appBuilder.walletAddress?.toAddress()
        ?: throw IllegalArgumentException("Wallet address (proposer) is required for multi-signature.")
    
    val flowAccount = FlowCadenceApi.getAccount(proposerAddress)
    val accountKeys = flowAccount.keys?.toList() 
        ?: throw InvalidKeyException("On-chain account $proposerAddress has no keys")

    // Find the proposal key using the first provider
    val firstProviderPublicKey = providers.firstOrNull()?.getPublicKey()?.ensureHexFormat()
        ?: throw IllegalArgumentException("At least one crypto provider is required for multi-signature proposal key selection.")

    val designatedProposalKey = findMatchingAccountKey(accountKeys, firstProviderPublicKey, proposerAddress)
    
    logd(TAG, "Designated proposal key for multi-sig: index ${designatedProposalKey.index}, seqNo ${designatedProposalKey.sequenceNumber}")

    // Create KMM signers for all providers
    val kmmSigners = providers.map { cryptoProviderInstance ->
        createKMMSigner(cryptoProviderInstance, accountKeys, proposerAddress)
    }.distinctBy { "${it.address}-${it.keyIndex}" }

    if (kmmSigners.isEmpty()) {
        throw InvalidKeyException("None of the provided crypto providers have a matching key on account $proposerAddress.")
    }

    val actualPayerAddress = (appBuilder.payer?.removeHexPrefix()
        ?: (if (isGasFree()) AppConfig.payer().address.removeHexPrefix() else proposerAddress.removeHexPrefix()))

    val authorizers = determineAuthorizers(appBuilder, proposerAddress, actualPayerAddress)

    logd(TAG, "prepareAndSignWithMultiSignature: proposer=$proposerAddress, payer=$actualPayerAddress, authorizers=$authorizers")
    logd(TAG, "prepareAndSignWithMultiSignature: KMM Signers for buildAndSign (${kmmSigners.size}): ${kmmSigners.joinToString { it.address + "-" + it.keyIndex }}")

    // Use Flow KMM's TransactionBuilder and its buildAndSign() method
    return TransactionBuilder(
        script = appBuilder.script.orEmpty(),
        arguments = appBuilder.arguments,
        gasLimit = BigInteger.fromLong(appBuilder.limit?.toLong() ?: 9999L)
    ).apply {
        withReferenceBlockId(FlowCadenceApi.getBlockHeader(null).id.removeHexPrefix())
        withPayer(actualPayerAddress)
        withProposalKey(
            address = proposerAddress.removeHexPrefix(),
            keyIndex = designatedProposalKey.index.toInt(),
            sequenceNumber = BigInteger.fromLong(designatedProposalKey.sequenceNumber.toLong())
        )
        withAuthorizers(authorizers)
        withSigners(kmmSigners)
    }.buildAndSign()
}

private fun findMatchingAccountKey(accountKeys: List<AccountPublicKey>, publicKey: String, address: String): AccountPublicKey {
    val pubRaw = publicKey.removeHexPrefix().lowercase()
    val pubStripped = if (pubRaw.startsWith("04") && pubRaw.length == 130) pubRaw.substring(2) else pubRaw
    val pubWith04 = if (!pubRaw.startsWith("04") && pubRaw.length == 128) "04$pubRaw" else pubRaw
    
    logd(TAG, "findMatchingAccountKey: Looking for key match on account $address")
    logd(TAG, "  Provider key: $publicKey -> normalized: $pubRaw")
    
    val matchingKey = accountKeys.findLast { accKey ->
        val accPubRaw = accKey.publicKey.removeHexPrefix().lowercase()
        val accPubStripped = if (accPubRaw.startsWith("04") && accPubRaw.length == 130) accPubRaw.substring(2) else accPubRaw
        
        // Try comprehensive matching for backward compatibility
        val isMatch = accPubRaw == pubRaw || accPubRaw == pubStripped || 
                     accPubStripped == pubRaw || accPubStripped == pubStripped ||
                     accPubRaw == pubWith04 || accKey.publicKey.removeHexPrefix().lowercase() == pubWith04
        
        if (isMatch) {
            logd(TAG, "  ✓ MATCH found! Key index ${accKey.index}")
        }
        
        isMatch
    }
    
    if (matchingKey == null) {
        logd(TAG, "  ✗ NO MATCH found. Available keys:")
        accountKeys.forEach { key ->
            logd(TAG, "    Index ${key.index}: ${key.publicKey}")
        }
    }
    
    return matchingKey ?: throw InvalidKeyException("Proposal key matching public key ($publicKey) not found on account $address")
}

private fun createKMMSigner(cryptoProvider: CryptoProvider, accountKeys: List<AccountPublicKey>, address: String): Signer {
    val providerPublicKey = cryptoProvider.getPublicKey().ensureHexFormat()
    val onChainKey = findMatchingAccountKey(accountKeys, providerPublicKey, address)
    
    val keyOnChainHashingAlgorithm = onChainKey.hashingAlgorithm
    logd(TAG, "Using KMM hashing algorithm ${keyOnChainHashingAlgorithm.name} for provider ${cryptoProvider.getPublicKey()} (key index ${onChainKey.index}) on account $address")
    
    val signerInstance: Signer = cryptoProvider.getSigner(keyOnChainHashingAlgorithm)
    signerInstance.address = address.removeHexPrefix()
    signerInstance.keyIndex = onChainKey.index.toInt()
    return signerInstance
}

/**
 * Checks if a provider public key matches an on-chain account key
 * Enhanced for backward compatibility with different public key formats
 */
private fun isKeyMatch(providerPublicKey: String, onChainPublicKey: String): Boolean {
    val providerRaw = providerPublicKey.removeHexPrefix().lowercase()
    val onChainRaw = onChainPublicKey.removeHexPrefix().lowercase()
    
    // Handle both compressed and uncompressed EC keys with comprehensive format matching
    val providerStripped = if (providerRaw.startsWith("04") && providerRaw.length == 130) providerRaw.substring(2) else providerRaw
    val onChainStripped = if (onChainRaw.startsWith("04") && onChainRaw.length == 130) onChainRaw.substring(2) else onChainRaw
    val providerWith04 = if (!providerRaw.startsWith("04") && providerRaw.length == 128) "04$providerRaw" else providerRaw
    val onChainWith04 = if (!onChainRaw.startsWith("04") && onChainRaw.length == 128) "04$onChainRaw" else onChainRaw
    
    // Try all possible combinations for maximum backward compatibility
    return onChainRaw == providerRaw || 
           onChainRaw == providerStripped || 
           onChainStripped == providerRaw || 
           onChainStripped == providerStripped ||
           onChainRaw == providerWith04 ||
           onChainWith04 == providerRaw ||
           onChainWith04 == providerStripped ||
           onChainStripped == providerWith04
}

private fun determineAuthorizers(appBuilder: TransactionBuilder, proposerAddress: String, payerAddress: String): List<String> {
    return when {
        appBuilder.isBridgePayer -> listOf(proposerAddress.removeHexPrefix(), payerAddress)
        appBuilder.authorizers.isNullOrEmpty() -> listOf(proposerAddress.removeHexPrefix())
        else -> appBuilder.authorizers?.map { it.removeHexPrefix() } ?: listOf(proposerAddress.removeHexPrefix())
    }.distinct()
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun Transaction.send(): Transaction {
    logd(TAG, "Sending transaction: $this")

    val submittedTxId: String = try {
        val responseTransaction = FlowCadenceApi.sendTransaction(this)
        responseTransaction.id ?: throw RuntimeException("Transaction ID was null in response from sendTransaction. Full response: $responseTransaction")
    } catch (e: kotlinx.serialization.MissingFieldException) {
        logd(TAG, "MissingFieldException while parsing sendTransaction response. This indicates KMM SDK may not expect the Access Node's response format for POST /transactions.")
        val rawErrorMessage = e.cause?.message ?: e.message ?: "Unknown error during sendTransaction response parsing"

        if (rawErrorMessage.contains(""""code": 400""") && rawErrorMessage.contains("invalid signature")) {
            throw RuntimeException("Flow Access Node rejected transaction: Invalid Signature. Raw response: $rawErrorMessage", e)
        }
        
        loge(TAG, "Transaction submission status uncertain due to response parsing error. Raw error: $rawErrorMessage")
        throw RuntimeException("Failed to parse Flow Access Node response after sending transaction. The transaction may or may not have been processed. Raw error: $rawErrorMessage", e)
        
    } catch (e: RuntimeException) {
        if (e.message?.contains("Invalid Flow argument: invalid transaction: invalid signature") == true) {
            logd(TAG, "Transaction rejected by Flow Access Node: Invalid Signature. Details: ${e.message}")
            throw RuntimeException("Flow Access Node rejected transaction: Invalid Signature. Details: ${e.message}", e)
        }
        throw e
    }

    logd(TAG, "Transaction submitted with ID: $submittedTxId")

    // Wait for seal using the extracted ID with improved error handling
    val seal = try {
        FlowCadenceApi.waitForSeal(submittedTxId)
    } catch (e: kotlinx.serialization.SerializationException) {
        // Handle JSON deserialization errors from Flow SDK
        val errorMessage = e.message ?: "Unknown serialization error"
        logd(TAG, "Transaction result parsing failed due to JSON deserialization error: $errorMessage")
        logd(TAG, "Transaction was successfully submitted (ID: $submittedTxId) but result parsing failed.")
        logd(TAG, "This is likely a Flow SDK issue with parsing complex transaction result JSON.")
        
        // Return a mock sealed result since the transaction was submitted successfully
        // The user can check the transaction status on FlowScan using the transaction ID
        TransactionResult(
            blockId = "",
            status = org.onflow.flow.models.TransactionStatus.SEALED,
            statusCode = 0,
            errorMessage = "Result parsing failed due to Flow SDK JSON deserialization issue. Transaction was submitted successfully. Check status on FlowScan.",
            computationUsed = "0",
            events = emptyList(),
            execution = org.onflow.flow.models.TransactionExecution.success,
            links = null
        )
    } catch (e: RuntimeException) {
        if (e.message?.contains("Illegal input: Expected JsonPrimitive") == true || 
            e.message?.contains("serialization") == true ||
            e.message?.contains("deserialization") == true) {
            // Handle Flow SDK JSON parsing errors
            logd(TAG, "Transaction result parsing failed due to Flow SDK JSON parsing error: ${e.message}")
            logd(TAG, "Transaction was successfully submitted (ID: $submittedTxId) but result parsing failed.")
            
            // Return a mock sealed result since the transaction was submitted successfully
            TransactionResult(
                blockId = "",
                status = org.onflow.flow.models.TransactionStatus.SEALED,
                statusCode = 0,
                errorMessage = "Result parsing failed due to Flow SDK JSON parsing issue. Transaction was submitted successfully. Check status on FlowScan.",
                computationUsed = "0",
                events = emptyList(),
                execution = org.onflow.flow.models.TransactionExecution.success,
                links = null
            )
        } else {
            // Re-throw other runtime exceptions
            throw e
        }
    }
    
    logd(TAG, "Transaction sealed. Status=${seal.status}, Execution=${seal.execution}")

    // Only try to fetch full transaction if seal parsing succeeded
    val fullTx = if (seal.errorMessage.contains("Flow SDK JSON")) {
        // Don't try to fetch full transaction if we know there are JSON parsing issues
        logd(TAG, "Skipping full transaction fetch due to known JSON parsing issues")
        this.copy(id = submittedTxId, result = seal)
    } else {
        try {
            logd(TAG, "Fetching full transaction $submittedTxId after seal")
            val fetchedTx = FlowCadenceApi.getTransaction(submittedTxId)
            logd(TAG, "Retrieved full transaction: $fetchedTx")
            fetchedTx
        } catch (e: Exception) {
            logd(TAG, "Failed to fetch full transaction details, but transaction was successful: ${e.message}")
            // Return the original transaction with the ID and result
            this.copy(id = submittedTxId, result = seal)
        }
    }
    
    return fullTx
}

suspend fun Transaction.addLocalEnvelopeSignatures(): Transaction {
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        ?: throw RuntimeException("Current crypto provider is null for local envelope signing.")

    val signingAddress = this.payer
    val keyIndexForSigning: Int
    val hashingAlgorithmForSigning: HashingAlgorithm

    logd(TAG, "addLocalEnvelopeSignatures: Tx Payer=${this.payer}, Tx ProposalAddress=${this.proposalKey.address}, Tx ProposalKeyIndex=${this.proposalKey.keyIndex}")

    // Check if the payer of this transaction is also its proposer
    if (this.payer.removeHexPrefix().equals(this.proposalKey.address.removeHexPrefix(), ignoreCase = true)) {
        // Payer is the Proposer - use the proposal key for envelope signature
        keyIndexForSigning = this.proposalKey.keyIndex
        logd(TAG, "Payer is Proposer. Using tx.proposalKey.keyIndex ($keyIndexForSigning) for envelope signature.")

        val payerAccount = FlowCadenceApi.getAccount(this.payer)
        val payerAccountKeys = payerAccount.keys?.toList()
            ?: throw InvalidKeyException("Payer account ${this.payer} has no keys for proposal key verification.")

        val targetProposalKeyOnAccount = payerAccountKeys.find { it.index.toInt() == keyIndexForSigning }
            ?: throw InvalidKeyException("Transaction's proposal key (index $keyIndexForSigning) not found on payer/proposer account ${this.payer}.")

        // Verify the current crypto provider matches the proposal key
        val providerPublicKey = cryptoProvider.getPublicKey().ensureHexFormat()
        if (!isKeyMatch(providerPublicKey, targetProposalKeyOnAccount.publicKey)) {
            throw InvalidKeyException("Current crypto provider does not match the public key of the transaction's proposal key.")
        }
        
        hashingAlgorithmForSigning = targetProposalKeyOnAccount.hashingAlgorithm
        logd(TAG, "Verified current provider can sign for tx.proposalKey. Using on-chain hashing algorithm: ${hashingAlgorithmForSigning.name}")

    } else {
        // Payer is different from Proposer - find the provider's key on the payer account
        logd(TAG, "Payer (${this.payer}) is different from Proposer (${this.proposalKey.address}) in addLocalEnvelopeSignatures.")
        
        val externalPayerAccount = FlowCadenceApi.getAccount(this.payer)
        val externalPayerAccountKeys = externalPayerAccount.keys?.toList() 
            ?: throw InvalidKeyException("Specified payer account ${this.payer} has no keys.")
        
        val providerPublicKey = cryptoProvider.getPublicKey().ensureHexFormat()
        val currentLocalProvidersKeyOnExternalPayerAccount = externalPayerAccountKeys.findLast { accKey ->
            isKeyMatch(providerPublicKey, accKey.publicKey)
        } ?: throw InvalidKeyException("Current local crypto provider's key ($providerPublicKey) not found on the specified (external) payer account ${this.payer}.")

        keyIndexForSigning = currentLocalProvidersKeyOnExternalPayerAccount.index.toInt()
        hashingAlgorithmForSigning = currentLocalProvidersKeyOnExternalPayerAccount.hashingAlgorithm
        logd(TAG, "Found current local provider's key on the specified payer account ${this.payer}: keyIndex=$keyIndexForSigning, hashAlgo=${hashingAlgorithmForSigning.name}")
    }

    logd(TAG, "Final KMM Signer for local envelope: address=${signingAddress.removeHexPrefix()}, keyIndex=$keyIndexForSigning, hashAlgo=${hashingAlgorithmForSigning.name}")

    val kmmSigner: Signer = cryptoProvider.getSigner(hashingAlgorithmForSigning).apply {
        this.address = signingAddress.removeHexPrefix()
        this.keyIndex = keyIndexForSigning
    }

    // Use KMM's addEnvelopeSignature method instead of manually signing
    return this.addEnvelopeSignature(signingAddress.removeHexPrefix(), keyIndexForSigning, kmmSigner)
}

suspend fun Transaction.buildPayerSignable(): PayerSignable? {
    val payerAccount = FlowCadenceApi.getAccount(payer)
    payerAccount.keys ?: return null

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
            formattedTx.envelopeMessage().toHexString()
        )
    )
}

suspend fun Transaction.buildBridgeFeePayerSignable(): PayerSignable? {
    val payerAccount = FlowCadenceApi.getAccount(payer)
    payerAccount.keys ?: return null

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
            formattedTx.envelopeMessage().toHexString()
        )
    )
}

private fun String.ensureHexFormat(): String {
    return if (startsWith("0x")) this else "0x$this"
}

suspend fun prepare(builder: TransactionBuilder): Transaction {
    logd(TAG, "--- prepare() called by sendTransaction/sendBridgeTransaction ---")
    logd(TAG, "App-level builder input - scriptId: ${builder.scriptId}")
    logd(TAG, "App-level builder input - script content: [${builder.script?.take(100)?.replace("\n", "<NL>")}...]")
    logd(TAG, "App-level builder input - arguments (${builder.arguments.size}): ${builder.arguments.map { it.getTypeName() + ":" + it.value?.toString()?.take(60) }}")
    logd(TAG, "App-level builder input - walletAddress: ${builder.walletAddress}")
    logd(TAG, "App-level builder input - payer: ${builder.payer}")
    logd(TAG, "App-level builder input - limit: ${builder.limit}")

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
    val accountKeys = flowAccount.keys?.toList() ?: throw InvalidKeyException("On-chain account $walletAddress has no keys")
    logd(TAG, "On-chain keys for $walletAddress: $accountKeys")
    logd(TAG, "Provider public key from local account ${localAccountInstance.userInfo.username} (for $walletAddress): ${cryptoProvider.getPublicKey()}")
    
    val providerPublicKey = cryptoProvider.getPublicKey().ensureHexFormat()
    logd(TAG, "Normalized provider public key: $providerPublicKey")

    val currentKey = findMatchingAccountKey(accountKeys, providerPublicKey, walletAddress)
    logd(TAG, "Found matching key for $walletAddress on-chain: index ${currentKey.index}, seqNo ${currentKey.sequenceNumber}, hashAlgo: ${currentKey.hashingAlgorithm.name}")

    // Determine the correct hashing algorithm from the on-chain key
    val keyOnChainHashingAlgorithm = currentKey.hashingAlgorithm
    logd(TAG, "Using KMM hashing algorithm ${keyOnChainHashingAlgorithm.name} for key ${currentKey.index} on account $walletAddress")

    // Determine payer and authorizers
    val payer = builder.payer?.removeHexPrefix() ?: (if (isGasFree()) AppConfig.payer().address.removeHexPrefix() else builder.walletAddress?.removeHexPrefix()).orEmpty()
    val authorizers = determineAuthorizers(builder, flowAccount.address, payer)

    // Get reference block ID
    val referenceBlockId = FlowCadenceApi.getBlockHeader(null).id.removeHexPrefix()
    val script = builder.script.orEmpty()
    logd(TAG, "Cadence script for TransactionBuilder: $script")

    // Log argument encoding verification
    logd(TAG, "--- Verifying argument encoding before passing to KMM TransactionBuilder ---")
    builder.arguments.forEachIndexed { index, arg ->
        val encodedValue = try {
            arg.encode()
        } catch (e: Exception) {
            loge(TAG, "ERROR encoding argument $index value: ${arg.value}): ${e.message}")
            "ERROR_ENCODING"
        }
        logd(TAG, "Arg $index: Value=${arg.value}, EncodedForm='${encodedValue}'")
        if (encodedValue.isEmpty()) {
            logd(TAG, "CRITICAL: Argument $index encoded to an EMPTY STRING!")
        }
    }
    logd(TAG, "--- End of argument encoding verification ---")

    // Get the KMM Signer configured with the correct hashing algorithm
    logd(TAG, "Getting KMM Signer from CryptoProvider with hashing algorithm: $keyOnChainHashingAlgorithm")
    
    val kmmSigner = cryptoProvider.getSigner(keyOnChainHashingAlgorithm).apply {
        logd(TAG, "Setting signer address to: ${flowAccount.address.removeHexPrefix()}")
        this.address = flowAccount.address.removeHexPrefix()
        logd(TAG, "Setting signer keyIndex to: ${currentKey.index.toInt()}")
        this.keyIndex = currentKey.index.toInt()
        logd(TAG, "KMM Signer configured - address: $address, keyIndex: $keyIndex")
    }
    
    // Use Flow KMM's TransactionBuilder with buildAndSign() method
    logd(TAG, "Building transaction using Flow KMM TransactionBuilder and buildAndSign()")
    logd(TAG, "Pre-buildAndSign transaction details:")
    logd(TAG, "  - Script length: ${script.length} chars")
    logd(TAG, "  - Arguments count: ${builder.arguments.size}")
    logd(TAG, "  - Gas limit: ${builder.limit}")
    logd(TAG, "  - Reference block ID: $referenceBlockId")
    logd(TAG, "  - Payer: $payer")
    logd(TAG, "  - Proposal key: address=${flowAccount.address.removeHexPrefix()}, keyIndex=${currentKey.index.toInt()}, seqNum=${currentKey.sequenceNumber.toLong()}")
    logd(TAG, "  - Authorizers: $authorizers")
    logd(TAG, "  - Signer: address=${kmmSigner.address}, keyIndex=${kmmSigner.keyIndex}")
    
    val result = try {
        logd(TAG, "Calling KMM TransactionBuilder.buildAndSign()...")
        
        TransactionBuilder(
            script = script,
            arguments = builder.arguments,
            gasLimit = BigInteger.fromInt(builder.limit!!)
        ).apply {
            withReferenceBlockId(referenceBlockId)
            withPayer(payer)
            withProposalKey(
                address = flowAccount.address.removeHexPrefix(),
                keyIndex = currentKey.index.toInt(),
                sequenceNumber = BigInteger.fromLong(currentKey.sequenceNumber.toLong())
            )
            withAuthorizers(authorizers)
            withSigners(listOf(kmmSigner))
        }.buildAndSign()
        
    } catch (e: Exception) {
        loge(TAG, "ERROR in buildAndSign: ${e.javaClass.simpleName}: ${e.message}")
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

    val newEnvelopeSignature = TransactionSignature(
        address = sign.address,
        keyIndex = sign.keyId,
        signature = sign.sig
    )
    logd(TAG, "Created new envelope signature: $newEnvelopeSignature")

    return copy(envelopeSignatures = envelopeSignatures + newEnvelopeSignature)
}

suspend fun Transaction.addFreeBridgeFeeEnvelope(): Transaction {
    val response = executeHttpFunction(FUNCTION_SIGN_AS_BRIDGE_PAYER, buildBridgeFeePayerSignable(), BASE_HOST)
    logd(TAG, "response:$response")

    val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs

    val newEnvelopeSignature = TransactionSignature(
        address = sign.address,
        keyIndex = sign.keyId,
        signature = sign.sig
    )

    return copy(envelopeSignatures = envelopeSignatures + newEnvelopeSignature)
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun Transaction.submitOnly(): String {
    logd(TAG, "Submitting transaction (without waiting for seal): $this")

    val submittedTxId: String = try {
        val responseTransaction = FlowCadenceApi.sendTransaction(this)
        responseTransaction.id ?: throw RuntimeException("Transaction ID was null in response from sendTransaction. Full response: $responseTransaction")
    } catch (e: kotlinx.serialization.MissingFieldException) {
        logd(TAG, "MissingFieldException while parsing sendTransaction response. This indicates KMM SDK may not expect the Access Node's response format for POST /transactions.")
        val rawErrorMessage = e.cause?.message ?: e.message ?: "Unknown error during sendTransaction response parsing"

        if (rawErrorMessage.contains(""""code": 400""") && rawErrorMessage.contains("invalid signature")) {
            throw RuntimeException("Flow Access Node rejected transaction: Invalid Signature. Raw response: $rawErrorMessage", e)
        }
        
        loge(TAG, "Transaction submission status uncertain due to response parsing error. Raw error: $rawErrorMessage")
        throw RuntimeException("Failed to parse Flow Access Node response after sending transaction. The transaction may or may not have been processed. Raw error: $rawErrorMessage", e)
        
    } catch (e: RuntimeException) {
        if (e.message?.contains("Invalid Flow argument: invalid transaction: invalid signature") == true) {
            logd(TAG, "Transaction rejected by Flow Access Node: Invalid Signature. Details: ${e.message}")
            throw RuntimeException("Flow Access Node rejected transaction: Invalid Signature. Details: ${e.message}", e)
        }
        throw e
    }

    logd(TAG, "Transaction submitted with ID: $submittedTxId")
    return submittedTxId
}
