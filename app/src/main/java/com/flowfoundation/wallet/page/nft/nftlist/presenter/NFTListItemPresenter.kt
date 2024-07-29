package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.annotation.SuppressLint
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftListBinding
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.page.collection.CollectionActivity
import com.flowfoundation.wallet.page.nft.nftdetail.NftDetailActivity
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTItemModel
import com.flowfoundation.wallet.page.nft.nftlist.title
import com.flowfoundation.wallet.page.nft.nftlist.widget.NftItemPopupMenu
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2pix
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity

class NFTListItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<NFTItemModel> {
    private val binding by lazy { ItemNftListBinding.bind(view) }
    private val activity by lazy { findActivity(view) as FragmentActivity }
    private val context = view.context

    private val dividerSize by lazy { R.dimen.nft_list_divider_size.res2pix() }

    private val isCollectionPage by lazy { activity.javaClass == CollectionActivity::class.java }

    @SuppressLint("SetTextI18n")
    override fun bind(model: NFTItemModel) {
        val nft = model.nft
        with(binding) {
            Glide.with(coverView).load(nft.cover()).transform(RoundedCorners(10.dp2px().toInt()))
                .placeholder(R.drawable.ic_placeholder).into(coverView)
            nameView.text = nft.title() ?: nft.title ?: nft.contractName()
            priceView.text = nft.postMedia.description ?: ""

            coverViewWrapper.setOnClickListener {
                NftDetailActivity.launch(context, nft.uniqueId())
            }
            coverViewWrapper.setOnLongClickListener {
                NftItemPopupMenu(coverView, model.nft).show()
                true
            }

            view.setBackgroundResource(R.color.transparent)
            view.setPadding(0, 0, 0, 0)
        }
        view.setOnClickListener {
            NftDetailActivity.launch(context, nft.uniqueId())
        }
        bindAccessible(model)
    }

    private fun bindAccessible(model: NFTItemModel) {
        val accessible = ChildAccountCollectionManager.isNFTAccessible(model.nft.collectionAddress, model.nft.collectionContractName)
        binding.priceView.setVisible(accessible)
        binding.tvInaccessibleTag.setVisible(accessible.not())
    }

    private fun showPopupMenu(model: NFTItemModel) {

    }
}