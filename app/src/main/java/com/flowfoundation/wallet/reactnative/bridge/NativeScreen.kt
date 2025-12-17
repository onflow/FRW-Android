package com.flowfoundation.wallet.reactnative.bridge

/**
 * Enum representing native Android screens that can be launched from React Native
 */
enum class NativeScreen(val screenName: String) {
    // Backup screens
    MULTI_BACKUP("multiBackup"),
    DEVICE_BACKUP("deviceBackup"),
    SEED_PHRASE_BACKUP("seedPhraseBackup"),
    BACKUP_OPTIONS("backupOptions"),

    // Restore screens
    WALLET_RESTORE("walletRestore"),
    RECOVERY_PHRASE_RESTORE("recoveryPhraseRestore"),
    KEY_STORE_RESTORE("keyStoreRestore"),
    PRIVATE_KEY_RESTORE("privateKeyRestore"),
    GOOGLE_DRIVE_RESTORE("googleDriveRestore"),
    ICLOUD_RESTORE("icloudRestore"),
    MULTI_RESTORE("multiRestore");

    companion object {
        fun fromString(value: String): NativeScreen? {
            return values().find { it.screenName == value }
        }
    }
}
