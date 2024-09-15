package com.flowfoundation.wallet.page.backup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowAddress
import com.flowfoundation.wallet.manager.account.AccountKeyManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccount
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.backup.BackupListManager
import com.flowfoundation.wallet.page.backup.model.BackupKey
import com.flowfoundation.wallet.page.backup.model.BackupListTitle
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceKeyModel
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.viewModelIOScope
import okhttp3.internal.filterList


class WalletBackupViewModel : ViewModel(), OnTransactionStateChange {

    val devicesLiveData = MutableLiveData<List<Any>>()
    val backupListLiveData = MutableLiveData<List<Any>>()
    val seedPhraseListLiveData = MutableLiveData<List<Any>>()

    private val devices = mutableListOf<Any>()

    private val backupList = mutableListOf<Any>()

    private val seedPhraseList = mutableListOf<Any>()

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
                seedPhraseListLiveData.postValue(emptyList())
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
                val multiKeyList = keyList.filterList { info?.backupInfo?.type != BackupType.FULL_WEIGHT_SEED_PHRASE.index}
                BackupListManager.setBackupTypeList(multiKeyList)
                backupList.clear()
                backupList.addAll(multiKeyList)
                if (backupList.size > 0) {
                    backupList.add(0, BackupListTitle.MULTI_BACKUP)
                }
                backupListLiveData.postValue(backupList)
                seedPhraseList.clear()
                seedPhraseList.addAll(keyList.filterList { info?.backupInfo?.type == BackupType.FULL_WEIGHT_SEED_PHRASE.index})
                if (seedPhraseList.isNotEmpty()) {
                    seedPhraseList.add(0, BackupListTitle.FULL_WEIGHT_SEED_PHRASE)
                }
                seedPhraseListLiveData.postValue(seedPhraseList)
            }
        }
    }

    private fun loadDevices() {
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
                seedPhraseList.firstOrNull { (it is BackupKey) && it.keyId == AccountKeyManager.getRevokingIndexId() }
                    ?.let { key ->
                        seedPhraseList[seedPhraseList.indexOf(key)] = (key as BackupKey).copy(
                            isRevoking = state.isProcessing()
                        )
                        seedPhraseListLiveData.value = seedPhraseList
                    }
            }
        }
    }
}