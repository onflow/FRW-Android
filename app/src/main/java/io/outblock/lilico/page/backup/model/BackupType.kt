package io.outblock.lilico.page.backup.model

import io.outblock.lilico.R


enum class BackupType(val index: Int, val displayName: String, val iconRes: Int) {
    GOOGLE_DRIVE(0, "Backup - Google Drive", R.drawable.ic_backup_google_drive),
    ICLOUD(1, "Backup - iCloud", -1),
    MANUAL(2, "Backup - Manual", R.drawable.ic_backup_recovery_phrase),
    PASSKEY(3, "Backup - Passkey", -1);

    companion object {

        @JvmStatic
        fun getBackupName(type: Int): String {
            return values().firstOrNull { it.index == type }?.displayName ?: ""
        }

        @JvmStatic
        fun getBackupIcon(type: Int): Int {
            return values().firstOrNull { it.index == type }?.iconRes ?: -1
        }
    }
}