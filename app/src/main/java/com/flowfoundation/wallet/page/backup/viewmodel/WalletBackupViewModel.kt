package com.flowfoundation.wallet.page.backup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountKeyManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccount
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.backup.BackupListManager
import com.flowfoundation.wallet.page.backup.model.BackupKey
import com.flowfoundation.wallet.page.backup.model.BackupListTitle
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceKeyModel
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.viewModelIOScope
import org.onflow.flow.models.FlowAddress


class WalletBackupViewModel : ViewModel(), OnTransactionStateChange {

    val devicesLiveData = MutableLiveData<List<Any>>()
    val backupListLiveData = MutableLiveData<List<Any>>()
    val seedPhraseListLiveData = MutableLiveData<List<Any>>()

    private val devices = mutableListOf<Any>()

    private val backupList = mutableListOf<Any>()

    private val seedPhraseList = mutableListOf<Any>()

    private val TAG = "WalletBackupViewModel"

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun loadData() {
        loadBackupList()
        loadDevices()
    }

    private fun loadBackupList() {
        viewModelIOScope(this) {
            try {
                logd(TAG, "Loading backup list...")
                val service = retrofit().create(ApiService::class.java)
                val response = service.getKeyDeviceInfo()
                logd(TAG, "API Response: ${response.status}")

                val backupKeyList = response.data.result?.filter {
                    it.backupInfo != null && it.backupInfo.type >= 0
                }
                logd(TAG, "Filtered backup key list size: ${backupKeyList?.size}")

                if (backupKeyList.isNullOrEmpty()) {
                    logd(TAG, "No backup keys found, clearing lists")
                    BackupListManager.clear()
                    backupListLiveData.postValue(emptyList())
                    seedPhraseListLiveData.postValue(emptyList())
                    return@viewModelIOScope
                }

                val account = FlowAddress(
                    WalletManager.wallet()?.walletAddress().orEmpty()
                ).lastBlockAccount()
                val currentKey = CryptoProviderManager.getCurrentCryptoProvider()?.getPublicKey()
                val keys = account.keys ?: emptyList()
                logd(TAG, "Account keys count: ${keys.size}")

                uiScope {
                    val keyList = backupKeyList.mapNotNull { info ->
                        logd(TAG, "Processing backup key - API pubKey: '${info.pubKey.publicKey}'")

                        val matchingKey = keys.lastOrNull { accountKey ->
                            val accountPubKey = accountKey.publicKey.removePrefix("0x")
                            val apiPubKey = info.pubKey.publicKey.removePrefix("0x")
                            val isRevoked = accountKey.revoked

                            logd(
                                TAG,
                                "  Comparing normalized keys - API: '$apiPubKey', Account: '$accountPubKey', revoked: $isRevoked"
                            )
                            logd(TAG, "  Normalized keys match: ${apiPubKey == accountPubKey}")

                            apiPubKey == accountPubKey && !isRevoked
                        }

                        if (matchingKey != null) {
                            logd(
                                TAG,
                                "  ✅ MATCH FOUND! Creating BackupKey with keyId: ${matchingKey.index}"
                            )
                            BackupKey(
                                matchingKey.index.toInt(),
                                info,
                                isRevoking = false
                            )
                        } else {
                            logd(TAG, "  ❌ NO MATCH - skipping this backup key")
                            null
                        }
                    }
                    logd(TAG, "Mapped key list size: ${keyList.size}")

                    // Log current key for reference
                    logd(TAG, "Current device key: '$currentKey'")

                    // Log all account keys for debugging
                    logd(TAG, "All account keys:")
                    keys.forEachIndexed { index, key ->
                        logd(
                            TAG,
                            "  [$index] pubKey: '${key.publicKey}', index: ${key.index}, revoked: ${key.revoked}"
                        )
                    }

                    val multiKeyList = keyList.filter {
                        it.info?.backupInfo?.type != BackupType.FULL_WEIGHT_SEED_PHRASE.index
                    }
                    logd(TAG, "Multi backup keys: ${multiKeyList.size}")

                    BackupListManager.setBackupTypeList(multiKeyList)
                    backupList.clear()
                    backupList.addAll(multiKeyList)
                    if (backupList.size > 0) {
                        backupList.add(0, BackupListTitle.MULTI_BACKUP)
                    }
                    logd(TAG, "Final backup list size: ${backupList.size}")
                    backupListLiveData.postValue(backupList)

                    seedPhraseList.clear()
                    val seedPhraseKeys = keyList.filter {
                        it.info?.backupInfo?.type == BackupType.FULL_WEIGHT_SEED_PHRASE.index
                    }
                    seedPhraseList.addAll(seedPhraseKeys)
                    if (seedPhraseList.isNotEmpty()) {
                        seedPhraseList.add(0, BackupListTitle.FULL_WEIGHT_SEED_PHRASE)
                    }
                    logd(TAG, "Final seed phrase list size: ${seedPhraseList.size}")
                    seedPhraseListLiveData.postValue(seedPhraseList)
                }
            } catch (e: Exception) {
                logd(TAG, "Error loading backup list")
                uiScope {
                    backupListLiveData.postValue(emptyList())
                    seedPhraseListLiveData.postValue(emptyList())
                }
            }
        }
    }

