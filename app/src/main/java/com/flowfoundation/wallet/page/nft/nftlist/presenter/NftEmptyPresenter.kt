package com.flowfoundation.wallet.page.nft.nftlist.presenter

import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutNftEmptyBinding
import com.flowfoundation.wallet.utils.extensions.setVisible
import jp.wasabeef.glide.transformations.BlurTransformation

class NftEmptyPresenter(
    private val binding: LayoutNftEmptyBinding,
) {

    init {
        with(binding) {
            Glide.with(backgroundImage).load(R.drawable.bg_empty).transform(BlurTransformation(10, 20)).into(backgroundImage)
        }
    }

    fun setVisible(isVisible: Boolean) {
        binding.root.setVisible(isVisible)
    }
}