package com.flowfoundation.wallet.page.backup.model

import com.flowfoundation.wallet.R


enum class BackupViewMnemonicModel(val layoutId: Int) {
    BACKUP_DETAIL_PIN(R.layout.fragment_restore_pin_code),
    BACKUP_DETAIL_RECOVERY_PHRASE(R.layout.fragment_view_recovery_phrase),
    BACKUP_DETAIL_ERROR_BACKUP(R.layout.fragment_restore_error),
    BACKUP_DETAIL_ERROR_PIN(R.layout.fragment_restore_error),
}
