package com.flowfoundation.wallet.manager.evm

import wallet.core.jni.CoinType
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet


object EvmWalletManager {

    private const val DERIVATION_PATH = "m/44'/60'/1'/0/0"
    private val wallet = HDWallet(128, "")

    fun wallet(): HDWallet {
        return wallet
    }

    fun address(): String {
        return CoinType.ETHEREUM.deriveAddress(wallet.getKey(CoinType.ETHEREUM, DERIVATION_PATH)).lowercase()
    }

    fun signData(data: ByteArray): ByteArray {
        return wallet.getCurveKey(Curve.SECP256K1, DERIVATION_PATH).sign(data, Curve.SECP256K1).apply {
            (this[this.size - 1]) = (this[this.size - 1] + 27).toByte()
        }
    }
}