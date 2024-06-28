package com.flowfoundation.wallet.page.notification.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.notification.model.WalletNotification
import com.flowfoundation.wallet.page.notification.presenter.NotificationItemPresenter


class NotificationItemAdapter: BaseAdapter<WalletNotification>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return NotificationItemPresenter(parent.inflate(R.layout.item_wallet_notification))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as NotificationItemPresenter).bind(getData()[position])
    }
}