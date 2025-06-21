package com.flowfoundation.wallet

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.RegisterRequest
import com.flowfoundation.wallet.network.retrofit
import org.onflow.flow.models.bytesToHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.utils.Env
import org.onflow.flow.models.SigningAlgorithm
import java.io.File

@RunWith(AndroidJUnit4::class)
class TestApi {

    @Test
    fun testRegister() {
        CoroutineScope(Dispatchers.IO).launch {
            val wallet = HDWallet("normal dune pole key case cradle unfold require tornado mercy hospital buyer", "")
            val privateKey = wallet.getDerivedKey(CoinType.ETHEREUM, 0, 0, 0)
            val publicKey = privateKey.publicKeyNist256p1.uncompressed().data().bytesToHex().removePrefix("04")

            val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
            val service = retrofit().create(ApiService::class.java)
            val user = service.register(
                RegisterRequest(
                    username = "ttt",
                    accountKey = AccountKey(publicKey = publicKey),
                    deviceInfo = deviceInfoRequest
                )
            )
            Log.w("user", user.toString())
        }
    }

    @Test
    fun testKeyStoreRegister() {
        CoroutineScope(Dispatchers.IO).launch {
            HDWallet("normal dune pole key case cradle unfold require tornado mercy hospital buyer", "")
            val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
            
            // Create storage for the new Flow Wallet Kit
            val baseDir = File(Env.getApp().filesDir, "wallet_test")
            val storage = FileSystemStorage(baseDir)
            
            // Use the current Flow Wallet Kit PrivateKey implementation
            val privateKey = PrivateKey.create(storage)
            val publicKeyBytes = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)
            val publicKey = publicKeyBytes?.bytesToHex()?.removePrefix("04") ?: ""
            
            val service = retrofit().create(ApiService::class.java)
            val user = service.register(
                RegisterRequest(
                    username = "ttt",
                    accountKey = AccountKey(publicKey = publicKey),
                    deviceInfo = deviceInfoRequest
                )
            )
            Log.w("user", user.toString())
        }
    }

    @Test
    fun testCreateWallet() {
        CoroutineScope(Dispatchers.IO).launch {
            val service = retrofit().create(ApiService::class.java)
            val resp = service.createWallet()
            Log.w("resp", resp.toString())
        }
    }
}