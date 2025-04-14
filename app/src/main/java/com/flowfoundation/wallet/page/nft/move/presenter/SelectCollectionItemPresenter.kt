package com.flowfoundation.wallet.page.nft.move.presenter

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemSelectCollectionListBinding
import com.flowfoundation.wallet.page.nft.move.model.CollectionDetailInfo
import com.flowfoundation.wallet.utils.extensions.setVisible


class SelectCollectionItemPresenter(
    private val view: View,
    private val selectedCollectionId: String? = null,
    private val callback: (CollectionDetailInfo) -> Unit,
): BaseViewHolder(view), BasePresenter<CollectionDetailInfo> {

    private val binding by lazy { ItemSelectCollectionListBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: CollectionDetailInfo) {
        with(binding) {
            Glide.with(iconView).load(model.logo).centerCrop().into(iconView)
            collectionNameView.text = model.name.ifEmpty {
                model.contractName
            }
            collectionCountView.text = "${model.count} NFTs"
            ivCollectionLogo.setImageResource(
                if (model.isFlowCollection) {
                    R.drawable.ic_switch_vm_cadence
                } else {
                    R.drawable.ic_switch_vm_evm
                }
            )
            collectionSelectedView.setVisible(model.id == selectedCollectionId)
            view.setOnClickListener {
                callback.invoke(model)
            }
        }
    }
}