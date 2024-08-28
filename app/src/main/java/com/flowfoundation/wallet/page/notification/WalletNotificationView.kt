package com.flowfoundation.wallet.page.notification

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.flowfoundation.wallet.databinding.ViewWalletNotificationBinding
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.page.notification.adapter.NotificationItemAdapter
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible

class WalletNotificationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val binding = ViewWalletNotificationBinding.inflate(LayoutInflater.from(context))
    private val itemAdapter by lazy { NotificationItemAdapter() }

    init {
        addView(binding.root)
        with(binding.vpNotification) {
            adapter = itemAdapter
            val padding = 16f.dp2px().toInt()
            addItemDecoration(object : RecyclerView.ItemDecoration(){
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.left = padding
                    outRect.right = padding
                }
            })
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    binding.tabLayout.onTabSelected(position)
                }
            })
        }
        onNotificationChange()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onNotificationChange() {
        val list = WalletNotificationManager.getNotificationList()
        itemAdapter.setNewDiffData(list)
        itemAdapter.notifyDataSetChanged()
        binding.tabLayout.setMaxCount(list.size)
        binding.tabLayout.setVisible(list.isNotEmpty())
    }
}