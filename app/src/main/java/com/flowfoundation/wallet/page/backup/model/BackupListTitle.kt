package com.flowfoundation.wallet.page.backup.model

import com.flowfoundation.wallet.R

enum class BackupListTitle(val titleResId: Int, val titleSize: Float, val titleColorResId: Int) {
    DEVICE_BACKUP(R.string.device_backup, 16f, R.color.text_2),
    OTHER_DEVICES(R.string.other_devices, 14f, R.color.text_3),
    MULTI_BACKUP(R.string.multi_backup, 16f, R.color.text_2),
    FULL_WEIGHT_SEED_PHRASE(R.string.full_weight_seed_phrase, 16f, R.color.text_2)
}