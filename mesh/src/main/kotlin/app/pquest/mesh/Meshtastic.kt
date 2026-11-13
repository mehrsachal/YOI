package app.pquest.mesh

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Just enough protobuf wire format for the handful of Meshtastic messages we exchange. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()
    private fun raw(v: Long) { var x = v; while (x and 0x7FL.inv() != 0L) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }; out.write(x.toInt()) }
    fun varint(field: Int, v: Long) = apply { raw((field shl 3).toLong()); raw(v) }
    fun fixed32(field: Int, v: Int) = apply { raw((field shl 3 or 5).toLong()); for (i in 0..3) out.write(v ushr (8 * i) and 0xFF) }
    fun bytes(field: Int, b: ByteArray) = apply { raw((field shl 3 or 2).toLong()); raw(b.size.toLong()); out.write(b) }
    fun bytes(): ByteArray = out.toByteArray()
}

class ProtoField(val num: Int, val value: Long, val data: ByteArray?)

fun protoFields(b: ByteArray): List<ProtoField> {
    val out = ArrayList<ProtoField>(); var i = 0
    fun varint(): Long { var r = 0L; var s = 0; while (true) { val x = b[i++].toInt(); r = r or ((x and 0x7F).toLong() shl s); if (x and 0x80 == 0) return r; s += 7; require(s < 64) } }
    while (i < b.size) {
        val tag = varint().toInt()
        when (tag and 7) {
            0 -> out += ProtoField(tag ushr 3, varint(), null)
            1 -> { var v = 0L; for (k in 0..7) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k)); i += 8; out += ProtoField(tag ushr 3, v, null) }
            2 -> { val n = varint().toInt(); require(n >= 0 && i + n <= b.size); out += ProtoField(tag ushr 3, 0, b.copyOfRange(i, i + n)); i += n }
            5 -> { var v = 0L; for (k in 0..3) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k)); i += 4; out += ProtoField(tag ushr 3, v, null) }
            else -> throw IllegalArgumentException("wire type")
        }
    }
    return out
}

data class MeshNode(val num: Long, val name: String, val lastHeard: Long, val hops: Int?, val snr: Float?)

sealed class FromRadio {
    data class Packet(val from: Long, val to: Long, val port: Int, val payload: ByteArray, val hops: Int?) : FromRadio()
    data class MyInfo(val nodeNum: Long) : FromRadio()
    data class Node(val node: MeshNode) : FromRadio()
    data class ConfigComplete(val id: Long) : FromRadio()
    object Other : FromRadio()
}

/** Meshtastic client protocol (the protobuf frames exchanged with a radio over Bluetooth). */
object Meshtastic {
    const val BROADCAST = 0xFFFFFFFFL
    /** PRIVATE_APP port: opaque application payloads, relayed by any Meshtastic node. */
    const val PORT_PRIVATE = 256
    const val MAX_PAYLOAD = 200

    fun wantConfig(id: Int): ByteArray = ProtoWriter().varint(3, id.toLong()).bytes()

    fun send(payload: ByteArray, packetId: Int, hopLimit: Int = 3, to: Long = BROADCAST): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too large for LoRa" }
        val data = ProtoWriter().varint(1, PORT_PRIVATE.toLong()).bytes(2, payload).bytes()
        val packet = ProtoWriter().fixed32(2, to.toInt()).bytes(4, data).fixed32(6, packetId).varint(9, hopLimit.toLong()).bytes()
        return ProtoWriter().bytes(1, packet).bytes()
    }

    fun parse(frame: ByteArray): FromRadio = runCatching {
        val f = protoFields(frame)
        f.firstOrNull { it.num == 2 }?.data?.let { p ->
            val pf = protoFields(p)
            val data = pf.firstOrNull { it.num == 4 }?.data?.let(::protoFields) ?: return@runCatching FromRadio.Other // still encrypted: not for us
            val limit = pf.firstOrNull { it.num == 9 }?.value?.toInt(); val start = pf.firstOrNull { it.num == 15 }?.value?.toInt()
            return@runCatching FromRadio.Packet(
                pf.firstOrNull { it.num == 1 }?.value ?: 0, pf.firstOrNull { it.num == 2 }?.value ?: 0,
                data.firstOrNull { it.num == 1 }?.value?.toInt() ?: 0, data.firstOrNull { it.num == 2 }?.data ?: ByteArray(0),
                if (start != null && limit != null && start >= limit) start - limit else null,
            )
        }
        f.firstOrNull { it.num == 3 }?.data?.let { return@runCatching FromRadio.MyInfo(protoFields(it).firstOrNull { x -> x.num == 1 }?.value ?: 0) }
        f.firstOrNull { it.num == 4 }?.data?.let { n ->
            val nf = protoFields(n)
            val user = nf.firstOrNull { it.num == 2 }?.data?.let(::protoFields)
            val num = nf.firstOrNull { it.num == 1 }?.value ?: 0
            return@runCatching FromRadio.Node(MeshNode(
                num, user?.firstOrNull { it.num == 2 }?.data?.let { String(it) } ?: "!%08x".format(num),
                (nf.firstOrNull { it.num == 5 }?.value ?: 0) * 1000, nf.firstOrNull { it.num == 9 }?.value?.toInt(),
                nf.firstOrNull { it.num == 4 }?.value?.let { java.lang.Float.intBitsToFloat(it.toInt()) },
            ))
        }
        f.firstOrNull { it.num == 7 }?.let { return@runCatching FromRadio.ConfigComplete(it.value) }
        FromRadio.Other
    }.getOrDefault(FromRadio.Other)
}

/**
 * Application-layer encryption for mesh traffic, independent of how the radios themselves are configured.
 * Packet = key id (1 byte) + nonce (12) + AES-256-GCM ciphertext and tag (16): 29 bytes of overhead.
 */
object MeshCrypto {
    const val OVERHEAD = 29
    private val rnd = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }
    fun keyId(key: ByteArray): Byte = MessageDigest.getInstance("SHA-256").digest(key)[0]

    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return byteArrayOf(keyId(key)) + nonce + c.doFinal(plain)
    }

    /** Tries each known mission key whose id matches; returns the key index and plaintext, or null. */
    fun open(keys: List<ByteArray>, packet: ByteArray): Pair<Int, ByteArray>? {
        if (packet.size < OVERHEAD) return null
        keys.forEachIndexed { i, k ->
            if (k.size == 32 && keyId(k) == packet[0]) runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, packet, 1, 12))
                return Pair(i, c.doFinal(packet, 13, packet.size - 13))
            }
        }
        return null
    }
}
