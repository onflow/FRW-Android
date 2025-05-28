package com.flowfoundation.wallet.page.profile.subpage.wallet.key.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutKeyListItemBinding
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyRevokeDialog
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.model.AccountKey
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast

class AccountKeyListItemPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<AccountKey> {

    private val binding by lazy { LayoutKeyListItemBinding.bind(view) }

    private var isExpanded = true

    @SuppressLint("SetTextI18n")
    override fun bind(model: AccountKey) {
        with(binding) {
            if (model.backupType > -1) {
                ivKeyType.setImageResource(BackupType.getBackupKeyIcon(model.backupType))
            } else {
                ivKeyType.setImageResource(if (model.deviceType == 2) R.drawable.ic_device_type_computer else R.drawable.ic_device_type_phone)
            }
            val keyType: String
            val typeColor: Int
            if (model.isCurrentDevice) {
                keyType = R.string.current_device.res2String()
                typeColor = R.color.accent_blue.res2color()
            } else {
                keyType = model.deviceName
                typeColor = R.color.text_3.res2color()
            }
            tvKeyType.text = keyType
            tvKeyType.setTextColor(typeColor)
            val weight = if (model.weight < 0) 0 else model.weight
            val statusType: String
            val statusColor: Int
            if (model.revoked) {
                statusType = R.string.revoked.res2String()
                statusColor = R.color.accent_red.res2color()
            } else if (model.isRevoking) {
                statusType = R.string.revoking.res2String()
                statusColor = R.color.accent_orange.res2color()
            } else if (weight >= 1000){
                statusType = R.string.full_access.res2String()
                statusColor = R.color.accent_green.res2color()
            } else {
                statusType = R.string.multi_sign.res2String()
                statusColor = R.color.text_3.res2color()
            }
            tvStatusLabel.text = statusType
            tvStatusLabel.backgroundTintList = ColorStateList.valueOf(statusColor)
            tvStatusLabel.setTextColor(statusColor)
            tvKeyWeight.text = "$weight / 1000"
            val progress = weight / 1000f
            pbKeyWeight.max = 100
            if (progress > 1) {
                pbKeyWeight.progressTintList = ColorStateList.valueOf(R.color.accent_green
                    .res2color())
                pbKeyWeight.progress = 100
            } else {
                pbKeyWeight.progress = (progress * 100).toInt()
            }

            tvPublicKeyContent.text = model.publicKey.base16Value
            tvCurveContent.text = model.signAlgo.name
            tvHashContent.text = model.hashAlgo.name
            tvSequenceContent.text = model.sequenceNumber.toString()
            tvKeyIndex.text = if(model.id < 0) "0${model.id}" else model.id.toString()

            cvTitleCard.setOnClickListener {
                toggleContent()
            }
            if (model.revoked || model.isRevoking) {
                srlSwipeLayout.setLockDrag(true)
                srlSwipeLayout.close(false)
            } else {
                srlSwipeLayout.setLockDrag(false)
            }
            tvRevoke.setOnClickListener {
                if (model.isCurrentDevice) {
                    toast(msgRes = R.string.can_not_revoke)
                    return@setOnClickListener
                }
                if (model.revoked || model.isRevoking) {
                    return@setOnClickListener
                }
                srlSwipeLayout.close(false)
                AccountKeyRevokeDialog.show(
                    findActivity(view) as FragmentActivity, model.id
                )
            }
            ivKeyCopy.setOnClickListener {
                textToClipboard(model.publicKey.base16Value)
                toast(msgRes = R.string.copied_to_clipboard)
            }
        }
    }

    private fun toggleContent() {
        isExpanded = isExpanded.not()
        with(binding) {
            fbToggle.setImageResource(if (isExpanded) {
                R.drawable.ic_key_list_collapse
            } else {
                R.drawable.ic_key_list_expand
            })
            clKeyContent.setVisible(isExpanded)
        }
    }
}