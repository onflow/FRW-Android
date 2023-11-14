package io.outblock.lilico

import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nftco.flow.sdk.bytesToHex
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.RegisterRequest
import io.outblock.lilico.network.retrofit
import io.outblock.wallet.KeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet

@RunWith(AndroidJUnit4::class)
class TestApi {

    @Test
    fun testRegister() {
        CoroutineScope(Dispatchers.IO).launch {
            val wallet = HDWallet("normal dune pole key case cradle unfold require tornado mercy hospital buyer", "")
            val privateKey = wallet.getDerivedKey(CoinType.FLOW, 0, 0, 0)
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
            val wallet = HDWallet("normal dune pole key case cradle unfold require tornado mercy hospital buyer", "")
//            val privateKey = wallet.getDerivedKey(CoinType.FLOW, 0, 0, 0)
//            val publicKey = privateKey.publicKeyNist256p1.uncompressed().data().bytesToHex().removePrefix("04")

            val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
            val keyPair = KeyManager.generateKeyWithPrefix("test_user")
            val publicKey = keyPair.public.encoded.bytesToHex().removePrefix("04")
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