package com.flowfoundation.wallet.page.main.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.explore.ExploreFragment
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.nft.nftlist.NFTFragment
import com.flowfoundation.wallet.page.profile.ProfileFragment
import com.flowfoundation.wallet.page.wallet.fragment.WalletHomeFragment

class MainPageAdapter(
    private val activity: MainActivity
) : FragmentStatePagerAdapter(activity.supportFragmentManager) {
    override fun getCount(): Int = if (WalletManager.isChildAccountSelected()) {
        3
    } else {
        4
    }

    override fun getItem(position: Int): Fragment {
        return when (position) {
            1 -> NFTFragment()
            2 -> if (WalletManager.isChildAccountSelected()) {
                ProfileFragment()
            } else {
                ExploreFragment()
            }
            3 -> ProfileFragment()
            else -> WalletHomeFragment()
        }
    }
}