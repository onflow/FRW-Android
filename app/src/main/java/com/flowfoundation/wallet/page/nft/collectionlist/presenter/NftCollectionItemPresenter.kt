package com.flowfoundation.wallet.page.nft.collectionlist.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftCollectionListBinding
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.nft.collectionlist.NftEnableConfirmDialog
import com.flowfoundation.wallet.page.nft.collectionlist.model.NftCollectionItem
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import jp.wasabeef.glide.transformations.BlurTransformation

class NftCollectionItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<NftCollectionItem> {

    private val binding by lazy { ItemNftCollectionListBinding.bind(view) }

    override fun bind(model: NftCollectionItem) {
        with(binding) {
            nameView.text = model.collection.name
            descView.text = model.collection.description
            Glide.with(coverView).load(model.collection.banner()).transform(BlurTransformation(2, 5)).into(coverView)
            stateButton.setOnClickListener {
                if (model.isNormalState()) {
                    NftEnableConfirmDialog.show((findActivity(view) as FragmentActivity).supportFragmentManager, model.collection)
                }
            }
            progressBar.setVisible(model.isAdding == true)
            stateButton.setVisible(model.isAdding != true)
            titleWrapper.setOnClickListener {
                if (AppConfig.useInAppBrowser()) {
                    openBrowser(findActivity(view)!!, model.collection.officialWebsite)
                }
            }
            stateButton.setImageResource(if (model.isNormalState()) R.drawable.ic_baseline_add_24_salmon_primary else R.drawable.ic_check_round)
        }
    }
}