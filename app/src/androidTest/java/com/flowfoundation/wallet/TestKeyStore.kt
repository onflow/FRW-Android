package com.flowfoundation.wallet

import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.StoredKey

class TestKeyStore {

    init {
        System.loadLibrary("TrustWalletCore")
    }

    @Test
    fun testDecryptMnemonic() {
        val keyStore = StoredKey("Test Wallet", "password".toByteArray())
        val result = keyStore.decryptMnemonic("wrong".toByteArray())
        val result2 = keyStore.decryptMnemonic("password".toByteArray())
        println("TAG result: $result")
        println("TAG result2: $result2")
        assertNull(result)
        assertNotNull(result2)
    }

    @Test
    fun testRemoveCoins() {
        val password = "password".toByteArray()
        val keyStore = StoredKey("Test Wallet", password)
        val wallet = keyStore.wallet(password)

        assertNotNull(keyStore.accountForCoin(CoinType.BITCOIN, wallet))
        assertNotNull(keyStore.accountForCoin(CoinType.ETHEREUM, wallet))
        assertEquals(keyStore.accountCount(), 2)

        keyStore.removeAccountForCoin(CoinType.BITCOIN)

        assertEquals(keyStore.accountCount(), 1)
        assertEquals(keyStore.account(0).coin(), CoinType.ETHEREUM)
    }

    @Test
    fun testImportKeystore() {
        val json = """
            {"version":3,"id":"c0cae541-21f2-43f5-ab45-66c49a21a43f","address":"8cd687688f1ca87c34259a251b0f31f7dfc1bdbd","crypto":{"ciphertext":"d361ad39e8e859d309838d5017f7dc6b88e4ccf08ec5bc12f6b78fca702a8f74","cipherparams":{"iv":"c895d524bc3bcaf7d35baf43140237df"},"cipher":"aes-128-ctr","kdf":"scrypt","kdfparams":{"dklen":32,"salt":"72ace8aae80f25c56b05eede9f6654f407914dfaf5f40c2d74fb19be7e1d541a","n":131072,"r":8,"p":1},"mac":"30962d3311dc476ea9b8941a823c083dd9f149083c15e75fddbd193af0e3d261"}}
        """.trimIndent()
        val password = "11111111"
        val pass = password.toByteArray()
        val keyStore = StoredKey.importJSON(json.toByteArray())
        val privateKey = PrivateKey(keyStore.decryptPrivateKey(pass))
        val p1PublicKey = privateKey.publicKeyNist256p1.uncompressed().data().bytesToHex().removePrefix("04")

        val k1PublicKey = privateKey.getPublicKeySecp256k1(false).data().bytesToHex().removePrefix("04")
        println("TAG p1pk: $p1PublicKey")
        println("TAG k1pk: $k1PublicKey")
        assertEquals(p1PublicKey,
            "6d117d43b89a5c38751c73a799aecbb6df9ac7cb629df80c29678a0795ea890099f3cec59b195a2109f2aebc2dd4619b9b0cd8502b36c3fb0b1f953775214d89")

        assertEquals(k1PublicKey, "309b594113b7b61aec38586b2bd1c96258e1306cd8e10bfe8e74ef2dfb154911b7cdcbe219053b19ce5b86c1fede5caf1df70ddca7bb7fde04cc36a7fb7fd948")

    }

    @Test
    fun testLongHexPassword() {
        val json = """
            {
            "address": "34bae2218c254ed190c0f5b1dd4323aee8e7da09",
            "id": "86066d8c-8dba-4d81-afd4-934e2a2b72a2",
            "version": 3,
            "crypto": {
                "cipher": "aes-128-ctr",
                "cipherparams": {
                    "iv": "a4976ad73057007ad788d1f792db851d"
                },
                "ciphertext": "5e4458d69964172c492616b751d6589b4ad7da4217dcfccecc3f4e515a934bb8",
                "kdf": "scrypt",
                "kdfparams": {
                    "dklen": 32,
                    "n": 4096,
                    "p": 6,
                    "r": 8,
                    "salt": "24c72d92bf88a4f7c7b3f5e3cb3620714d71fceabbb0bc6099f50c6d5d898e7c"
                },
                "mac": "c15e3035ddcaca766dfc56648978d33e94d3c57d4a5e13fcf8b5f8dbb0902900"
            }
        }
        """.trimIndent()
        val password =
            "2d6eefbfbd4622efbfbdefbfbd516718efbfbdefbfbdefbfbdefbfbd59efbfbd30efbfbdefbfbd3a4348efbfbd2aefbfbdefbfbd49efbfbd27efbfbd0638efbfbdefbfbdefbfbd4cefbfbd6befbfbdefbfbd6defbfbdefbfbd63efbfbd5aefbfbd61262b70efbfbdefbfbdefbfbdefbfbdefbfbdc7aa373163417cefbfbdefbfbdefbfbd44efbfbdefbfbd1d10efbfbdefbfbdefbfbd61dc9e5b124befbfbd11efbfbdefbfbd2fefbfbdefbfbd3d7c574868efbfbdefbfbdefbfbd37043b7b5c1a436471592f02efbfbd18efbfbdefbfbd2befbfbdefbfbd7218efbfbd6a68efbfbdcb8e5f3328773ec48174efbfbd67efbfbdefbfbdefbfbdefbfbdefbfbd2a31efbfbd7f60efbfbdd884efbfbd57efbfbd25efbfbd590459efbfbd37efbfbd2bdca20fefbfbdefbfbdefbfbdefbfbd39450113efbfbdefbfbdefbfbd454671efbfbdefbfbdd49fefbfbd47efbfbdefbfbdefbfbdefbfbd00efbfbdefbfbdefbfbdefbfbd05203f4c17712defbfbd7bd1bbdc967902efbfbdc98a77efbfbd707a36efbfbd12efbfbdefbfbd57c78cefbfbdefbfbdefbfbd10efbfbdefbfbdefbfbde1a1bb08efbfbdefbfbd26efbfbdefbfbd58efbfbdefbfbdc4b1efbfbd295fefbfbd0eefbfbdefbfbdefbfbd0e6eefbfbd"
        val pass = password.hexToBytes()
        val keyStore = StoredKey.importJSON(json.toByteArray())
        val privateKey = keyStore.decryptPrivateKey(pass)
        assertEquals(privateKey.bytesToHex(), "0x043c5429c7872502531708ec0d821c711691402caf37ef7ba78a8c506f10653b")
    }

    @Test
    fun testExportJSON() {
        val password = "password".toByteArray()
        val keyStore = StoredKey("Test Wallet", password)
        val json = keyStore.exportJSON()
        assertNotNull(json)

        val newKeyStore = StoredKey.importJSON(json)
        val privateKey = newKeyStore.decryptPrivateKey("".toByteArray())
        assertNull(privateKey)
    }

    @Test
    fun testMnemonic() {
        val password = "password".toByteArray()
        val mnemonic = "call property between lady glow catch old subject hazard forest service vibrant"
        val keyStore = StoredKey.importHDWallet(
            mnemonic,
            "Wallet name",
            password,
            CoinType.FLOW
        )
        assertEquals(mnemonic, keyStore.decryptMnemonic(password))

        keyStore.store("test_key_store")
        Thread.sleep(1000)
        val newKeyStore = StoredKey.load("test_key_store")
        assertEquals(mnemonic, newKeyStore.decryptMnemonic(password))
    }
}