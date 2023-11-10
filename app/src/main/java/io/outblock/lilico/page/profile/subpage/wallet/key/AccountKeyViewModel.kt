package io.outblock.lilico.page.profile.subpage.wallet.key

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.FlowPublicKey
import io.outblock.lilico.manager.account.AccountKeyManager
import io.outblock.lilico.manager.flowjvm.lastBlockAccount
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.profile.subpage.wallet.key.model.AccountKey
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.utils.viewModelIOScope
import io.outblock.lilico.wallet.getPublicKey
import java.util.concurrent.CopyOnWriteArrayList


class AccountKeyViewModel : ViewModel(), OnTransactionStateChange {
    val keyListLiveData = MutableLiveData<List<AccountKey>>()

    private val keyList = CopyOnWriteArrayList<AccountKey>()

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun load() {
        viewModelIOScope(this) {
            val account = FlowAddress(WalletManager.selectedWalletAddress()).lastBlockAccount()
            if (account == null || account.keys.isEmpty()) {
                keyListLiveData.postValue(emptyList())
                return@viewModelIOScope
            }
            uiScope {
                keyList.addAll(account.keys.map {
                    AccountKey(
                        it.id,
                        it.publicKey,
                        it.signAlgo,
                        it.hashAlgo,
                        it.weight,
                        it.sequenceNumber,
                        it.revoked,
                        isRevoking = false,
                        isCurrentDevice = isCurrentDevice(it.publicKey),
                        deviceName = ""
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
                    keyDeviceInfo.find { it.pubKey?.publicKey == accountKey.publicKey.base16Value }
                        ?.let {
                            accountKey.deviceName = it.device?.device_name ?: ""
                        }
                }
                keyListLiveData.value = keyList
            }
        }

    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.firstOrNull { it.type == TransactionState.TYPE_REVOKE_KEY }
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

    private fun isCurrentDevice(publicKey: FlowPublicKey): Boolean {
        return getPublicKey() == publicKey.base16Value
    }

}