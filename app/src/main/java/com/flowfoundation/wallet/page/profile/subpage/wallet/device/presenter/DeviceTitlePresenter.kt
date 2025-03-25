package com.flowfoundation.wallet.page.profile.subpage.wallet.device.presenter

import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutDeviceTitleItemBinding
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceTitle

class DeviceTitlePresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<DeviceTitle> {
    private val binding by lazy { LayoutDeviceTitleItemBinding.bind(view) }
    override fun bind(model: DeviceTitle) {
        binding.tvDeviceTitle.text = model.text
    }
}