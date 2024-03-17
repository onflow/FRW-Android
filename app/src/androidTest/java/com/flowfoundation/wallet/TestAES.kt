package com.flowfoundation.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfoundation.wallet.utils.secret.aesDecrypt
import com.flowfoundation.wallet.utils.secret.aesEncrypt
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestAES {

    @Test
    fun aesTest() {
        val key = "0102030405060708"
        val iv = "0102030405060708"
        val message = "hello world"
        val encrypt = aesEncrypt(key, iv, message)
        val decrypt = aesDecrypt(key, iv, encrypt)
        println("encrypt:$encrypt")
        assert(message == decrypt)
    }
}