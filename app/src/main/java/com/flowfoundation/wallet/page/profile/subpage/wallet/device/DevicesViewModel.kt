package com.flowfoundation.wallet.page.profile.subpage.wallet.device

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.nftco.flow.sdk.FlowAddress
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccount
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceKeyModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceTitle
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.viewModelIOScope
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
            val keyDeviceList = infoResponse.data.result?.filter { it.backupInfo != null && it.backupInfo.type < 0 } ?: emptyList()
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
                        val currentKey = CryptoProviderManager.getCurrentCryptoProvider()?.getPublicKey()
                        val keyId = if (unRevokedDevice.publicKey.base16Value == currentKey) null else unRevokedDevice.id
                        deviceList.add(
                            DeviceKeyModel(
                                deviceId = device.id,
                                keyId = keyId,
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
                        devices.add(DeviceTitle(R.string.current_device.res2String()))
                        devices.addAll(currentDevice)
                    }
                    val otherDevice = deviceList.filterList {
                        DeviceInfoManager.isCurrentDevice(deviceId).not()
                    }
                    if (otherDevice.isNotEmpty()) {
                        devices.add(DeviceTitle(R.string.other_devices.res2String()))
                        devices.addAll(otherDevice)
                    }
                    devicesLiveData.postValue(devices)
                }
            }
        }
    }
}