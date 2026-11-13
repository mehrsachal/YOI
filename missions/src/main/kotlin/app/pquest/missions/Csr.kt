package app.pquest.missions

import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.Signature

/** Minimal PKCS#10 certificate request (RSA, SHA-256) for TAK Server certificate enrolment. No external crypto library. */
object Csr {
    private fun der(tag: Int, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(); o.write(tag)
        when {
            body.size < 128 -> o.write(body.size)
            body.size < 256 -> { o.write(0x81); o.write(body.size) }
            else -> { o.write(0x82); o.write(body.size shr 8); o.write(body.size and 0xFF) }
        }
        o.write(body); return o.toByteArray()
    }
    private fun seq(vararg parts: ByteArray) = der(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun oid(vararg bytes: Int) = der(0x06, ByteArray(bytes.size) { bytes[it].toByte() })
    private val SHA256_RSA = seq(oid(0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0B), der(0x05, ByteArray(0)))

    /** [subject] is a list of (attribute, value) with attribute one of CN, O, OU, in the order to encode. */
    fun build(keys: KeyPair, subject: List<Pair<String, String>>): ByteArray {
        val types = mapOf("CN" to 3, "O" to 10, "OU" to 11)
        val name = seq(*subject.map { (k, v) -> der(0x31, seq(oid(0x55, 0x04, types.getValue(k)), der(0x0C, v.toByteArray()))) }.toTypedArray())
        val info = seq(der(0x02, byteArrayOf(0)), name, keys.public.encoded, der(0xA0, ByteArray(0)))
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(keys.private); update(info); sign() }
        return seq(info, SHA256_RSA, der(0x03, byteArrayOf(0) + sig))
    }
}