    private fun loadDevices() {
        viewModelIOScope(this) {
            try {
                logd(TAG, "Loading device list...")
                val service = retrofit().create(ApiService::class.java)
                val response = service.getDeviceList()
                val deviceInfoList = response.data ?: emptyList()
                logd(TAG, "Device info list size: ${deviceInfoList.size}")

                val infoResponse = service.getKeyDeviceInfo()
                val keyDeviceList = infoResponse.data.result?.filter {
                    it.backupInfo != null && it.backupInfo.type < 0
                } ?: emptyList()
                logd(TAG, "Key device list size: ${keyDeviceList.size}")

                val account = FlowAddress(
                    WalletManager.wallet()?.walletAddress().orEmpty()
                ).lastBlockAccount()
                val keys = account.keys ?: emptyList()
                val deviceList = mutableListOf<DeviceKeyModel>()

                deviceInfoList.forEach { device ->
                    val deviceKey = keyDeviceList.lastOrNull { it.device?.id == device.id }
                    if (deviceKey != null) {
                        val unRevokedDevice = keys.firstOrNull { accountKey ->
                            val accountPubKey = accountKey.publicKey.removePrefix("0x")
                            val devicePubKey = deviceKey.pubKey.publicKey.removePrefix("0x")
                            val isRevoked = accountKey.revoked
                            accountPubKey == devicePubKey && !isRevoked
                        }

                        if (unRevokedDevice != null) {
                            val currentKey =
                                CryptoProviderManager.getCurrentCryptoProvider()?.getPublicKey()

                            val devicePubKey = unRevokedDevice.publicKey.removePrefix("0x")
                            val currentKeyNormalized = currentKey?.removePrefix("0x")
                            val isSameKeyAsCurrentDevice = devicePubKey == currentKeyNormalized

                            val keyId = if (isSameKeyAsCurrentDevice) {
                                null
                            } else {
                                unRevokedDevice.index.toInt()
                            }

                            // Always add to device list (main branch behavior), regardless of keyId
                            deviceList.add(
                                DeviceKeyModel(
                                    deviceId = device.id,
                                    keyId = keyId,
                                    deviceModel = device
                                )
                            )
                        } else {
                            logd(TAG, "    ❌ NO MATCHING ACCOUNT KEY found for device")
                        }
                    } else {
                        logd(
                            TAG,
                            "  ❌ NO DEVICE KEY found in keyDeviceList for device ID: ${device.id}"
                        )

                        // Debug: show all available device IDs in keyDeviceList
                        val availableDeviceIds = keyDeviceList.mapNotNull { it.device?.id }
                        logd(TAG, "    Available device IDs in keyDeviceList: $availableDeviceIds")
                    }
                }
                logd(TAG, "Final device list size: ${deviceList.size}")

                uiScope {
                    if (deviceList.isNotEmpty()) {
                        devices.clear()
                        val currentDevice = deviceList.filter {
                            DeviceInfoManager.isCurrentDevice(it.deviceId)
                        }
                        if (currentDevice.isNotEmpty()) {
                            devices.add(BackupListTitle.DEVICE_BACKUP)
                            devices.addAll(currentDevice)
                        }
                        val otherDevice = deviceList.filter {
                            !DeviceInfoManager.isCurrentDevice(it.deviceId)
                        }.take(2)
                        if (otherDevice.isNotEmpty()) {
                            devices.add(BackupListTitle.OTHER_DEVICES)
                            devices.addAll(otherDevice)
                        }
                        logd(TAG, "Final devices list for UI: ${devices.size}")
                        devicesLiveData.postValue(devices)
                    } else {
                        logd(TAG, "No devices to display")
                        devicesLiveData.postValue(emptyList())
                    }
                }
            } catch (e: Exception) {
                logd(TAG, "Error loading devices")
                uiScope {
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