package com.flowfoundation.wallet.page.backup.multibackup.model

import com.flowfoundation.wallet.R


enum class BackupOption(val layoutId: Int, val iconId: Int) {
    BACKUP_START(R.id.fragment_backup_start, R.drawable.ic_settings_backup),
    BACKUP_WITH_GOOGLE_DRIVE(R.id.fragment_backup_google_drive_with_pin, R.drawable.ic_backup_google_drive),
    BACKUP_WITH_RECOVERY_PHRASE(R.id.fragment_backup_recovery_phrase, R.drawable.ic_backup_recovery_phrase),
    BACKUP_COMPLETED(R.id.fragment_backup_completed, R.drawable.ic_settings_backup),
}

class BackupOptionModel(
    val option: BackupOption,
    val index: Int,
)

enum class BackupGoogleDriveOption(val layoutId: Int) {
    BACKUP_PIN(R.id.fragment_backup_pin_code),
    BACKUP_GOOGLE_DRIVE(R.id.fragment_backup_google_drive)
}
