package io.outblock.lilico.page.profile.subpage.wallet.device.presenter

import android.annotation.SuppressLint
import android.view.View
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.databinding.LayoutDeviceInfoItemBinding
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.page.profile.subpage.wallet.device.detail.DeviceInfoActivity
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceModel
import io.outblock.lilico.utils.findActivity
import io.outblock.lilico.utils.formatGMTToDate


class DeviceInfoPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<DeviceModel> {

    private val binding by lazy { LayoutDeviceInfoItemBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: DeviceModel) {
        val isCurrentDevice = DeviceInfoManager.isCurrentDevice(model.id)
        with(binding) {
            ivDeviceType.setImageResource(R.drawable.ic_device_type_phone)
            tvDeviceName.text = model.device_name
            tvDeviceOs.text = model.user_agent
            tvDeviceLocation.text = model.city + ", " + model.countryCode + " · " +
                    if (isCurrentDevice) {
                        "Online"
                    } else {
                        formatGMTToDate(model.updated_at)
                    }
            ivDeviceMark.setImageResource(
                if (isCurrentDevice) {
                    R.drawable.ic_circle_right_green
                } else {
                    R.drawable.ic_arrow_right
                }
            )
            clDeviceLayout.setOnClickListener {
                DeviceInfoActivity.launch(view.context, model)
            }
        }
    }

}