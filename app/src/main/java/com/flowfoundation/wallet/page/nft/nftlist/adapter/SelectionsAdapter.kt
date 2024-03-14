package com.flowfoundation.wallet.page.nft.nftlist.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftSelectionsBinding
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftdetail.NftDetailActivity
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.dp2px

class SelectionsAdapter : BaseAdapter<Nft>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ViewHolder(parent.inflate(R.layout.item_nft_selections))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }
}

private class ViewHolder(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<Nft> {
    private val binding by lazy { ItemNftSelectionsBinding.bind(view) }

    private val corners by lazy { 10.dp2px().toInt() }

    init {
        val size = (ScreenUtils.getScreenWidth() * 0.7f).toInt()
        view.layoutParams = ViewGroup.LayoutParams(size, size)
    }

    override fun bind(model: Nft) {
        with(binding) {
            Glide.with(imageView).load(model.cover()).transform(CenterCrop(), RoundedCorners(corners)).into(imageView)
        }
        view.setOnClickListener {
            NftDetailActivity.launch(view.context, model.uniqueId())
        }
    }
}