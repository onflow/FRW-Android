package io.outblock.lilico.page.restore.multirestore.model

import io.outblock.lilico.R


class RestoreOptionModel(
    val option: RestoreOption,
    val index: Int
)

enum class RestoreOption(val layoutId: Int, val iconId: Int) {
    RESTORE_START(R.layout.fragment_restore_start, -1),
    RESTORE_FROM_GOOGLE_DRIVE(R.layout.fragment_restore_google_drive, R.drawable.ic_backup_google_drive),
    RESTORE_FROM_RECOVERY_PHRASE(R.layout.fragment_restore_recovery_phrase, R.drawable.ic_backup_recovery_phrase),
    RESTORE_COMPLETED(R.layout.fragment_restore_completed, -1)
}