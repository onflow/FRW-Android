package com.flowfoundation.wallet.page.profile.subpage.wallet.device.presenter

import android.annotation.SuppressLint
import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutDeviceInfoItemBinding
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.detail.DeviceInfoActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceKeyModel
import com.flowfoundation.wallet.utils.formatGMTToDate


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