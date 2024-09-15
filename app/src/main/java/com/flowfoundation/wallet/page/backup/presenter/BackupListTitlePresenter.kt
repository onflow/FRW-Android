package com.flowfoundation.wallet.page.backup.presenter

import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutBackupTitleItemBinding
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.backup.BackupRecoveryPhraseActivity
import com.flowfoundation.wallet.page.backup.device.CreateDeviceBackupActivity
import com.flowfoundation.wallet.page.backup.model.BackupListTitle
import com.flowfoundation.wallet.page.backup.multibackup.MultiBackupActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.DevicesActivity
import com.flowfoundation.wallet.page.walletrestore.AccountNotFoundDialog
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog


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
                    if (isTestnet() || isPreviewnet()) {
                        SwitchNetworkDialog(view.context, DialogType.BACKUP).show()
                    } else {
                        MultiBackupActivity.launch(view.context)
                    }
                } else if (model == BackupListTitle.FULL_WEIGHT_SEED_PHRASE) {
                    if (isTestnet() || isPreviewnet()) {
                        SwitchNetworkDialog(view.context, DialogType.BACKUP).show()
                    } else {
                        BackupRecoveryPhraseActivity.launch(view.context)
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