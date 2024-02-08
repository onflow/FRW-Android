package io.outblock.lilico.page.profile.subpage.wallet.device

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowAddress
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.flowjvm.lastBlockAccount
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceKeyModel
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceTitle
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.utils.viewModelIOScope
import okhttp3.internal.filterList


class DevicesViewModel : ViewModel() {
    val devicesLiveData = MutableLiveData<List<Any>>()
    private val devices = mutableListOf<Any>()

    fun load() {
        viewModelIOScope(this) {
            val service = retrofit().create(ApiService::class.java)
            val response = service.getDeviceList()
            val deviceInfoList = response.data ?: emptyList()
            val infoResponse = service.getKeyDeviceInfo()
            val keyDeviceList = infoResponse.data.result ?: emptyList()
            val account = FlowAddress(WalletManager.selectedWalletAddress()).lastBlockAccount()
            val keys = account?.keys ?: emptyList()
            val deviceList = mutableListOf<DeviceKeyModel>()
            deviceInfoList.forEach { device ->
                val deviceKey = keyDeviceList.lastOrNull { it.device?.id == device.id }
                if (deviceKey != null) {
                    val unRevokedDevice = keys.firstOrNull {
                        it.publicKey.base16Value ==
                                deviceKey.pubKey.publicKey && it.revoked.not()
                    }
                    if (unRevokedDevice != null) {
                        deviceList.add(
                            DeviceKeyModel(
                                deviceId = device.id,
                                keyId = unRevokedDevice.id,
                                deviceModel = device
                            )
                        )
                    }
                }
            }
            uiScope {
                if (deviceList.isEmpty().not()) {
                    devices.clear()
                    val currentDevice =
                        deviceList.filterList { DeviceInfoManager.isCurrentDevice(deviceId) }
                    if (currentDevice.isNotEmpty()) {
                        devices.add(DeviceTitle("Current Device"))
                        devices.addAll(currentDevice)
                    }
                    val otherDevice = deviceList.filterList {
                        DeviceInfoManager.isCurrentDevice(deviceId).not()
                    }
                    if (otherDevice.isNotEmpty()) {
                        devices.add(DeviceTitle("Other Devices"))
                        devices.addAll(otherDevice)
                    }
                    devicesLiveData.postValue(devices)
                }
            }
        }
    }
}