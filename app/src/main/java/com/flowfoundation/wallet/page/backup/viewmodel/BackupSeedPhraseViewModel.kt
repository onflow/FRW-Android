package com.flowfoundation.wallet.page.backup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.AccountSyncRequest
import com.flowfoundation.wallet.network.model.BackupInfoRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupSeedPhraseOption
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.error.BackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import org.onflow.flow.models.TransactionStatus
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.logd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.onflow.flow.infrastructure.Cadence.Companion.uint8
import java.io.File
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi

class BackupSeedPhraseViewModel: ViewModel(), OnTransactionStateChange {

    val optionChangeLiveData = MutableLiveData<BackupSeedPhraseOption>()

    val createBackupCallbackLiveData = MutableLiveData<Boolean>()

    val mnemonicListLiveData = MutableLiveData<List<MnemonicModel>>()

    // For backup, we need to generate a NEW crypto provider from the stored mnemonic
    private val backupCryptoProvider: HDWalletCryptoProvider? by lazy {
        try {
            val globalMnemonic = Wallet.store().mnemonic()
            logd("BackupSeedPhraseVM", "Attempting to create backup crypto provider from global mnemonic")
            logd("BackupSeedPhraseVM", "Global mnemonic available: ${globalMnemonic.isNotBlank()}")
            if (globalMnemonic.isNotBlank()) {
                logd("BackupSeedPhraseVM", "Creating SeedPhraseKey with mnemonic length: ${globalMnemonic.split(" ").size} words")
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = globalMnemonic,
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = FileSystemStorage(baseDir)
                )
                logd("BackupSeedPhraseVM", "SeedPhraseKey created successfully")
                val provider = HDWalletCryptoProvider(seedPhraseKey)
                logd("BackupSeedPhraseVM", "HDWalletCryptoProvider created successfully")
                logd("BackupSeedPhraseVM", "Backup provider public key: ${provider.getPublicKey()}")
                provider
            } else {
                logd("BackupSeedPhraseVM", "No global mnemonic available for backup")
                null
            }
        } catch (e: Exception) {
            logd("BackupSeedPhraseVM", "Failed to create backup crypto provider: ${e.message}")
            logd("BackupSeedPhraseVM", "Stack trace: ${e.stackTraceToString()}")
            null
        }
    }

    private var currentTxId: String? = null

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun changeOption(option: BackupSeedPhraseOption) {
        optionChangeLiveData.postValue(option)
    }

    fun getMnemonic(): String {
        // Always get mnemonic from global storage for display
        return try {
            val globalMnemonic = Wallet.store().mnemonic()
            if (globalMnemonic.isNotBlank()) {
                globalMnemonic
            } else {
                ""
            }
        } catch (e: Exception) {
            logd("BackupSeedPhraseVM", "Failed to get global mnemonic: ${e.message}")
            ""
        }
    }

    fun loadMnemonic() {
        ioScope {
            val str = getMnemonic()

            withContext(Dispatchers.Main) {
                val list = str.split(" ").mapIndexed { index, s -> MnemonicModel(index + 1, s) }
                val result = mutableListOf<MnemonicModel>()
                (0 until list.size / 2).forEach { i ->
                    result.add(list[i])
                    result.add(list[i + list.size / 2])
                }
                mnemonicListLiveData.value = result
            }
        }
    }

    fun copyMnemonic() {
        val mnemonic = getMnemonic()

        textToClipboard(mnemonic)
        toast(R.string.copied_to_clipboard)
    }

    fun uploadToChain() {
        val backupProvider = backupCryptoProvider
        logd("BackupSeedPhraseVM", "uploadToChain() called")
        logd("BackupSeedPhraseVM", "Backup provider available: ${backupProvider != null}")
        if (backupProvider == null) {
            logd("BackupSeedPhraseVM", "Backup provider is null, cannot proceed with backup")
            createBackupCallbackLiveData.postValue(false)
            return
        }
        
        logd("BackupSeedPhraseVM", "Backup provider public key: ${backupProvider.getPublicKey()}")
        logd("BackupSeedPhraseVM", "Backup provider signature algo: ${backupProvider.getSignatureAlgorithm()}")
        logd("BackupSeedPhraseVM", "Backup provider hash algo: ${backupProvider.getHashAlgorithm()}")
        
        // Debug WalletManager state
        logd("BackupSeedPhraseVM", "=== WALLET MANAGER DEBUG ===")
        val wallet = WalletManager.wallet()
        logd("BackupSeedPhraseVM", "WalletManager.wallet(): $wallet")
        logd("BackupSeedPhraseVM", "WalletManager.wallet() is null: ${wallet == null}")
        if (wallet != null) {
            val walletAddress = wallet.walletAddress()
            logd("BackupSeedPhraseVM", "wallet.walletAddress(): '$walletAddress'")
            logd("BackupSeedPhraseVM", "wallet.accounts: ${wallet.accounts}")
            logd("BackupSeedPhraseVM", "wallet.accounts.keys: ${wallet.accounts.keys}")
            logd("BackupSeedPhraseVM", "wallet.accounts.values: ${wallet.accounts.values}")
        }
        val selectedAddress = WalletManager.selectedWalletAddress()
        logd("BackupSeedPhraseVM", "WalletManager.selectedWalletAddress(): '$selectedAddress'")
        
        val account = AccountManager.get()
        logd("BackupSeedPhraseVM", "AccountManager.get(): $account")
        if (account != null) {
            logd("BackupSeedPhraseVM", "account.wallet: ${account.wallet}")
            logd("BackupSeedPhraseVM", "account.wallet?.walletAddress(): ${account.wallet?.walletAddress()}")
            logd("BackupSeedPhraseVM", "account.prefix: ${account.prefix}")
            logd("BackupSeedPhraseVM", "account.userInfo: ${account.userInfo}")
        }
        
        // Debug current account's crypto provider
        logd("BackupSeedPhraseVM", "=== CURRENT ACCOUNT CRYPTO PROVIDER DEBUG ===")
        val currentCryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        logd("BackupSeedPhraseVM", "Current crypto provider: $currentCryptoProvider")
        logd("BackupSeedPhraseVM", "Current crypto provider type: ${currentCryptoProvider?.javaClass?.simpleName}")
        if (currentCryptoProvider != null) {
            logd("BackupSeedPhraseVM", "Current crypto provider public key: ${currentCryptoProvider.getPublicKey()}")
            logd("BackupSeedPhraseVM", "Current crypto provider signature algo: ${currentCryptoProvider.getSignatureAlgorithm()}")
            logd("BackupSeedPhraseVM", "Current crypto provider hash algo: ${currentCryptoProvider.getHashAlgorithm()}")
            logd("BackupSeedPhraseVM", "Current crypto provider key weight: ${currentCryptoProvider.getKeyWeight()}")
        }
        
        // Debug crypto provider generation step by step
        logd("BackupSeedPhraseVM", "=== CRYPTO PROVIDER GENERATION DEBUG ===")
        if (account != null) {
            logd("BackupSeedPhraseVM", "Testing generateAccountCryptoProvider with current account:")
            logd("BackupSeedPhraseVM", "  account.keyStoreInfo.isNullOrBlank(): ${account.keyStoreInfo.isNullOrBlank()}")
            logd("BackupSeedPhraseVM", "  account.prefix.isNullOrBlank(): ${account.prefix.isNullOrBlank()}")
            logd("BackupSeedPhraseVM", "  account.isActive: ${account.isActive}")
            
            val testCryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(account)
            logd("BackupSeedPhraseVM", "  Generated crypto provider: $testCryptoProvider")
            logd("BackupSeedPhraseVM", "  Generated crypto provider type: ${testCryptoProvider?.javaClass?.simpleName}")
            
            // Test global mnemonic availability for active accounts
            if (account.isActive) {
                try {
                    val globalMnemonic = Wallet.store().mnemonic()
                    logd("BackupSeedPhraseVM", "  Global mnemonic for active account: '$globalMnemonic'")
                    logd("BackupSeedPhraseVM", "  Global mnemonic length: ${globalMnemonic.split(" ")?.size} words")
                } catch (e: Exception) {
                    logd("BackupSeedPhraseVM", "  Error getting global mnemonic: ${e.message}")
                }
            }
        }
        logd("BackupSeedPhraseVM", "=== END CRYPTO PROVIDER GENERATION DEBUG ===")
        logd("BackupSeedPhraseVM", "=== END CRYPTO PROVIDER DEBUG ===")
        
        // Test key matching logic with actual keys
        logd("BackupSeedPhraseVM", "=== KEY MATCHING TEST ===")
        val testCurrentKey = "0x0480cfba8f0465a25d5a26e9d171281f23a723d3c5b12049cfe3a1ea515c6a5594b38201a5ad3c36f26438556a98875e36b8295cbbd6056101ab208e8bc416199a"
        val testOnChainKey = "80cfba8f0465a25d5a26e9d171281f23a723d3c5b12049cfe3a1ea515c6a5594b38201a5ad3c36f26438556a98875e36b8295cbbd6056101ab208e8bc416199a"
        
        // Apply the same logic as findMatchingAccountKey
        val pubRaw = testCurrentKey.removePrefix("0x").lowercase()
        val pubStripped = if (pubRaw.startsWith("04") && pubRaw.length == 130) pubRaw.substring(2) else pubRaw
        val accPubRaw = testOnChainKey.removePrefix("0x").lowercase()
        
        logd("BackupSeedPhraseVM", "Test current key: $testCurrentKey")
        logd("BackupSeedPhraseVM", "Test on-chain key: $testOnChainKey")
        logd("BackupSeedPhraseVM", "Current key (raw): $pubRaw")
        logd("BackupSeedPhraseVM", "Current key (stripped): $pubStripped")
        logd("BackupSeedPhraseVM", "On-chain key (raw): $accPubRaw")
        logd("BackupSeedPhraseVM", "Match check: accPubRaw == pubRaw: ${accPubRaw == pubRaw}")
        logd("BackupSeedPhraseVM", "Match check: accPubRaw == pubStripped: ${accPubRaw == pubStripped}")
        logd("BackupSeedPhraseVM", "Should match: ${accPubRaw == pubRaw || accPubRaw == pubStripped}")
        logd("BackupSeedPhraseVM", "=== END KEY MATCHING TEST ===")
        
        ioScope {
            try {
                withTimeout(45000) { // 45 second timeout (longer than Flow's 30s to catch timeouts)
                    try {
                        logd("BackupSeedPhraseVM", "Starting transaction to add backup key")
                        
                        // Debug on-chain account keys first
                        logd("BackupSeedPhraseVM", "=== ON-CHAIN ACCOUNT KEYS DEBUG ===")
                        val walletAddress = WalletManager.selectedWalletAddress()
                        if (currentCryptoProvider != null) {
                            try {
                                val onChainAccount = FlowCadenceApi.getAccount(walletAddress)
                                logd("BackupSeedPhraseVM", "On-chain account: ${onChainAccount.address}")
                                logd("BackupSeedPhraseVM", "On-chain account keys count: ${onChainAccount.keys?.size ?: 0}")
                                onChainAccount.keys?.forEachIndexed { index, key ->
                                    logd("BackupSeedPhraseVM", "Key $index: index=${key.index}, publicKey=${key.publicKey}, signAlgo=${key.signingAlgorithm}, hashAlgo=${key.hashingAlgorithm}, weight=${key.weight}, revoked=${key.revoked}, seqNum=${key.sequenceNumber}")
                                }
                                
                                // Check if current provider key matches any on-chain key
                                val currentProviderPubKey = currentCryptoProvider.getPublicKey()
                                val currentProviderPubKeyRaw = currentProviderPubKey.removePrefix("0x").lowercase()
                                val currentProviderPubKeyStripped = if (currentProviderPubKeyRaw.startsWith("04") && currentProviderPubKeyRaw.length == 130) {
                                    currentProviderPubKeyRaw.substring(2)
                                } else {
                                    currentProviderPubKeyRaw
                                }
                                
                                logd("BackupSeedPhraseVM", "Current provider public key: $currentProviderPubKey")
                                logd("BackupSeedPhraseVM", "Current provider public key (raw): $currentProviderPubKeyRaw")
                                logd("BackupSeedPhraseVM", "Current provider public key (stripped): $currentProviderPubKeyStripped")
                                
                                var foundMatch = false
                                onChainAccount.keys?.forEach { key ->
                                    val onChainPubKeyRaw = key.publicKey.removePrefix("0x").lowercase()
                                    val onChainPubKeyStripped = if (onChainPubKeyRaw.startsWith("04") && onChainPubKeyRaw.length == 130) {
                                        onChainPubKeyRaw.substring(2)
                                    } else {
                                        onChainPubKeyRaw
                                    }
                                    
                                    val isDirectMatch = onChainPubKeyRaw == currentProviderPubKeyRaw
                                    val isStrippedMatch = onChainPubKeyRaw == currentProviderPubKeyStripped
                                    val isProviderStrippedMatch = onChainPubKeyStripped == currentProviderPubKeyRaw
                                    val isBothStrippedMatch = onChainPubKeyStripped == currentProviderPubKeyStripped
                                    
                                    if (isDirectMatch || isStrippedMatch || isProviderStrippedMatch || isBothStrippedMatch) {
                                        foundMatch = true
                                        logd("BackupSeedPhraseVM", "MATCH FOUND! Key index ${key.index} matches current provider")
                                        logd("BackupSeedPhraseVM", "  On-chain key: ${key.publicKey}")
                                        logd("BackupSeedPhraseVM", "  Match type: direct=$isDirectMatch, stripped=$isStrippedMatch, providerStripped=$isProviderStrippedMatch, bothStripped=$isBothStrippedMatch")
                                    } else {
                                        logd("BackupSeedPhraseVM", "No match for key index ${key.index}")
                                        logd("BackupSeedPhraseVM", "  On-chain key (raw): $onChainPubKeyRaw")
                                        logd("BackupSeedPhraseVM", "  On-chain key (stripped): $onChainPubKeyStripped")
                                    }
                                }
                                
                                if (!foundMatch) {
                                    logd("BackupSeedPhraseVM", "CRITICAL: No matching on-chain key found for current crypto provider!")
                                    logd("BackupSeedPhraseVM", "This explains the invalid signature error - the current provider key is not on the account")
                                }
                            } catch (e: Exception) {
                                logd("BackupSeedPhraseVM", "Error fetching on-chain account keys: ${e.message}")
                            }
                        } else {
                            logd("BackupSeedPhraseVM", "Cannot debug on-chain keys: walletAddress=$walletAddress, currentCryptoProvider=$currentCryptoProvider")
                        }
                        logd("BackupSeedPhraseVM", "=== END ON-CHAIN ACCOUNT KEYS DEBUG ===")
                        
                        val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                            val newPubKeyWithPrefix = backupProvider.getPublicKey() // e.g., "0x04..."
                            val newPubKeyHexRaw = newPubKeyWithPrefix.removePrefix("0x")
                            
                            // Flow's Cadence addKey script expects the publicKey string argument to be the
                            // 64-byte hex representation (128 chars) WITHOUT the "04" uncompressed prefix.
                            val newPubKeyForCadence = if (newPubKeyHexRaw.startsWith("04") && newPubKeyHexRaw.length == 130) {
                                newPubKeyHexRaw.substring(2)
                            } else {
                                newPubKeyHexRaw
                            }
                            logd("BackupSeedPhraseVM", "Original new public key: $newPubKeyWithPrefix")
                            logd("BackupSeedPhraseVM", "Stripped public key for Cadence: $newPubKeyForCadence")
                            logd("BackupSeedPhraseVM", "Public key length for Cadence: ${newPubKeyForCadence.length}")

                            arg { string(newPubKeyForCadence) }
                            arg { uint8(backupProvider.getSignatureAlgorithm().cadenceIndex.toUByte()) }
                            arg { uint8(backupProvider.getHashAlgorithm().cadenceIndex.toUByte()) }
                            arg { ufix64Safe(1000) }
                        }
                        logd("BackupSeedPhraseVM", "Transaction created with ID: $txId")
                        if (txId.isNullOrBlank()) {
                            // Handle case where transaction ID is null or empty
                            logd("BackupSeedPhraseVM", "Transaction ID is null or blank!")
                            ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, 
                                RuntimeException("Failed to get transaction ID"))
                            createBackupCallbackLiveData.postValue(false)
                            return@withTimeout
                        }
                        val transactionState = TransactionState(
                            transactionId = txId,
                            time = System.currentTimeMillis(),
                            state = TransactionStatus.PENDING.ordinal,
                            type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                            data = ""
                        )
                        currentTxId = txId
                        TransactionStateManager.newTransaction(transactionState)
                        pushBubbleStack(transactionState)
                        logd("BackupSeedPhraseVM", "Transaction submitted successfully: $txId")
                    } catch (e: Exception) {
                        // Reset state and notify UI of failure
                        logd("BackupSeedPhraseVM", "Transaction failed with exception: ${e.message}")
                        logd("BackupSeedPhraseVM", "Exception stack trace: ${e.stackTraceToString()}")
                        currentTxId = null
                        createBackupCallbackLiveData.postValue(false)
                        ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, e)
                        throw e
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Handle timeout specifically
                logd("BackupSeedPhraseVM", "Transaction timed out after 45 seconds")
                currentTxId = null
                createBackupCallbackLiveData.postValue(false)
                ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, 
                    RuntimeException("Backup process timed out after 45 seconds"))
                toast(R.string.backup_failed)
            }
        }
    }

    private fun syncKeyInfo() {
        val backupProvider = backupCryptoProvider
        if (backupProvider == null) {
            createBackupCallbackLiveData.postValue(false)
            return
        }
        
        ioScope {
            try {
                withTimeout(30000) { // 30 second timeout for network requests
                    try {
                        val deviceInfo = DeviceInfoManager.getDeviceInfoRequest()
                        val service = retrofit().create(ApiService::class.java)
                        val resp = service.syncAccount(
                            AccountSyncRequest(
                                AccountKey(
                                    publicKey = backupProvider.getPublicKey(),
                                    signAlgo = backupProvider.getSignatureAlgorithm().cadenceIndex,
                                    hashAlgo = backupProvider.getHashAlgorithm().cadenceIndex,
                                    weight = backupProvider.getKeyWeight()
                                ),
                                deviceInfo,
                                BackupInfoRequest(
                                    name = BackupType.FULL_WEIGHT_SEED_PHRASE.displayName,
                                    type = BackupType.FULL_WEIGHT_SEED_PHRASE.index
                                )
                            )
                        )
                        val isSuccess = resp.status == 200
                        if (!isSuccess) {
                            // Log detailed error information for debugging
                            ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, 
                                RuntimeException("Sync failed with status: ${resp.status}"))
                        }
                        createBackupCallbackLiveData.postValue(isSuccess)
                    } catch (e: Exception) {
                        ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, e)
                        createBackupCallbackLiveData.postValue(false)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Handle timeout for network sync
                ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, 
                    RuntimeException("Account sync timed out after 30 seconds"))
                createBackupCallbackLiveData.postValue(false)
            }
        }
    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_ADD_PUBLIC_KEY }
        transaction?.let { state ->
            if (currentTxId == state.transactionId) {
                if (state.isSuccess()) {
                    currentTxId = null
                    syncKeyInfo()
                } else if (state.isFailed()) {
                    // Handle failed transactions to prevent infinite loading
                    currentTxId = null
                    createBackupCallbackLiveData.postValue(false)
                    ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, 
                        RuntimeException("Transaction failed: ${state.errorMsg}"))
                }
            }
        }
    }
}