package com.flowfoundation.wallet.reactnative.bridge

/**
 * Enum representing native Android screens that can be launched from React Native
 */
enum class NativeScreen(val screenName: String) {
    MULTI_BACKUP("multiBackup"),
    DEVICE_BACKUP("deviceBackup"),
    SEED_PHRASE_BACKUP("seedPhraseBackup"),
    BACKUP_OPTIONS("backupOptions"),
    WALLET_RESTORE("walletRestore");

    companion object {
        fun fromString(value: String): NativeScreen? {
            return values().find { it.screenName == value }
        }
    }
}
