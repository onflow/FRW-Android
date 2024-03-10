package com.flowfoundation.wallet.page.profile.subpage.wallet.key.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutKeyListItemBinding
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyRevokeDialog
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.model.AccountKey
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.wallet.Wallet

class AccountKeyListItemPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<AccountKey> {

    private val binding by lazy { LayoutKeyListItemBinding.bind(view) }

    private var isExpanded = false

    @SuppressLint("SetTextI18n")
    override fun bind(model: AccountKey) {
        with(binding) {
            tvSerialNumber.text = "Key ${model.id}"
            val labelText: String
            val labelColor: Int
            if (model.isCurrentDevice) {
                labelText = "Current Device"
                labelColor = R.color.accent_blue.res2color()
            } else if (model.revoked) {
                labelText = "Revoked"
                labelColor = R.color.accent_red.res2color()
            } else if (model.isRevoking) {
                labelText = "Revoking..."
                labelColor = R.color.accent_orange.res2color()
            } else {
                labelText = model.deviceName
                labelColor = R.color.text_3.res2color()
            }
            tvDeviceLabel.text = labelText
            tvDeviceLabel.backgroundTintList = ColorStateList.valueOf(labelColor)
            tvDeviceLabel.setTextColor(labelColor)
            tvDeviceLabel.setVisible(labelText.isNotEmpty())
            val weight = if (model.weight < 0) 0 else model.weight
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

            cvTitleCard.setOnClickListener {
                toggleContent()
            }
            tvRevoke.setOnClickListener {
                if (model.isCurrentDevice) {
                    toast(msg = "Can't Revoke Current Device Key")
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
            fbToggle.setImageResource(if(isExpanded) {
                R.drawable.ic_key_list_collapse
            } else {
                R.drawable.ic_key_list_expand
            })
            clKeyContent.setVisible(isExpanded)
        }
    }
}