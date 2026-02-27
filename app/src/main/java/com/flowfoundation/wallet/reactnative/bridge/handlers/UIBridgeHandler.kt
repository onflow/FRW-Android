package com.flowfoundation.wallet.reactnative.bridge.handlers

import android.content.Intent
import android.widget.Toast
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.flowjvm.cadenceEnableToken
import com.flowfoundation.wallet.manager.flowjvm.cadenceNftEnabled
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.backup.BackupRecoveryPhraseActivity
import com.flowfoundation.wallet.page.backup.WalletBackupActivity
import com.flowfoundation.wallet.page.backup.device.CreateDeviceBackupActivity
import com.flowfoundation.wallet.page.backup.multibackup.MultiBackupActivity
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity
import com.flowfoundation.wallet.page.restore.multirestore.MultiRestoreActivity
import com.flowfoundation.wallet.page.walletrestore.WalletRestoreActivity as GoogleDriveRestoreActivity
import com.flowfoundation.wallet.page.scan.ScanBarcodeActivity
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.reactnative.bridge.QRCodeScanManager
import com.flowfoundation.wallet.reactnative.bridge.NativeScreen
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.gson.Gson
import org.onflow.flow.models.TransactionStatus

/**
 * Handler for UI-related bridge methods
 * Handles: QR scanning, closing RN view, toasts, native screen navigation
 */
class UIBridgeHandler(private val reactContext: ReactApplicationContext) {

    private val TAG = "UIBridgeHandler"

