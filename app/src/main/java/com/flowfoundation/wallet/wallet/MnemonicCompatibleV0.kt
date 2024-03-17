package com.flowfoundation.wallet.wallet

import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.secret.aesDecrypt
import java.util.*

/**
 * TODO delete this in future
 * recovery mnemonic from old version
 */
fun restoreMnemonicV0() {
    ioScope {
        val oldMnemonic = getMnemonic()
        if (oldMnemonic.isBlank()) {
            return@ioScope
        }
        logd("restoreMnemonicV0", "start")
        Wallet.store().updateMnemonic(oldMnemonic).store()
        cleanMnemonicPreferenceV0()
        logd("restoreMnemonicV0", "finish")
    }
}

private fun getMnemonic(): String {
    return aesDecrypt(mnemonicAesKey(), message = getMnemonicFromPreferenceV0())
}

private fun mnemonicAesKey(): String {
    return getPinCode() + randomCode()
}

private fun randomCode(): String {
    var key = getAesLocalCodeV0()
    if (key.isEmpty()) {
        key = UUID.randomUUID().toString().take(16)
        updateAesLocalCodeV0(key)
    }
    return key
}