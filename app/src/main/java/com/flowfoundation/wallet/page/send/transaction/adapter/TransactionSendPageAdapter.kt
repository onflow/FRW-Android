package com.flowfoundation.wallet.page.send.transaction.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.flowfoundation.wallet.page.send.transaction.AddressPageFragment

class TransactionSendPageAdapter(
    fragmentManager: FragmentManager
) : FragmentStatePagerAdapter(fragmentManager) {

    override fun getCount(): Int = 3

    override fun getItem(position: Int): Fragment {
        return AddressPageFragment.newInstance(position)
    }
}