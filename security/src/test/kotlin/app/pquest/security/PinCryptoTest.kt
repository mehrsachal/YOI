package app.pquest.security

import kotlin.test.*

class PinCryptoTest {
    @Test fun rightPinUnwrapsWrongPinDoesNot() {
        val salt = PinCrypto.salt(); val secret = PinCrypto.random(32)
        val blob = PinCrypto.wrap("246810", salt, secret)
        assertContentEquals(secret, PinCrypto.unwrap("246810", salt, blob))
        assertNull(PinCrypto.unwrap("246811", salt, blob))
        assertNull(PinCrypto.unwrap("246810", PinCrypto.salt(), blob))
        blob[20] = (blob[20] + 1).toByte()
        assertNull(PinCrypto.unwrap("246810", salt, blob))
    }

    @Test fun blobsNeverRepeatAndDoNotContainTheSecret() {
        val salt = PinCrypto.salt(); val secret = ByteArray(32) { 0x41 }
        val a = PinCrypto.wrap("1234", salt, secret); val b = PinCrypto.wrap("1234", salt, secret)
        assertFalse(a.contentEquals(b))
        assertFalse(String(a, Charsets.ISO_8859_1).contains("AAAA"))
    }
}
