package io.outblock.wallet

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import java.security.PrivateKey
import java.security.Signature


object SignatureManager {

    fun signData(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        val asn1Signature = signature.sign()
        val seq = ASN1Sequence.getInstance(asn1Signature)
        val r = (seq.getObjectAt(0) as ASN1Integer).value.toByteArray()
        val s = (seq.getObjectAt(1) as ASN1Integer).value.toByteArray()
        return (r.takeLast(32) + s.takeLast(32)).toByteArray()
    }
}