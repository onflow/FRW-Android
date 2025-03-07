package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftListBinding
import com.flowfoundation.wallet.page.nft.nftdetail.NftDetailActivity
import com.flowfoundation.wallet.page.nft.nftlist.getNFTCover
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTItemModel
import com.flowfoundation.wallet.page.nft.nftlist.title
import com.flowfoundation.wallet.page.nft.nftlist.widget.NftItemPopupMenu
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible

class NFTListItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<NFTItemModel> {
    private val binding by lazy { ItemNftListBinding.bind(view) }
    private val context = view.context

    @SuppressLint("SetTextI18n")
    override fun bind(model: NFTItemModel) {
        val nft = model.nft
        val fromAddress = model.accountAddress
        with(binding) {
            Glide.with(coverView).load(nft.getNFTCover())
                .transform(RoundedCorners(10.dp2px().toInt()))
                .placeholder(R.drawable.ic_placeholder).into(coverView)
            nameView.text = nft.title() ?: nft.title ?: nft.contractName()
            priceView.text = nft.postMedia?.description ?: ""

            coverViewWrapper.setOnClickListener {
                NftDetailActivity.launch(context, nft.uniqueId(), nft.contractName(), fromAddress)
            }
            coverViewWrapper.setOnLongClickListener {
                NftItemPopupMenu(coverView, model.nft).show()
                true
            }

            view.setBackgroundResource(R.color.transparent)
            view.setPadding(0, 0, 0, 0)
        }
        view.setOnClickListener {
            NftDetailActivity.launch(context, nft.uniqueId(), nft.contractName(), fromAddress)
        }
        bindAccessible(model)
    }

    private fun bindAccessible(model: NFTItemModel) {
        val accessible = ChildAccountCollectionManager.isNFTAccessible(model.nft.collectionAddress, model.nft.contractName())
        binding.priceView.setVisible(accessible)
        binding.tvInaccessibleTag.setVisible(accessible.not())
    }
}