package io.outblock.lilico.page.backup.presenter

import android.annotation.SuppressLint
import android.view.View
import androidx.fragment.app.FragmentActivity
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.databinding.LayoutBackupInfoItemBinding
import io.outblock.lilico.page.backup.BackupDetailActivity
import io.outblock.lilico.page.backup.model.BackupKey
import io.outblock.lilico.page.backup.model.BackupType
import io.outblock.lilico.page.profile.subpage.wallet.key.AccountKeyRevokeDialog
import io.outblock.lilico.utils.findActivity
import io.outblock.lilico.utils.formatGMTToDate


class BackupListItemPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<BackupKey> {

    private val binding by lazy { LayoutBackupInfoItemBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: BackupKey) {
        with(binding) {
            model.info?.backupInfo?.let {
                ivBackupType.setImageResource(BackupType.getBackupIcon(it.type))
                tvBackupName.text = BackupType.getBackupName(it.type)
            }
            model.info?.device?.let {
                tvBackupOs.text = it.user_agent
                tvBackupLocation.text = it.city + ", " + it.countryCode + " · " + formatGMTToDate(it.updated_at)
            }
            clBackupContentLayout.setOnClickListener {
                BackupDetailActivity.launch(view.context, model)
            }
            tvDelete.setOnClickListener {
                if (model.isRevoking) {
                    return@setOnClickListener
                }
                srlSwipeLayout.close(false)
                AccountKeyRevokeDialog.show(
                    findActivity(view) as FragmentActivity, model.keyId
                )
            }
        }
    }
}