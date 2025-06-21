package com.flowfoundation.wallet.page.backup.presenter

import android.annotation.SuppressLint
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutBackupInfoItemBinding
import com.flowfoundation.wallet.page.backup.BackupDetailActivity
import com.flowfoundation.wallet.page.backup.model.BackupKey
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyRevokeDialog
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.formatGMTToDate


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
            val created = model.info?.backupInfo?.createTime
            model.info?.device?.let {
                tvBackupOs.text = it.user_agent
                tvBackupLocation.text = cityInfo(it.city, it.countryCode) +
                        if (created.isNullOrBlank()) formatGMTToDate(it.updated_at) else
                            formatGMTToDate(created)
            }
            clBackupContentLayout.setOnClickListener {
                BackupDetailActivity.launch(view.context, model)
            }
            if (model.isRevoking || model.isCurrentKey) {
                srlSwipeLayout.setLockDrag(true)
                srlSwipeLayout.close(false)
            } else {
                srlSwipeLayout.setLockDrag(false)
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

    private fun cityInfo(city: String, country: String): String {
        val sb = StringBuilder()
        if (city.isNotBlank()) {
            sb.append(city)
            if (country.isNotBlank()) {
                sb.append(", ").append(country)
            }
            sb.append(" · ")
        } else {
            if (country.isNotBlank()) {
                sb.append(country).append(" · ")
            }
        }
        return sb.toString()
    }
}