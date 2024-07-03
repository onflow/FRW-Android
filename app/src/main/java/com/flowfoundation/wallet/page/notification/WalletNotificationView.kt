package com.flowfoundation.wallet.page.notification

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.ViewWalletNotificationBinding
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.page.notification.adapter.NotificationItemAdapter
import com.google.android.material.tabs.TabLayoutMediator

class WalletNotificationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val binding = ViewWalletNotificationBinding.inflate(LayoutInflater.from(context))
    private val itemAdapter by lazy { NotificationItemAdapter() }

    init {
        addView(binding.root)
        with(binding.vpNotification) {
            adapter = itemAdapter
        }
        TabLayoutMediator(binding.tabNotification, binding.vpNotification) { _, _ -> }.attach()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onNotificationChange() {
        val list = WalletNotificationManager.getNotificationList()
        itemAdapter.setNewDiffData(list)
        itemAdapter.notifyDataSetChanged()
    }
}