    fun scanQRCode(promise: Promise) {
        try {
            // Store the promise for later resolution
            QRCodeScanManager.setPendingPromise(promise)

            // Create intent to launch scan activity
            val intent = Intent(reactContext, ScanBarcodeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Start the activity
            reactContext.startActivity(intent)
        } catch (e: Exception) {
            uiScope {
                promise.reject("SCAN_ERROR", "Failed to start QR scanner: ${e.message}", e)
            }
        }
    }

    fun closeRN(id: String?) {
        logd(TAG, "closeRN() called - id: $id")
        try {
            val currentActivity = reactContext.currentActivity
            logd(TAG, "closeRN() - currentActivity: ${currentActivity?.javaClass?.simpleName}, isFinishing: ${currentActivity?.isFinishing}, isDestroyed: ${currentActivity?.isDestroyed}")

            if (currentActivity == null || currentActivity.isFinishing || currentActivity.isDestroyed) {
                logw(TAG, "closeRN() - Activity is null, finishing, or destroyed - skipping closeRN")
                return
            }

            // If a flowIdentifier is provided, close the screen immediately then execute the tx in background
            if (!id.isNullOrBlank()) {
                logd(TAG, "closeRN() - flowIdentifier provided, closing screen and triggering add token tx: $id")
                // Close the activity first so the user isn't waiting on the API call
                currentActivity.runOnUiThread {
                    if (!currentActivity.isFinishing && !currentActivity.isDestroyed) {
                        currentActivity.setResult(android.app.Activity.RESULT_OK)
                        currentActivity.finish()
                        logd(TAG, "closeRN() - activity finished, tx will execute in background")
                    }
                }
                // Fetch token info and submit the Cadence tx in the background
                ioScope {
                    try {
                        val service = retrofitApi().create(ApiService::class.java)
                        val tokenList = service.getAddTokenList("flow", chainNetWorkString())
                        val coin = tokenList.tokens.firstOrNull { it.contractId() == id }
                        if (coin == null) {
                            logw(TAG, "closeRN() - token not found for contractId: $id")
                        } else {
                            logd(TAG, "closeRN() - found token: ${coin.tokenName()}, executing cadenceEnableToken")
                            val transactionId = cadenceEnableToken(coin)
                            if (transactionId.isNullOrBlank()) {
                                loge(TAG, "closeRN() - cadenceEnableToken returned null/blank transactionId")
                            } else {
                                logd(TAG, "closeRN() - tx submitted: $transactionId")
                                val transactionState = TransactionState(
                                    transactionId = transactionId,
                                    time = System.currentTimeMillis(),
                                    state = TransactionStatus.PENDING.ordinal,
                                    type = TransactionState.TYPE_ADD_TOKEN,
                                    data = Gson().toJson(coin)
                                )
                                TransactionStateManager.newTransaction(transactionState)
                                pushBubbleStack(transactionState)
                            }
                        }
                    } catch (e: Exception) {
                        loge(TAG, "closeRN() - Failed to enable token: ${e.message}")
                        e.printStackTrace()
                    }
                }
                return
            }

            // No flowIdentifier → just close the activity
            currentActivity.runOnUiThread {
                try {
                    if (!currentActivity.isFinishing && !currentActivity.isDestroyed) {
                        logd(TAG, "closeRN() - Calling finish() to close React Native activity")
                        currentActivity.setResult(android.app.Activity.RESULT_OK)
                        currentActivity.finish()
                        logd(TAG, "closeRN() - finish() called successfully")
                    } else {
                        logw(TAG, "closeRN() - Activity already finishing or destroyed, skipping")
                    }
                } catch (e: Exception) {
                    loge(TAG, "closeRN() - Failed to finish activity on UI thread: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            loge(TAG, "closeRN() - Failed to close React Native activity: ${e.message}")
            e.printStackTrace()
        }
    }

    fun closeRNWithNFT(id: String?) {
        logd(TAG, "closeRNWithNFT() called - id: $id")
        try {
            val currentActivity = reactContext.currentActivity

            if (currentActivity == null || currentActivity.isFinishing || currentActivity.isDestroyed) {
                logw(TAG, "closeRNWithNFT() - Activity is null, finishing, or destroyed - skipping")
                return
            }

            if (!id.isNullOrBlank()) {
                logd(TAG, "closeRNWithNFT() - flowIdentifier provided, closing screen and triggering enable NFT collection tx: $id")
                // Close the activity first so the user isn't waiting on the API call
                currentActivity.runOnUiThread {
                    if (!currentActivity.isFinishing && !currentActivity.isDestroyed) {
                        currentActivity.setResult(android.app.Activity.RESULT_OK)
                        currentActivity.finish()
                        logd(TAG, "closeRNWithNFT() - activity finished, tx will execute in background")
                    }
                }
                // Fetch NFT collection info and submit the Cadence tx in the background
                ioScope {
                    try {
                        val service = retrofitApi().create(ApiService::class.java)
                        val response = service.getNFTCollections()
                        val collection = response.data.firstOrNull { it.flowIdentifier == id }
                        if (collection == null) {
                            logw(TAG, "closeRNWithNFT() - NFT collection not found for flowIdentifier: $id")
                        } else {
                            logd(TAG, "closeRNWithNFT() - found collection: ${collection.name}, executing cadenceNftEnabled")
                            val transactionId = cadenceNftEnabled(collection)
                            if (transactionId.isNullOrBlank()) {
                                loge(TAG, "closeRNWithNFT() - cadenceNftEnabled returned null/blank transactionId")
                            } else {
                                logd(TAG, "closeRNWithNFT() - tx submitted: $transactionId")
                                val transactionState = TransactionState(
                                    transactionId = transactionId,
                                    time = System.currentTimeMillis(),
                                    state = TransactionStatus.PENDING.ordinal,
                                    type = TransactionState.TYPE_ENABLE_NFT,
                                    data = Gson().toJson(collection)
                                )
                                TransactionStateManager.newTransaction(transactionState)
                                pushBubbleStack(transactionState)
                            }
                        }
                    } catch (e: Exception) {
                        loge(TAG, "closeRNWithNFT() - Failed to enable NFT collection: ${e.message}")
                        e.printStackTrace()
                    }
                }
                return
            }

            // No flowIdentifier → just close the activity
            currentActivity.runOnUiThread {
                try {
                    if (!currentActivity.isFinishing && !currentActivity.isDestroyed) {
                        logd(TAG, "closeRNWithNFT() - Calling finish() to close React Native activity")
                        currentActivity.setResult(android.app.Activity.RESULT_OK)
                        currentActivity.finish()
                        logd(TAG, "closeRNWithNFT() - finish() called successfully")
                    } else {
                        logw(TAG, "closeRNWithNFT() - Activity already finishing or destroyed, skipping")
                    }
                } catch (e: Exception) {
                    loge(TAG, "closeRNWithNFT() - Failed to finish activity on UI thread: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            loge(TAG, "closeRNWithNFT() - Failed to close React Native activity: ${e.message}")
            e.printStackTrace()
        }
    }

    fun showToast(title: String, message: String?, type: String?, duration: Double?) {
        try {
            // Concatenate title and message
            val displayMessage = when {
                title.isNotEmpty() && !message.isNullOrEmpty() -> "$title: $message"
                title.isNotEmpty() -> title
                !message.isNullOrEmpty() -> message
                else -> ""
            }
            if (displayMessage.isEmpty()) {
                logw(TAG, "showToast() skipped - empty message")
                return
            }

            val toastDuration = duration ?: 2000.0

            logd(TAG, "showToast() called - title: $title, message: $message, type:" +
              " ${type ?: "info"}, duration: ${toastDuration}ms")

            // Convert duration from milliseconds to boolean (long or short)
            val isLongDuration = toastDuration > 2000.0

            uiScope {
                toast(msg = displayMessage, duration = if (isLongDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            loge(TAG, "showToast() error: ${e.message}")
            e.printStackTrace()
        }
    }

    fun hideToast(id: String) {
        try {
            logd(TAG, "hideToast() called - id: $id")
            // Android native toast typically auto-dismiss, but we can implement custom logic here
            // For now, this is mainly for API compatibility
        } catch (e: Exception) {
            loge(TAG, "hideToast() error: ${e.message}")
        }
    }

    fun clearAllToasts() {
        try {
            logd(TAG, "clearAllToasts() called")
            // Android native toast typically auto-dismiss, but we can implement custom logic here
            // For now, this is mainly for API compatibility
        } catch (e: Exception) {
            loge(TAG, "clearAllToasts() error: ${e.message}")
        }
    }

    fun launchNativeScreen(screenName: String, params: String?) {
        logd(TAG, "launchNativeScreen() called - screen: $screenName, params: $params")

        try {
            val currentActivity = reactContext.currentActivity

            if (currentActivity == null) {
                logw(TAG, "launchNativeScreen() - no current activity")
                return
            }

            val screen = NativeScreen.fromString(screenName)
            if (screen == null) {
                loge(TAG, "launchNativeScreen() - unknown screen: $screenName")
                return
            }

            when (screen) {
                NativeScreen.MULTI_BACKUP -> {
                    // First launch WalletBackupActivity (parent) so back navigation works correctly
                    WalletBackupActivity.launch(currentActivity, fromRegistration = true)

                    // Then immediately launch MultiBackupActivity (Cloud backup: Google Drive, Passkey, Recovery Phrase)
                    // When user presses back, they will return to WalletBackupActivity
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        reactContext.currentActivity?.let { activity ->
                            MultiBackupActivity.launch(activity)
                            logd(TAG, "launchNativeScreen() - launched MultiBackupActivity")
                        }
                    }, 300) // Small delay to ensure WalletBackupActivity is created first
                }

                NativeScreen.DEVICE_BACKUP -> {
                    // First launch WalletBackupActivity (parent) so back navigation works correctly
                    WalletBackupActivity.launch(currentActivity, fromRegistration = true)

                    // Then immediately launch CreateDeviceBackupActivity (QR code sync between devices)
                    // When user presses back, they will return to WalletBackupActivity
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        reactContext.currentActivity?.let { activity ->
                            CreateDeviceBackupActivity.launch(activity)
                            logd(TAG, "launchNativeScreen() - launched CreateDeviceBackupActivity")
                        }
                    }, 300) // Small delay to ensure WalletBackupActivity is created first
                }

                NativeScreen.SEED_PHRASE_BACKUP -> {
                    // First launch WalletBackupActivity (parent) so back navigation works correctly
                    WalletBackupActivity.launch(currentActivity, fromRegistration = true)

                    // Then immediately launch BackupRecoveryPhraseActivity (View/create recovery phrase)
                    // When user presses back, they will return to WalletBackupActivity
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        reactContext.currentActivity?.let { activity ->
                            val intent = BackupRecoveryPhraseActivity.createIntent(activity)
                            activity.startActivity(intent)
                            logd(TAG, "launchNativeScreen() - launched BackupRecoveryPhraseActivity")
                        }
                    }, 300) // Small delay to ensure WalletBackupActivity is created first
                }

                NativeScreen.BACKUP_OPTIONS -> {
                    // Launch WalletBackupActivity (Native backup options screen)
                    WalletBackupActivity.launch(currentActivity, fromRegistration = true)
                    logd(TAG, "launchNativeScreen() - launched WalletBackupActivity")
                }

                NativeScreen.WALLET_RESTORE -> {
                    // Launch WalletRestoreActivity (Native account restore/recovery screen with multiple options)
                    // Use com.flowfoundation.wallet.page.restore.WalletRestoreActivity which shows:
                    // - Import from Device
                    // - Import from Backup
                    // - Import from Raw Key
                    val intent = Intent(currentActivity, WalletRestoreActivity::class.java)
                    // Pass flag to indicate this was launched from RN GetStartedScreen
                    // so back button can navigate back to GetStartedScreen
                    intent.putExtra("launchedFromRN", true)
                    currentActivity.startActivity(intent)
                    logd(TAG, "launchNativeScreen() - launched WalletRestoreActivity with restore options from RN")
                }

                NativeScreen.RECOVERY_PHRASE_RESTORE -> {
                    // Launch KeyStoreRestoreActivity in seed phrase mode (Restore from 12-word recovery phrase)
                    KeyStoreRestoreActivity.launchSeedPhrase(currentActivity)
                    logd(TAG, "launchNativeScreen() - launched KeyStoreRestoreActivity (seed phrase mode)")
                }

                NativeScreen.KEY_STORE_RESTORE -> {
                    // Launch KeyStoreRestoreActivity (Restore from key store file)
                    KeyStoreRestoreActivity.launchKeyStore(currentActivity)
                    logd(TAG, "launchNativeScreen() - launched KeyStoreRestoreActivity (key store mode)")
                }

                NativeScreen.PRIVATE_KEY_RESTORE -> {
                    // Launch KeyStoreRestoreActivity in private key mode
                    KeyStoreRestoreActivity.launchPrivateKey(currentActivity)
                    logd(TAG, "launchNativeScreen() - launched KeyStoreRestoreActivity (private key mode)")
                }

                NativeScreen.GOOGLE_DRIVE_RESTORE -> {
                    // Launch GoogleDriveRestoreActivity (Google Drive restore flow)
                    GoogleDriveRestoreActivity.launch(currentActivity)
                    logd(TAG, "launchNativeScreen() - launched GoogleDriveRestoreActivity (Google Drive)")
                }

                NativeScreen.MULTI_RESTORE -> {
                    // Launch MultiRestoreActivity (Multi-restore with all cloud backup options)
                    MultiRestoreActivity.launch(currentActivity)
                    logd(TAG, "launchNativeScreen() - launched MultiRestoreActivity")
                }
            }
        } catch (e: Exception) {
            loge(TAG, "launchNativeScreen() error: ${e.message}")
            e.printStackTrace()
        }
    }

}
