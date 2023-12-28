package io.outblock.lilico.page.backup.multibackup.model

import io.outblock.lilico.R


enum class BackupOption(val layoutId: Int, val iconId: Int) {
    BACKUP_START(R.id.fragment_backup_start, -1),
    BACKUP_WITH_GOOGLE_DRIVE(R.id.fragment_backup_google_drive, R.drawable.ic_backup_google_drive),
    BACKUP_WITH_RECOVERY_PHRASE(R.id.fragment_backup_recovery_phrase, R.drawable.ic_backup_recovery_phrase),
    BACKUP_COMPLETED(R.id.fragment_backup_completed, -1)
}

class BackupOptionModel(
    val option: BackupOption,
    val index: Int,
)