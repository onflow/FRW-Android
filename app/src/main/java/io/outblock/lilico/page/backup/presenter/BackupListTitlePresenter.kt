package io.outblock.lilico.page.backup.presenter

import android.view.View
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.databinding.LayoutBackupTitleItemBinding
import io.outblock.lilico.manager.app.isTestnet
import io.outblock.lilico.page.backup.device.CreateDeviceBackupActivity
import io.outblock.lilico.page.backup.model.BackupListTitle
import io.outblock.lilico.page.backup.multibackup.MultiBackupActivity
import io.outblock.lilico.page.profile.subpage.wallet.device.DevicesActivity
import io.outblock.lilico.page.walletrestore.AccountNotFoundDialog
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.extensions.res2color
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.widgets.SwitchNetworkDialog


class BackupListTitlePresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<BackupListTitle> {

    private val binding by lazy { LayoutBackupTitleItemBinding.bind(view) }


    override fun bind(model: BackupListTitle) {
        with(binding) {
            tvTitle.text = model.titleResId.res2String()
            tvTitle.textSize = model.titleSize
            tvTitle.setTextColor(model.titleColorResId.res2color())
            configureAction(model == BackupListTitle.OTHER_DEVICES)
            tvViewAll.setOnClickListener {
                DevicesActivity.launch(view.context)
            }
            ivAdd.setOnClickListener {
                if (model == BackupListTitle.DEVICE_BACKUP) {
                    CreateDeviceBackupActivity.launch(view.context)
                } else if (model == BackupListTitle.MULTI_BACKUP) {
                    if (isTestnet()) {
                        SwitchNetworkDialog(view.context).show()
                    } else {
                        MultiBackupActivity.launch(view.context)
                    }
                }
            }
        }
    }

    private fun configureAction(viewAll: Boolean) {
        binding.ivAdd.setVisible(viewAll.not())
        binding.tvViewAll.setVisible(viewAll)
    }
}