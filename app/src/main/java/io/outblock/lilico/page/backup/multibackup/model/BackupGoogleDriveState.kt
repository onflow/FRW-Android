package io.outblock.lilico.page.backup.multibackup.model


enum class BackupGoogleDriveState {
    CREATE_BACKUP,
    UPLOAD_BACKUP,
    UPLOAD_BACKUP_FAILURE,
    REGISTRATION_KEY_LIST,
    NETWORK_ERROR,
    BACKUP_SUCCESS
}