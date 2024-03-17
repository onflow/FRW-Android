package com.flowfoundation.wallet.page.nft.nftlist.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import com.flowfoundation.wallet.page.nft.nftlist.NFTFragment
import com.flowfoundation.wallet.page.nft.nftlist.NftGridFragment
import com.flowfoundation.wallet.page.nft.nftlist.NftListFragment

class NftListPageAdapter(
    fragment: NFTFragment
) : FragmentStatePagerAdapter(fragment.childFragmentManager) {

    override fun getCount(): Int = 2

    override fun getItem(position: Int): Fragment {
        return when (position) {
            1 -> NftGridFragment.newInstance()
            else -> NftListFragment.newInstance()
        }
    }
}