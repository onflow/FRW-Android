package com.flowfoundation.wallet.page.nft.nftlist.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftListCollectionTabBinding
import com.flowfoundation.wallet.page.nft.nftlist.NftViewModel
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionItemModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity

class CollectionTabsAdapter : BaseAdapter<CollectionItemModel>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return TabsViewHolder(parent.inflate(R.layout.item_nft_list_collection_tab))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as TabsViewHolder).bind(getItem(position))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
    }
}

private class TabsViewHolder(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<CollectionItemModel> {
    private val binding by lazy { ItemNftListCollectionTabBinding.bind(view) }

    private val corners by lazy { 12.dp2px().toInt() }

    private val activity by lazy { findActivity(view) as FragmentActivity }

    private val viewModel by lazy { ViewModelProvider(activity)[NftViewModel::class.java] }

    private var model: CollectionItemModel? = null

    init {
        view.setOnClickListener {
            model?.collection?.contractName()?.let { viewModel.selectCollection(it) }
        }
    }

    override fun bind(model: CollectionItemModel) {
        val config = model.collection
        with(binding) {
            if (this@TabsViewHolder.model != model) {
                Glide.with(coverView).load(config.logo()).transform(CenterCrop(), RoundedCorners(corners)).into(coverView)
                nameView.text = config.name
                countView.text = view.context.getString(R.string.collectibles_count, model.count)
            }
            root.strokeColor = if (model.isSelected) R.color.neutrals4.res2color() else Color.TRANSPARENT
        }
        this.model = model
        bindAccessible(model)
    }

    private fun bindAccessible(model: CollectionItemModel) {
        val accessible = ChildAccountCollectionManager.isNFTCollectionAccessible(model.collection.contractIdWithCollection())
        binding.countView.setVisible(accessible)
        binding.tvInaccessibleTag.setVisible(accessible.not())
    }
}

val diffCallback = object : DiffUtil.ItemCallback<CollectionItemModel>() {
    override fun areItemsTheSame(oldItem: CollectionItemModel, newItem: CollectionItemModel): Boolean {
        return oldItem.collection.contractName() == newItem.collection.contractName()
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: CollectionItemModel, newItem: CollectionItemModel): Boolean {
        return oldItem == newItem
    }
}