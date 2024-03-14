package com.flowfoundation.wallet.page.nft.nftlist.presenter

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutNftEmptyBinding
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.page.main.MainActivityViewModel
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import jp.wasabeef.glide.transformations.BlurTransformation

class NftEmptyPresenter(
    private val binding: LayoutNftEmptyBinding,
) {

    init {
        with(binding) {
            getNewButton.setOnClickListener {
                ViewModelProvider(findActivity(binding.root) as FragmentActivity)[MainActivityViewModel::class.java].changeTab(HomeTab.EXPLORE)
            }
            Glide.with(backgroundImage).load(R.drawable.bg_empty).transform(BlurTransformation(10, 20)).into(backgroundImage)
        }
    }

    fun setVisible(isVisible: Boolean) {
        binding.root.setVisible(isVisible)
    }
}