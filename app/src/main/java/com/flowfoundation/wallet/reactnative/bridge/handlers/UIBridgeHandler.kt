package com.flowfoundation.wallet.reactnative.bridge.handlers

import android.content.Intent
import android.widget.Toast
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.flowfoundation.wallet.page.backup.BackupRecoveryPhraseActivity
import com.flowfoundation.wallet.page.backup.WalletBackupActivity
import com.flowfoundation.wallet.page.backup.device.CreateDeviceBackupActivity
import com.flowfoundation.wallet.page.backup.multibackup.MultiBackupActivity
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity
import com.flowfoundation.wallet.page.restore.multirestore.MultiRestoreActivity
import com.flowfoundation.wallet.page.walletrestore.WalletRestoreActivity as GoogleDriveRestoreActivity
import com.flowfoundation.wallet.page.scan.ScanBarcodeActivity
import com.flowfoundation.wallet.reactnative.bridge.QRCodeScanManager
import com.flowfoundation.wallet.reactnative.bridge.NativeScreen
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope

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

            if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
                // Use runOnUiThread to ensure activity operations run on main thread
                currentActivity.runOnUiThread {
                    try {
                        if (!currentActivity.isFinishing && !currentActivity.isDestroyed) {
                            logd(TAG, "closeRN() - Calling finish() to close React Native activity and return to previous activity")
                            // Use finish() to close the activity and return to the previous activity in the task stack
                            // This should return to the native home screen that launched React Native
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
            } else {
                logw(TAG, "closeRN() - Activity is null, finishing, or destroyed - skipping closeRN")
            }
        } catch (e: Exception) {
            // If finishing activity fails, log error but don't crash
            loge(TAG, "closeRN() - Failed to close React Native activity: ${e.message}")
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

                NativeScreen.ICLOUD_RESTORE -> {
                    // iCloud restore not supported on Android, launch MultiRestoreActivity instead
                    // which provides Google Drive and other cloud options
                    MultiRestoreActivity.launch(currentActivity)
                    logd(TAG, "launchNativeScreen() - iCloud requested on Android, launched MultiRestoreActivity instead")
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
