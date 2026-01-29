package com.flowfoundation.wallet.page.main.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import com.flowfoundation.wallet.page.explore.ExploreFragment
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.nft.nftlist.NFTFragment
import com.flowfoundation.wallet.page.profile.SettingFragment
import com.flowfoundation.wallet.page.wallet.WalletFragment
import com.flowfoundation.wallet.reactnative.ReactNativeActivityFragment

class MainPageAdapter(
    activity: MainActivity
) : FragmentStatePagerAdapter(activity.supportFragmentManager) {
    override fun getCount(): Int = 5

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> WalletFragment()
            1 -> NFTFragment()
            2 -> ExploreFragment()
            3 -> SettingFragment()
            4 -> ReactNativeActivityFragment()
            else -> throw IllegalStateException("Unexpected position: $position")
        }
    }
}