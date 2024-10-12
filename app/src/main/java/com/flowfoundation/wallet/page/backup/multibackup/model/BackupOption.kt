package com.flowfoundation.wallet.page.backup.multibackup.model

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.backup.model.BackupType


enum class BackupOption(val layoutId: Int, val iconId: Int) {
    BACKUP_START(R.id.fragment_backup_start_with_about, R.drawable.ic_settings_backup),
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

enum class BackupSeedPhraseOption(val layoutId: Int) {
    BACKUP_WARING(R.id.fragment_wallet_create_warning),
    BACKUP_SEED_PHRASE(R.id.fragment_backup_recovery_phrase_info)
}

enum class BackupRecoveryPhraseOption(val layoutId: Int) {
    BACKUP_WARING(R.id.fragment_backup_recovery_phrase_warning),
    BACKUP_RECOVERY_PHRASE(R.id.fragment_backup_recovery_phrase_info)
}

enum class BackupStartOption(val layoutId: Int) {
    BACKUP_ABOUT(R.id.fragment_backup_start_about),
    BACKUP_START(R.id.fragment_backup_start)
}

enum class BackupAbout(val titleId: Int, val contentId: Int, val noteId: Int) {
    ABOUT_MULTI_BACKUP(R.string.backup_start_about_title, R.string.backup_start_about_content, R
        .string.backup_start_about_note),
    ABOUT_RECOVERY_PHRASE(R.string.backup_recovery_phrase_about_title, R.string.recovery_phrase,
        R.string.backup_recovery_phrase_about_note)
}



class BackupCompletedItem(
    val type: BackupType,
    val mnemonic: String,
)