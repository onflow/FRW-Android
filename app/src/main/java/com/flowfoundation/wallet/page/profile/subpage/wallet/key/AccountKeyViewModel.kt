package com.flowfoundation.wallet.page.profile.subpage.wallet.key

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountKeyManager
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccount
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.model.AccountKey
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.nftco.flow.sdk.FlowAddress
import org.onflow.flow.models.AccountPublicKey
import java.util.concurrent.CopyOnWriteArrayList


class AccountKeyViewModel : ViewModel(), OnTransactionStateChange {
    val keyListLiveData = MutableLiveData<List<AccountKey>>()

    private val keyList = CopyOnWriteArrayList<AccountKey>()

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun load() {
        viewModelIOScope(this) {
            val account =
                FlowAddress(WalletManager.wallet()?.walletAddress().orEmpty()).lastBlockAccount()
            if (account == null || account.keys.isEmpty()) {
                keyListLiveData.postValue(emptyList())
                return@viewModelIOScope
            }

            uiScope {
                keyList.addAll(account.keys.map {
                    val publicKey = AccountPublicKey(
                        it.id.toString(), it.publicKey.base16Value, it.signAlgo, it.hashAlgo,
                        it.sequenceNumber.toString(), it.weight.toString(), it.revoked
                    ) //to-do
                    AccountKey(
                        it.id,
                        publicKey,
                        it.signAlgo,
                        it.hashAlgo,
                        it.weight,
                        it.sequenceNumber,
                        it.revoked,
                        isRevoking = false,
                        isCurrentDevice = isCurrentDevice(publicKey),
                        deviceName = "",
                        backupType = -1,
                        deviceType = -1,
                    )
                })
                keyListLiveData.postValue(keyList)
                loadDeviceInfo()
            }
        }
    }

    private fun loadDeviceInfo() {
        ioScope {
            val service = retrofit().create(ApiService::class.java)
            val response = service.getKeyDeviceInfo()
            val keyDeviceInfo = response.data.result ?: emptyList()
            uiScope {
                keyList.forEach { accountKey ->
                    keyDeviceInfo.find { it.pubKey.publicKey == accountKey.publicKey.publicKey }
                        ?.let {
                            accountKey.deviceName =
                                it.backupInfo?.name.takeIf { name -> !name.isNullOrEmpty() }
                                    ?: it.device?.device_name ?: ""
                            accountKey.deviceType = it.device?.device_type ?: -1
                            accountKey.backupType = it.backupInfo?.type ?: -1
                        }
                }
                keyListLiveData.value = keyList
            }
        }

    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_REVOKE_KEY }
        transaction?.let { state ->
            keyList.firstOrNull { it.id == AccountKeyManager.getRevokingIndexId() }?.let { key ->
                keyList[keyList.indexOf(key)] = key.copy(
                    isRevoking = state.isProcessing(),
                    revoked = state.isSuccess()
                )
                keyListLiveData.value = keyList
            }
        }

    }

    private fun isCurrentDevice(publicKey: AccountPublicKey): Boolean {
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
        return cryptoProvider?.getPublicKey() == publicKey.publicKey
    }

}