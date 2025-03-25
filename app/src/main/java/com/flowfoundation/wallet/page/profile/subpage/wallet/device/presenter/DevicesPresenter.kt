package com.flowfoundation.wallet.page.profile.subpage.wallet.device.presenter

import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityDevicesBinding
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.adapter.DevicesAdapter

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