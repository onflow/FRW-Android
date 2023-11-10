package io.outblock.lilico.page.profile.subpage.wallet.device.presenter

import androidx.recyclerview.widget.LinearLayoutManager
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.ActivityDevicesBinding
import io.outblock.lilico.page.profile.subpage.wallet.device.adapter.DevicesAdapter
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceModel


class DevicesPresenter(
    binding: ActivityDevicesBinding,
) : BasePresenter<List<Any>> {

    private val devicesAdapter by lazy { DevicesAdapter() }

    init {
        with(binding.rvDeviceList) {
            adapter = devicesAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    override fun bind(model: List<Any>) {
        devicesAdapter.setNewDiffData(model)
    }
}