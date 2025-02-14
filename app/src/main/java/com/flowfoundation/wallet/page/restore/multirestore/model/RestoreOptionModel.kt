package com.flowfoundation.wallet.page.restore.multirestore.model

import com.flowfoundation.wallet.R


class RestoreOptionModel(
    val option: RestoreOption,
    val index: Int
)

enum class RestoreOption(val layoutId: Int, val iconId: Int) {
    RESTORE_START(R.layout.fragment_restore_start, R.drawable.ic_settings_backup),
    RESTORE_FROM_GOOGLE_DRIVE(R.layout.fragment_restore_google_drive_with_pin, R.drawable.ic_backup_google_drive),
    RESTORE_FROM_RECOVERY_PHRASE(R.layout.fragment_restore_recovery_phrase, R.drawable.ic_backup_recovery_phrase),
    RESTORE_FROM_DROPBOX(R.layout.fragment_restore_dropbox_with_pin, R.drawable.ic_dropbox),
    RESTORE_COMPLETED(R.layout.fragment_restore_completed, R.drawable.ic_settings_backup),
    RESTORE_FAILED(R.layout.fragment_restore_error, R.drawable.ic_restore_error),
    RESTORE_FAILED_NO_ACCOUNT(R.layout.fragment_restore_error, R.drawable.ic_restore_error)
}

enum class RestoreGoogleDriveOption(val layoutId: Int) {
    RESTORE_PIN(R.layout.fragment_restore_pin_code),
    RESTORE_GOOGLE_DRIVE(R.layout.fragment_restore_google_drive),
    RESTORE_ERROR_BACKUP(R.layout.fragment_restore_error),
    RESTORE_ERROR_PIN(R.layout.fragment_restore_error)
}

enum class RestoreDropboxOption(val layoutId: Int) {
    RESTORE_PIN(R.layout.fragment_restore_pin_code),
    RESTORE_DROPBOX(R.layout.fragment_restore_dropbox),
    RESTORE_ERROR_BACKUP(R.layout.fragment_restore_error),
    RESTORE_ERROR_PIN(R.layout.fragment_restore_error)
}

enum class RestoreErrorOption(val titleId: Int, val descId: Int) {
    BACKUP_NOT_FOUND(R.string.backup_not_found, R.string.backup_not_found_desc),
    BACKUP_DECRYPTION_FAILED(R.string.backup_decryption_failed, R.string.backup_decryption_failed_desc),
    NO_ACCOUNT_FOUND(R.string.no_account_found, R.string.no_account_found_desc),
    RESTORE_FAILED(R.string.restore_failed, R.string.restore_failed_desc)
}

