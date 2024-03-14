package com.flowfoundation.wallet.page.transaction.record.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.network.model.TransferRecord
import com.flowfoundation.wallet.page.transaction.record.model.TransactionRecord
import com.flowfoundation.wallet.page.transaction.record.model.TransactionViewMoreModel
import com.flowfoundation.wallet.page.transaction.record.presenter.TransactionRecordItemPresenter
import com.flowfoundation.wallet.page.transaction.record.presenter.TransactionViewMoreItemPresenter
import com.flowfoundation.wallet.page.transaction.record.presenter.TransferRecordItemPresenter

private const val TYPE_TRANSACTION = 0
private const val TYPE_TRANSFER = 1
private const val TYPE_VIEW_MORE = 2
private const val TYPE_NONE = -1

class TransactionRecordListAdapter : BaseAdapter<Any>(diffCallback) {
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TransactionRecord -> TYPE_TRANSACTION
            is TransferRecord -> TYPE_TRANSFER
            is TransactionViewMoreModel -> TYPE_VIEW_MORE
            else -> TYPE_NONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TRANSACTION -> TransactionRecordItemPresenter(parent.inflate(R.layout.item_transaction_record))
            TYPE_TRANSFER -> TransferRecordItemPresenter(parent.inflate(R.layout.item_transfer_record))
            TYPE_VIEW_MORE -> TransactionViewMoreItemPresenter(parent.inflate(R.layout.item_transaction_view_more))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TransactionRecordItemPresenter -> holder.bind(getItem(position) as TransactionRecord)
            is TransferRecordItemPresenter -> holder.bind(getItem(position) as TransferRecord)
            is TransactionViewMoreItemPresenter -> holder.bind(getItem(position) as TransactionViewMoreModel)
        }
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is TransactionRecord && newItem is TransactionRecord) {
            return oldItem.transaction.hash == newItem.transaction.hash
        }
        if (oldItem is TransferRecord && newItem is TransferRecord) {
            return oldItem.txid == newItem.txid
        }
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is TransactionRecord && newItem is TransactionRecord) {
            return oldItem == newItem
        }

        if (oldItem is TransferRecord && newItem is TransferRecord) {
            return oldItem == newItem
        }
        return false
    }
}