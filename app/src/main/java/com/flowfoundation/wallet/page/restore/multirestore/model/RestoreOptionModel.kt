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
    RESTORE_COMPLETED(R.layout.fragment_restore_completed, R.drawable.ic_settings_backup)
}

enum class RestoreGoogleDriveOption(val layoutId: Int) {
    RESTORE_PIN(R.layout.fragment_restore_pin_code),
    RESTORE_GOOGLE_DRIVE(R.layout.fragment_restore_google_drive)
}
