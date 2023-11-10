package io.outblock.lilico.manager.account

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import io.outblock.lilico.R
import io.outblock.lilico.firebase.auth.isAnonymousSignIn
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.model.DeviceInfoRequest
import io.outblock.lilico.network.model.LocationInfo
import io.outblock.lilico.network.model.UpdateDeviceParams
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.utils.Env
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.loge


@SuppressLint("HardwareIds")
object DeviceInfoManager {

    private val currentDeviceId by lazy {
        Settings.Secure.getString(Env.getApp().contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val deviceName by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private val userAgent by lazy {
        "${R.string.app_short_name.res2String()} Android ${Build.VERSION.RELEASE}"
    }

    fun isCurrentDevice(id: String): Boolean {
        return currentDeviceId == id
    }

    suspend fun getDeviceInfoRequest(): DeviceInfoRequest? {
        return try {
            val service = retrofit().create(ApiService::class.java)
            val response = service.getDeviceLocation()
            response.data?.createDeviceInfo()
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

    private fun LocationInfo.createDeviceInfo(): DeviceInfoRequest {
        return DeviceInfoRequest(
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
