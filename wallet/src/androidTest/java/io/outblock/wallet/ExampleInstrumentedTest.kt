package io.outblock.wallet

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.outblock.wallet.test", appContext.packageName)
    }

    @Test
    fun testKeyGenerate() {
        val keyPair = KeyManager.generateKey()
//        val userId = "test_id"
//        KeyManager.storeKeyEntry(userId, keyEntry)
//        val privateKey = KeyManager.getPrivateKey(userId)
        val bytes = sha256("test_byte").hexStringToByteArray()
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(bytes)
        val signedData = signature.sign()

        val publicKey = keyPair.public as ECPublicKey
        signature.initVerify(publicKey)
        signature.update(bytes)
        assertEquals(true, signature.verify(signedData))
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)

        val result = StringBuilder()
        for (byte in digest) {
            result.append(String.format("%02x", byte))
        }

        return result.toString()
    }

    private fun String.hexStringToByteArray(): ByteArray {
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}