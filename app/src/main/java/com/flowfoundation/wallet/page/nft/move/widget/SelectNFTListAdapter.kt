package com.flowfoundation.wallet.page.nft.move.widget

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemSelectNftBinding
import com.flowfoundation.wallet.page.nft.move.SelectNFTViewModel
import com.flowfoundation.wallet.page.nft.move.model.NFTInfo
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.toast


class SelectNFTListAdapter(val viewModel: SelectNFTViewModel) : BaseAdapter<NFTInfo>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return SelectNFTItemViewHolder(parent.inflate(R.layout.item_select_nft), viewModel)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as SelectNFTItemViewHolder).bind(getItem(position))
    }
}

private class SelectNFTItemViewHolder(
    private val view: View,
    private val viewModel: SelectNFTViewModel
) : BaseViewHolder(view), BasePresenter<NFTInfo> {
    private val binding by lazy { ItemSelectNftBinding.bind(view) }
    private var nft: NFTInfo? = null

    init {
        view.setOnClickListener {
            nft?.id?.let {
                if (viewModel.isNFTSelected(it)) {
                    viewModel.unSelectNFT(it)
                    changeSelectStatus(false)
                } else {
                    if (viewModel.isSelectedToLimit()) {
                        toast(R.string.nft_move_limit)
                        return@setOnClickListener
                    }
                    viewModel.selectNFT(it)
                    changeSelectStatus(true)
                }
            }
        }
    }

    private fun changeSelectStatus(isSelect: Boolean) {
        with(binding) {
            ivCheckBox.setImageResource(if (isSelect) R.drawable.ic_check_round else R.drawable.ic_check_normal_white)
            viewCover.setVisible(isSelect)
        }
    }

    override fun bind(model: NFTInfo) {
        this.nft = model
        with(binding) {
            Glide.with(ivNftImage).load(model.cover).transform(RoundedCorners(16.dp2px().toInt()))
                .placeholder(R.drawable.ic_placeholder).into(ivNftImage)
        }
        changeSelectStatus(false)
    }

}
