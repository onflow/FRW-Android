package io.outblock.lilico.page.backup

import io.outblock.lilico.page.backup.model.BackupKey
import io.outblock.lilico.page.backup.model.BackupType

object BackupListManager {

    private val typeList = mutableListOf<BackupType>()

    fun setBackupTypeList(list: List<BackupKey>) {
        typeList.clear()
        typeList.addAll(
            list.mapNotNull {
                val type = it.info?.backupInfo?.type ?: -1
                BackupType.getBackupType(type)
            }
        )
    }

    fun backupCount(): Int {
        return typeList.size
    }

    fun hadBackupOption(type: BackupType): Boolean {
        return typeList.contains(type)
    }

    fun clear() {
        typeList.clear()
    }
}