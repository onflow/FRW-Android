package com.flowfoundation.wallet.manager.drive

import com.flowfoundation.wallet.BuildConfig

/**
 * Switch for Google Drive backup and restore (GOOGLE_DRIVE_BACKUP_ENABLED). Off by default: the
 * Google sign-in OAuth client lived in Firebase project lilico-334404 (556216024625), which is gone,
 * so sign-in fails and backups in users' Drive appDataFolder can't be read by a new client.
 * With the flag off every Drive entry point is hidden. Recovery phrase, Dropbox and device backups
 * keep working.
 *
 * To restore later: create an Android OAuth client + Drive API in a live project, set
 * `googleDriveBackupEnabled=true` in gradle.properties. Old backups stay tied to the old client.
 */
object GoogleDriveFlags {
    val backupEnabled: Boolean get() = BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED
}
