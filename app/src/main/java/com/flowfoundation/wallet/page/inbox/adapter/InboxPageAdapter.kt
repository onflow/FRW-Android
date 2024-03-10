package com.flowfoundation.wallet.page.inbox.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.flowfoundation.wallet.page.inbox.InboxListFragment

class InboxPageAdapter(
    fragmentManager: FragmentManager
) : FragmentStatePagerAdapter(fragmentManager) {

    override fun getCount(): Int = 2

    override fun getItem(position: Int): Fragment {
        return InboxListFragment.newInstance(position)
    }
}