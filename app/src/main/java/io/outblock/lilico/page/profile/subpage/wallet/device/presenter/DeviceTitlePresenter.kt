package io.outblock.lilico.page.profile.subpage.wallet.device.presenter

import android.view.View
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.databinding.LayoutDeviceTitleItemBinding
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceTitle


class DeviceTitlePresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<DeviceTitle> {
    private val binding by lazy { LayoutDeviceTitleItemBinding.bind(view) }
    override fun bind(model: DeviceTitle) {
        binding.tvDeviceTitle.text = model.text
    }
}