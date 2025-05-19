package com.flowfoundation.wallet.page.profile.subpage.wallet.device.adapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceKeyModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceTitle
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.presenter.DeviceTitlePresenter
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.presenter.DeviceInfoPresenter

class DevicesAdapter : BaseAdapter<Any>(devicesDiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DeviceTitle -> TYPE_TITLE
            is DeviceKeyModel -> TYPE_DEVICE
            else -> TYPE_NONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TITLE -> DeviceTitlePresenter(parent.inflate(R.layout.layout_device_title_item))
            TYPE_DEVICE -> DeviceInfoPresenter(parent.inflate(R.layout.layout_device_info_item))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DeviceTitlePresenter -> holder.bind(getItem(position) as DeviceTitle)
            is DeviceInfoPresenter -> holder.bind(getItem(position) as DeviceKeyModel)
        }
    }

    companion object {
        private const val TYPE_NONE = -1
        private const val TYPE_TITLE = 0
        private const val TYPE_DEVICE = 1
    }
}

val devicesDiffCallback = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is DeviceKeyModel && newItem is DeviceKeyModel) {
            return oldItem.deviceId == newItem.deviceId
        }
        return oldItem == newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return oldItem == newItem
    }

}