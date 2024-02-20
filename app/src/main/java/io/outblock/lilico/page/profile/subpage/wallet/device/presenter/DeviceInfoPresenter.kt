package io.outblock.lilico.page.profile.subpage.wallet.device.presenter

import android.annotation.SuppressLint
import android.view.View
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.databinding.LayoutDeviceInfoItemBinding
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.page.profile.subpage.wallet.device.detail.DeviceInfoActivity
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceKeyModel
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceModel
import io.outblock.lilico.utils.findActivity
import io.outblock.lilico.utils.formatGMTToDate


class DeviceInfoPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<DeviceKeyModel> {

    private val binding by lazy { LayoutDeviceInfoItemBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: DeviceKeyModel) {
        val isCurrentDevice = DeviceInfoManager.isCurrentDevice(model.deviceId)
        with(binding) {
            ivDeviceType.setImageResource(R.drawable.ic_device_type_phone)
            tvDeviceName.text = model.deviceModel.device_name
            tvDeviceOs.text = model.deviceModel.user_agent
            tvDeviceLocation.text = cityInfo(model.deviceModel.city, model.deviceModel.countryCode) +
                    if (isCurrentDevice) {
                        "Online"
                    } else {
                        formatGMTToDate(model.deviceModel.updated_at)
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

    private fun cityInfo(city: String, country: String): String {
        val sb = StringBuilder()
        if (city.isNotBlank()) {
            sb.append(city)
            if (country.isNotBlank()) {
                sb.append(", ").append(country)
            }
            sb.append(" · ")
        } else {
            if (country.isNotBlank()) {
                sb.append(country).append(" · ")
            }
        }
        return sb.toString()
    }
}