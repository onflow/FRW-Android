package io.outblock.lilico.page.backup.model

import io.outblock.lilico.R

enum class BackupListTitle(val titleResId: Int, val titleSize: Float, val titleColorResId: Int) {
    DEVICE_BACKUP(R.string.device_backup, 16f, R.color.text_2),
    OTHER_DEVICES(R.string.other_devices, 14f, R.color.text_3),
    MULTI_BACKUP(R.string.multi_backup, 16f, R.color.text_2)
}