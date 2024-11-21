package com.flowfoundation.wallet.manager.account

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.manager.walletconnect.model.WCDeviceInfo
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.DeviceInfoRequest
import com.flowfoundation.wallet.network.model.LocationInfo
import com.flowfoundation.wallet.network.model.UpdateDeviceParams
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.loge


@SuppressLint("HardwareIds")
object DeviceInfoManager {

    private val currentDeviceId by lazy {
        Settings.Secure.getString(Env.getApp().contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val deviceName by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private val userAgent by lazy {
        "${R.string.app_name.res2String()} Android ${Build.VERSION.RELEASE}"
    }

    fun getDeviceID(): String {
        return currentDeviceId
    }

    fun getDeviceInfoUserAgent(): String {
        return userAgent
    }

    fun isCurrentDevice(id: String): Boolean {
        return currentDeviceId == id
    }

    fun getDeviceInfoRequest(): DeviceInfoRequest {
        return DeviceInfoRequest(
            device_id = currentDeviceId,
            name = deviceName,
            type = "1",
            user_agent = userAgent
        )
    }

    suspend fun getWCDeviceInfo(): WCDeviceInfo? {
        return try {
            val service = retrofit().create(ApiService::class.java)
            val response = service.getDeviceLocation()
            response.data?.createWCDeviceInfo()
        } catch (e: Exception) {
            loge(e)
            null
        }
    }

    suspend fun updateDeviceInfo() {
        if (isAnonymousSignIn()) {
            return
        }
        try {
            val service = retrofit().create(ApiService::class.java)
            service.updateDeviceInfo(UpdateDeviceParams(currentDeviceId))
        } catch (e: Exception) {
            loge(e)
        }
    }

    private fun LocationInfo.createWCDeviceInfo(): WCDeviceInfo {
        return WCDeviceInfo(
            this.city,
            this.country,
            this.countryCode,
            currentDeviceId,
            this.query,
            this.isp,
            this.lat,
            this.lon,
            deviceName,
            this.org,
            this.regionName,
            "1",
            userAgent,
            this.zip,
        )
    }
}
