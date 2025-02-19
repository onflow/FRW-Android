package com.flowfoundation.wallet.page.main.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import com.flowfoundation.wallet.page.explore.ExploreFragment
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.nft.nftlist.NFTFragment
import com.flowfoundation.wallet.page.profile.ProfileFragment
import com.flowfoundation.wallet.page.transaction.record.TransactionRecordFragment
import com.flowfoundation.wallet.page.wallet.fragment.WalletHomeFragment

class MainPageAdapter(
    private val activity: MainActivity
) : FragmentStatePagerAdapter(activity.supportFragmentManager) {
    override fun getCount(): Int = 5

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> WalletHomeFragment() // ✅ Home tab
            1 -> NFTFragment()        // ✅ NFT tab
            2 -> ExploreFragment()    // ✅ Explore tab
            3 -> ProfileFragment()    // ✅ Profile tab
            4 -> TransactionRecordFragment() // ✅ Activity tab
            else -> throw IllegalStateException("Unexpected position: $position") // 🚀 Debugging
        }
    }
}