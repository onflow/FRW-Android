package com.flowfoundation.wallet.page.profile.subpage.developer.presenter

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutLocalKeyItemBinding
import com.flowfoundation.wallet.page.profile.subpage.developer.model.LocalAccountKey
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast

class LocalAccountKeyListItemPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<LocalAccountKey> {
    private val binding by lazy { LayoutLocalKeyItemBinding.bind(view) }

    override fun bind(model: LocalAccountKey) {
        with(binding) {
            tvUid.text = model.userId
            tvUsername.text = model.userName
            tvPublicKeyContent.text = model.publicKey
            ivKeyCopy.setOnClickListener {
                textToClipboard(model.publicKey)
                toast(msgRes = R.string.copied_to_clipboard)
            }
        }
    }

}