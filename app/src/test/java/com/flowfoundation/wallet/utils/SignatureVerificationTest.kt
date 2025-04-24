package com.flowfoundation.wallet.utils


import com.flowfoundation.wallet.BuildConfig
import okio.ByteString.Companion.decodeHex
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.jce.ECNamedCurveTable
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger

class SignatureVerificationTest {
    @Test
    fun testVerifySignature() {
        val publicKeyHex = BuildConfig.X_SIGNATURE_KEY

        val signatureHex = "7ed2cf4b11f75e373de3b459cdbd477ccbff756ca54fba8588da52533874e0195ce2986bc5e25e7b1b4035a864da625d1af1a3b75429892f91c3bb0dce0ba4d3"

        val messageHex = "f6d408ee8cbe8d0f0ffaec7add4d34a3cad64c075d7350c96f51c13c337860e2"

        val result = verifySignature(signatureHex.decodeHex().toByteArray(), messageHex.decodeHex().toByteArray(), publicKeyHex.decodeHex().toByteArray())

        assertTrue("Signature verify failed", result)
    }

    private fun verifySignature(rawSignatureBytes: ByteArray, hashedData: ByteArray, pubKeyBytes: ByteArray): Boolean {
        try {

            val ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1")

            val pubKeyPoint = ecSpec.curve.decodePoint(pubKeyBytes)

            val pubKeyParams = ECPublicKeyParameters(pubKeyPoint,
                ECDomainParameters(ecSpec.curve, ecSpec.g, ecSpec.n, ecSpec.h)
            )

            val r: BigInteger
            val s: BigInteger

            if (rawSignatureBytes.size == 64) {
                r = BigInteger(1, rawSignatureBytes.copyOfRange(0, 32))
                s = BigInteger(1, rawSignatureBytes.copyOfRange(32, 64))
            } else {
                val asn1InputStream = ASN1InputStream(ByteArrayInputStream(rawSignatureBytes))
                val sequence = asn1InputStream.readObject() as DERSequence
                r = (sequence.getObjectAt(0) as ASN1Integer).value
                s = (sequence.getObjectAt(1) as ASN1Integer).value
                asn1InputStream.close()
            }

            val signer = ECDSASigner()
            signer.init(false, pubKeyParams)

            return signer.verifySignature(hashedData, r, s)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun convertToDerFormat(rawSignature: ByteArray): ByteArray {
        require(rawSignature.size == 64) { "无效的签名长度" }

        val r = rawSignature.copyOfRange(0, 32)
        val s = rawSignature.copyOfRange(32, 64)

        fun encodeDERComponent(value: ByteArray): ByteArray {
            var trimmed = value
            // 移除前导零
            var i = 0
            while (i < trimmed.size - 1 && trimmed[i] == 0.toByte()) i++
            if (i > 0) trimmed = trimmed.copyOfRange(i, trimmed.size)

            // 如果最高位为1，添加一个前导零
            if (trimmed[0].toInt() and 0x80 != 0) {
                trimmed = byteArrayOf(0) + trimmed
            }

            return byteArrayOf(0x02, trimmed.size.toByte()) + trimmed
        }

        val rDer = encodeDERComponent(r)
        val sDer = encodeDERComponent(s)
        val sequence = byteArrayOf(0x30, (rDer.size + sDer.size).toByte()) + rDer + sDer

        return sequence
    }
}