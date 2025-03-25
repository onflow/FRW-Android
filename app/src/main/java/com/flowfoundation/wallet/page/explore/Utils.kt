package com.flowfoundation.wallet.page.explore

import androidx.recyclerview.widget.DiffUtil
import com.flowfoundation.wallet.database.WebviewRecord

val exploreRecentDiffCallback = object : DiffUtil.ItemCallback<WebviewRecord>() {
    override fun areItemsTheSame(oldItem: WebviewRecord, newItem: WebviewRecord): Boolean {
        return oldItem.url == newItem.url
    }

    override fun areContentsTheSame(oldItem: WebviewRecord, newItem: WebviewRecord): Boolean {
        return oldItem == newItem
    }
}