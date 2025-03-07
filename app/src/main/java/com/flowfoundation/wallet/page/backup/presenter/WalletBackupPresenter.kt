package com.flowfoundation.wallet.page.backup.presenter

import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.databinding.ActivityWalletBackupBinding
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.backup.BackupListAdapter
import com.flowfoundation.wallet.page.backup.BackupRecoveryPhraseActivity
import com.flowfoundation.wallet.page.backup.WalletBackupActivity
import com.flowfoundation.wallet.page.backup.device.CreateDeviceBackupActivity
import com.flowfoundation.wallet.page.backup.multibackup.MultiBackupActivity
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.setMultiBackupCreated
import com.flowfoundation.wallet.utils.setMultiBackupDeleted
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog

class WalletBackupPresenter(
    val activity: WalletBackupActivity,
    val binding: ActivityWalletBackupBinding,
) {

    private val backupAdapter by lazy { BackupListAdapter() }
    private val seedPhraseAdapter by lazy { BackupListAdapter() }
    private val deviceAdapter by lazy { BackupListAdapter() }

    private var isBackupListLoading = true
    private var isDeviceListLoading = true

    init {
        with(binding.rvMultiBackupList) {
            adapter = backupAdapter
            layoutManager = LinearLayoutManager(context)
        }
        with(binding.rvDeviceBackupList) {
            adapter = deviceAdapter
            layoutManager = LinearLayoutManager(context)
        }
        with(binding.rvSeadPhraseBackupList) {
            adapter = seedPhraseAdapter
            layoutManager = LinearLayoutManager(context)
        }
        with(binding) {
            cvCreateDeviceBackup.setOnClickListener {
                CreateDeviceBackupActivity.launch(activity)
            }
            cvCreateMultiBackup.setOnClickListener {
                if (isTestnet()) {
                    SwitchNetworkDialog(activity, DialogType.BACKUP).show()
                } else {
                    activity.backupResultLauncher.launch(MultiBackupActivity.createIntent(activity))
                }
            }
            cvCreateSeedPhraseBackup.setOnClickListener {
                if (isTestnet()) {
                    SwitchNetworkDialog(activity, DialogType.BACKUP).show()
                } else {
                    activity.backupResultLauncher.launch(BackupRecoveryPhraseActivity.createIntent(activity))
                }
            }
        }
    }

    fun showLoading() {
        isBackupListLoading = true
        isDeviceListLoading = true
        with(binding) {
            cvCreateSeedPhraseBackup.gone()
            cvCreateDeviceBackup.gone()
            cvCreateMultiBackup.gone()
            llMultiBackupList.gone()
            llDeviceBackupList.gone()
            llSeedPhraseList.gone()
            lavLoading.visible()
        }
    }

    fun bindSeedPhraseList(list: List<Any>) {
        with(binding) {
            cvCreateSeedPhraseBackup.setVisible(list.isEmpty())
            llSeedPhraseList.setVisible(list.isNotEmpty())
            seedPhraseAdapter.setNewDiffData(list)
        }
    }

    fun bindBackupList(list: List<Any>) {
        with(binding) {
            cvCreateMultiBackup.setVisible(list.isEmpty())
            llMultiBackupList.setVisible(list.isNotEmpty())
            backupAdapter.setNewDiffData(list)
        }
        setMultiBackupStatus(list.isEmpty())
        isBackupListLoading = false
        checkLoadingStatus()
    }

    private fun setMultiBackupStatus(noBackup: Boolean) {
        if (noBackup) {
            setMultiBackupDeleted()
        } else {
            setMultiBackupCreated()
        }
    }

    fun bindDeviceList(list: List<Any>) {
        with(binding) {
            cvCreateDeviceBackup.setVisible(list.isEmpty())
            llDeviceBackupList.setVisible(list.isNotEmpty())
            deviceAdapter.setNewDiffData(list)
        }
        isDeviceListLoading = false
        checkLoadingStatus()
    }

    private fun checkLoadingStatus() {
        if (!isBackupListLoading && !isDeviceListLoading) {
            binding.lavLoading.gone()
        }
    }
}