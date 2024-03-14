package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.presenter

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemAccessibleNftBinding
import com.flowfoundation.wallet.page.collection.CollectionActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.CollectionData
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.loadAvatar

class AccessibleNftCollectionPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<CollectionData> {

    private val binding by lazy { ItemAccessibleNftBinding.bind(view) }

    override fun bind(model: CollectionData) {
        with(binding) {
            view.setOnClickListener {
                findActivity(view)?.let {
                    CollectionActivity.launch(it, model.contractName, model.logo, model.name, model.idList.size)
                }
            }
            iconView.loadAvatar(model.logo)
            titleView.text = model.name.ifBlank { model.contractName }
            collectionCountView.text = view.context.getString(R.string.collections_count, model.idList.size)
            arrowView.setVisible(model.idList.isNotEmpty())
        }
    }
}