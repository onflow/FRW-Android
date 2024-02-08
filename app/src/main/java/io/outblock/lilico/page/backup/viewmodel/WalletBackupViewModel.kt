package io.outblock.lilico.page.backup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowAddress
import io.outblock.lilico.manager.account.AccountKeyManager
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.flowjvm.lastBlockAccount
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.backup.BackupListManager
import io.outblock.lilico.page.backup.model.BackupKey
import io.outblock.lilico.page.backup.model.BackupListTitle
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceKeyModel
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.utils.viewModelIOScope
import okhttp3.internal.filterList


class WalletBackupViewModel : ViewModel(), OnTransactionStateChange {

    val devicesLiveData = MutableLiveData<List<Any>>()
    val backupListLiveData = MutableLiveData<List<Any>>()

    private val devices = mutableListOf<Any>()

    private val backupList = mutableListOf<Any>()

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun loadData() {
        loadBackupList()
        loadDevices()
    }

    private fun loadBackupList() {
        viewModelIOScope(this) {
            val service = retrofit().create(ApiService::class.java)
            val response = service.getKeyDeviceInfo()
            val backupKeyList =
                response.data.result?.filter { it.backupInfo != null && it.backupInfo.type >= 0 }
            if (backupKeyList.isNullOrEmpty()) {
                BackupListManager.clear()
                backupListLiveData.postValue(emptyList())
                return@viewModelIOScope
            }
            val account = FlowAddress(WalletManager.selectedWalletAddress()).lastBlockAccount()
            uiScope {
                val keys = account?.keys ?: emptyList()
                val keyList = backupKeyList.mapNotNull { info ->
                    keys.lastOrNull { info.pubKey.publicKey == it.publicKey.base16Value && it.revoked.not() }
                        ?.let {
                            BackupKey(
                                it.id,
                                info,
                                isRevoking = false
                            )
                        }
                }
                BackupListManager.setBackupTypeList(keyList)
                backupList.clear()
                backupList.addAll(keyList)
                if (backupList.size > 0) {
                    backupList.add(0, BackupListTitle.MULTI_BACKUP)
                }
                backupListLiveData.postValue(backupList)
            }
        }
    }

    private fun loadDevices() {
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
                        devices.add(BackupListTitle.DEVICE_BACKUP)
                        devices.addAll(currentDevice)
                    }
                    val otherDevice = deviceList.filterList {
                        DeviceInfoManager.isCurrentDevice(deviceId).not()
                    }.take(2)
                    if (otherDevice.isNotEmpty()) {
                        devices.add(BackupListTitle.OTHER_DEVICES)
                        devices.addAll(otherDevice)
                    }
                    devicesLiveData.postValue(devices)
                } else {
                    devicesLiveData.postValue(emptyList())
                }
            }
        }
    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_REVOKE_KEY }
        transaction?.let { state ->
            if (state.isSuccess()) {
                loadData()
            } else if (state.isProcessing()) {
                backupList.firstOrNull { (it is BackupKey) && it.keyId == AccountKeyManager.getRevokingIndexId() }
                    ?.let { key ->
                        backupList[backupList.indexOf(key)] = (key as BackupKey).copy(
                            isRevoking = state.isProcessing()
                        )
                        backupListLiveData.value = backupList
                    }
            }
        }
    }
}