package com.flowfoundation.wallet.manager.backup

import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.getDoNotTipBackupAddressSet
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.setDoNotTipBackupAddressSet


object BackupTipManager {

    private val addressSet: MutableSet<String> by lazy {
        getDoNotTipBackupAddressSet().toMutableSet()
    }

    fun createBackup() {
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            if (addressSet.add(address)) {
                setDoNotTipBackupAddressSet(addressSet)
            }
        }
    }

    fun deleteBackup() {
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            if (addressSet.remove(address)) {
                setDoNotTipBackupAddressSet(addressSet)
            }
        }
    }

    fun backupStatusChange(backupSuccess: Boolean) {
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            if (backupSuccess) {
                if (addressSet.add(address)) {
                    setDoNotTipBackupAddressSet(addressSet)
                }
            } else {
                if (addressSet.remove(address)) {
                    setDoNotTipBackupAddressSet(addressSet)
                }
            }
        }
    }

    fun markDoNotShow(notShow: Boolean) {
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            if (notShow) {
                if (addressSet.add(address)) {
                    setDoNotTipBackupAddressSet(addressSet)
                }
            } else {
                if (addressSet.remove(address)) {
                    setDoNotTipBackupAddressSet(addressSet)
                }
            }
        }
    }

    fun isShowBackupTip(): Boolean {
        val address = WalletManager.selectedWalletAddress()
        return addressSet.contains(address).not()
    }
}