package io.outblock.lilico.page.backup.presenter

import androidx.recyclerview.widget.LinearLayoutManager
import io.outblock.lilico.databinding.ActivityWalletBackupBinding
import io.outblock.lilico.manager.app.isTestnet
import io.outblock.lilico.page.backup.BackupListAdapter
import io.outblock.lilico.page.backup.WalletBackupActivity
import io.outblock.lilico.page.backup.device.CreateDeviceBackupActivity
import io.outblock.lilico.page.backup.multibackup.MultiBackupActivity
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.isMultiBackupCreated
import io.outblock.lilico.utils.setMultiBackupCreated
import io.outblock.lilico.utils.setMultiBackupDeleted
import io.outblock.lilico.widgets.DialogType
import io.outblock.lilico.widgets.SwitchNetworkDialog

class WalletBackupPresenter(
    val activity: WalletBackupActivity,
    val binding: ActivityWalletBackupBinding,
) {

    private val backupAdapter by lazy { BackupListAdapter() }
    private val deviceAdapter by lazy { BackupListAdapter() }

    init {
        with(binding.rvMultiBackupList) {
            adapter = backupAdapter
            layoutManager = LinearLayoutManager(context)
        }
        with((binding.rvDeviceBackupList)) {
            adapter = deviceAdapter
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
                    MultiBackupActivity.launch(activity)
                }
            }
        }
    }

    fun bindBackupList(list: List<Any>) {
        with(binding) {
            cvCreateMultiBackup.setVisible(list.isEmpty())
            llMultiBackupList.setVisible(list.isNotEmpty())
            backupAdapter.setNewDiffData(list)
        }
        setMultiBackupStatus(list.isEmpty())
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
    }
}