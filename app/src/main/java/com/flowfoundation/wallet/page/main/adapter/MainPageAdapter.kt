package com.flowfoundation.wallet.page.main.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import com.flowfoundation.wallet.page.explore.ExploreFragment
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.nft.nftlist.NFTFragment
import com.flowfoundation.wallet.page.profile.SettingFragment
import com.flowfoundation.wallet.page.wallet.WalletFragment

class MainPageAdapter(
    activity: MainActivity
) : FragmentStatePagerAdapter(activity.supportFragmentManager) {
    // Activity tab (index 4) is now handled by React Native, not ViewPager
    override fun getCount(): Int = 4

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> WalletFragment()
            1 -> NFTFragment()
            2 -> ExploreFragment()
            3 -> SettingFragment()
            else -> throw IllegalStateException("Unexpected position: $position")
        }
    }
}