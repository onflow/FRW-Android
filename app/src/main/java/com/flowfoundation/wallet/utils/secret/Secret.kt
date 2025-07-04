package com.flowfoundation.wallet.utils.secret

import com.flowfoundation.wallet.BuildConfig
import org.onflow.flow.models.bytesToHex
import org.onflow.flow.models.hexToBytes
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密解密 key 和 iv 必须16位字符
 */

private const val defaultIv = BuildConfig.DRIVE_AES_IV

fun aesEncrypt(key: String, iv: String = defaultIv, message: String): String {
    val sKey = SecretKeySpec(key.parseKey(), "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
    cipher.init(Cipher.ENCRYPT_MODE, sKey, IvParameterSpec(iv.parseKey()))
    return cipher.doFinal(message.toByteArray()).bytesToHex()
}

fun aesDecrypt(key: String, iv: String = defaultIv, message: String): String {
    val sKey = SecretKeySpec(key.parseKey(), "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
    val data = message.hexToBytes()
    cipher.init(Cipher.DECRYPT_MODE, sKey, IvParameterSpec(iv.parseKey()))
    return String(cipher.doFinal(data))
}

private fun String.parseKey(): ByteArray = toByteArray().copyOf(16).take(16).toByteArray()