package com.flowfoundation.wallet

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import org.onflow.flow.models.bytesToHex
import org.onflow.flow.models.hexToBytes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import wallet.core.jni.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class TestWallet {

    init {
        System.loadLibrary("TrustWalletCore")
    }

    @Test
    fun testCreateMnemonic() {
        Log.w("method", "testCreateMnemonic()")
        val wallet = HDWallet(128, "")
        Log.w("mnemonic", wallet.mnemonic())
        assertEquals(Mnemonic.isValid(wallet.mnemonic()), true)
    }

    @Test
    fun testCreateWalletFromMnemonic() {
        Log.w("method", "testCreateMnemonic()")
        val wallet = HDWallet(MNEMONIC, "")
        assertEquals(MNEMONIC, wallet.mnemonic())
    }

    @Test
    fun testP256_SHA256() {
        val privateKeyData = PRIVATE_KEY.hexToBytes()
        val privateKey = PrivateKey(privateKeyData)
        assertEquals("638dc9ad0eee91d09249f0fd7c5323a11600e20d5b9105b66b782a96236e74cf",
            privateKey.data().bytesToHex())
        val publicKey = privateKey.publicKeyNist256p1.uncompressed()
        assertEquals("04dbe5b4b4416ad9158339dd692002ceddab895e11bd87d90ce7e3e745efef28d2ad6e736fe3d57d52213f397a7ba9f0bc8c65620a872aefedbc1ddd74c605cf58",
        publicKey.data().bytesToHex())

        val data = "hello schnorr".encodeToByteArray()
        val hashedData = Hash.sha256(data)
        val signature = privateKey.sign(hashedData, Curve.NIST256P1)
        assertEquals(true, privateKey.publicKeyNist256p1.verify(signature, hashedData))
        assertEquals(true, privateKey.publicKeyNist256p1.verify(P256_SHA256_SIGNATURE.hexToBytes(), hashedData))
        assertEquals("0cd37adf53dc353eeb07321c765d81aedd11f34a6393de31bb15e2c5a07793c96ac54369d71a7e769dced55fc941d2f723538e1b31bf587e7f435e911222068b01",
            signature.bytesToHex())
    }

    @Test
    fun testP256_SHA3_256() {
        val privateKeyData = PRIVATE_KEY.hexToBytes()
        val privateKey = PrivateKey(privateKeyData)
        assertEquals("638dc9ad0eee91d09249f0fd7c5323a11600e20d5b9105b66b782a96236e74cf",
            privateKey.data().bytesToHex())
        val publicKey = privateKey.publicKeyNist256p1.uncompressed()
        assertEquals("04dbe5b4b4416ad9158339dd692002ceddab895e11bd87d90ce7e3e745efef28d2ad6e736fe3d57d52213f397a7ba9f0bc8c65620a872aefedbc1ddd74c605cf58",
            publicKey.data().bytesToHex())

        val data = "hello schnorr".encodeToByteArray()
        val hashedData = Hash.sha3256(data)
        val signature = privateKey.sign(hashedData, Curve.NIST256P1)
        assertEquals(true, privateKey.publicKeyNist256p1.verify(signature, hashedData))
        assertEquals(true, privateKey.publicKeyNist256p1.verify(P256_SHA3_256_SIGNATURE.hexToBytes(), hashedData))
        assertEquals("74bae2badfff9e8193292978b07acb703ffafee2b81b551ab6dffa1135a144fd68e352ec7057eca55f5deac2307b8919797d0a7417cc4da983c5608a861afe9500",
            signature.bytesToHex())
    }

    @Test
    fun testSecp256k1_SHA3_256() {
        val privateKeyData = SECP256K1_TEST_PRIVATE_KEY.hexToBytes()
        val privateKey = PrivateKey(privateKeyData)
        assertEquals("9c33a65806715a537d7f67cf7bf8a020cbdac8a1019664a2fa34da42d1ddbc7d",
            privateKey.data().bytesToHex())
        val publicKey = privateKey.getPublicKeySecp256k1(false)
        assertEquals("04ad94008dea1505863fc92bd2db5b9fbf52a57f2a05d34fedb693c714bdc731cca57be95775517a9df788a564f2d7491d2c9716d1c0411a5a64155895749d47bc",
            publicKey.data().bytesToHex())

        val data = "hello schnorr".encodeToByteArray()
        val hashedData = Hash.sha3256(data)
        val signature = privateKey.sign(hashedData, Curve.SECP256K1)
        assertEquals("88271aaa67c0f66b9591b8706056a2f46876ceb8e3400ee95b0d32a4bcd99de9168b28f5e74cd561602fb36c035adccf4329001dc5ee42c32ae2fc0038cbc20301",
            signature.bytesToHex())
    }

    @Test
    fun testSecp256k1_SHA256() {
        val privateKeyData = SECP256K1_TEST_PRIVATE_KEY.hexToBytes()
        val privateKey = PrivateKey(privateKeyData)

        val publicKey = privateKey.getPublicKeySecp256k1(false)

        assertEquals("04ad94008dea1505863fc92bd2db5b9fbf52a57f2a05d34fedb693c714bdc731cca57be95775517a9df788a564f2d7491d2c9716d1c0411a5a64155895749d47bc",
            publicKey.data().bytesToHex())

        val data = "hello schnorr".encodeToByteArray()
        val hashedData = Hash.sha256(data)
        val signature = privateKey.sign(hashedData, Curve.SECP256K1)
        assertEquals("7c2e835850eee7375fa9540ddb7828c786338c84a6424b592be2388b1663a5fd27862167e21fd4a771c54abcc5ed3a23371265072129315aab93022e35f77ebe01",
            signature.bytesToHex())
    }

    @Test
    fun testWalletKey() {
        Log.w("method", "testWalletKey()")
        val privateKeyData = PRIVATE_KEY.hexToBytes()
        val privateKey = PrivateKey(privateKeyData)
        assertEquals(true, PrivateKey.isValid(privateKey.data(), Curve.NIST256P1))
        assertEquals(PRIVATE_KEY, privateKey.data().bytesToHex())
        assertEquals(PUBLIC_KEY, privateKey.publicKeyNist256p1.uncompressed().data().bytesToHex())
    }

    @Test
    fun testWalletSign() {
        Log.w("method", "testWalletSign()")
        val data = "hello schnorr".encodeToByteArray()
        val hashedData = Hash.sha256(data)
        val privateKeyData = PRIVATE_KEY.hexToBytes()
        val privateKey = PrivateKey(privateKeyData)
        assertEquals(PRIVATE_KEY, privateKey.data().bytesToHex())
        val signature = privateKey.sign(hashedData, Curve.NIST256P1)
        Log.w("signature -> ", signature.bytesToHex())
        assertEquals(true, privateKey.publicKeyNist256p1.verify(signature, hashedData))
        assertEquals(true, privateKey.publicKeyNist256p1.verify(P256_SHA256_SIGNATURE.hexToBytes(), hashedData))
    }

    @Test
    fun testScript() = runBlocking {
        println("===========> method: testScript()")
        val response = FlowCadenceApi.executeCadenceScript  {
            script {
                """
                access(all) fun main(): String {
                    let greeting = "Hello"
                    return greeting
                }
            """
            }

        }
        println("===========> response:${response}")
    }


    companion object {
        const val MNEMONIC = "normal dune pole key case cradle unfold require tornado mercy hospital buyer"
        const val PUBLIC_KEY =
            "04dbe5b4b4416ad9158339dd692002ceddab895e11bd87d90ce7e3e745efef28d2ad6e736fe3d57d52213f397a7ba9f0bc8c65620a872aefedbc1ddd74c605cf58"
        const val PRIVATE_KEY = "638dc9ad0eee91d09249f0fd7c5323a11600e20d5b9105b66b782a96236e74cf"
        const val P256_SHA256_SIGNATURE =
            "0cd37adf53dc353eeb07321c765d81aedd11f34a6393de31bb15e2c5a07793c96ac54369d71a7e769dced55fc941d2f723538e1b31bf587e7f435e911222068b01"
        const val P256_SHA3_256_SIGNATURE =
            "74bae2badfff9e8193292978b07acb703ffafee2b81b551ab6dffa1135a144fd68e352ec7057eca55f5deac2307b8919797d0a7417cc4da983c5608a861afe9500"

        const val SECP256K1_TEST_PRIVATE_KEY = "9c33a65806715a537d7f67cf7bf8a020cbdac8a1019664a2fa34da42d1ddbc7d"

        const val HOST_TESTNET = "access.devnet.nodes.onflow.org"

        const val DERIVATION_PATH = "m/44'/539'/0'/0/0"
    }
}