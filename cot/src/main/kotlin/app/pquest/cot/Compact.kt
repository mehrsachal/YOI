package app.pquest.cot

import app.pquest.coords.LatLon
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * Compact binary form of position reports, short text, receipts and alerts for LoRa mesh.
 * Every packet stays under [MAX] bytes before encryption so that, with the 28-byte AES-GCM overhead,
 * it still fits a single Meshtastic payload (limit 200 bytes).
 */
object Compact {
    const val MAX = 170
    const val MAX_TEXT = 100

    fun hash(uid: String): Int = uid.fold(0x811C9DC5.toInt()) { h, c -> (h xor c.code) * 16777619 }
    fun meshUid(hash: Int) = "MESH-%08X".format(java.util.Locale.ROOT, hash)

    private fun DataOutputStream.str(s: String, max: Int) {
        var b = s.toByteArray()
        if (b.size > max) { // cut on a character boundary
            var n = max
            while (n > 0 && (b[n].toInt() and 0xC0) == 0x80) n--
            b = b.copyOf(n)
        }
        writeByte(b.size); write(b)
    }
    private fun ByteBuffer.str(): String = ByteArray(get().toInt() and 0xFF).also { get(it) }.toString(Charsets.UTF_8)
    private fun DataOutputStream.pos(p: LatLon) { writeInt(Math.round(p.lat * 1e7).toInt()); writeInt(Math.round(p.lon * 1e7).toInt()) }
    private fun ByteBuffer.pos() = LatLon(int / 1e7, int / 1e7)
    private fun id8(id: String): ByteArray = ByteArray(8) { i -> id.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    private fun ByteBuffer.id8() = ByteArray(8).also { get(it) }.joinToString("") { "%02x".format(it) }

    /** Message ids are 16 hex characters so they survive the trip through the mesh unchanged. */
    fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(16)

    /** Returns null for events that are not carried over mesh (map items, long ids). */
    fun encode(e: CotEvent): ByteArray? {
        val bo = ByteArrayOutputStream(); val o = DataOutputStream(bo)
        when (e) {
            is CotEvent.Pli -> e.contact.let { c ->
                o.writeByte(1); o.writeInt(hash(c.uid)); o.str(c.callsign, 16); o.str(c.team, 10); o.pos(c.pos)
                o.writeShort((c.alt ?: -32768.0).toInt().coerceIn(-32768, 32767))
                o.writeByte(((c.course ?: 0.0) / 2).toInt() and 0xFF); o.writeByte((c.speed ?: 0.0).toInt().coerceIn(0, 255))
                o.writeInt((c.time / 1000).toInt())
            }
            is CotEvent.Chat -> e.msg.let { m ->
                if (m.id.length != 16 || !m.id.all { it in "0123456789abcdef" }) return null
                o.writeByte(2); o.write(id8(m.id)); o.writeInt(hash(m.fromUid)); o.str(m.fromCallsign, 16)
                o.writeByte(m.roomKind.ordinal)
                if (m.roomKind == RoomKind.DIRECT) o.writeInt(hash(m.room)) else o.str(if (m.roomKind == RoomKind.ALL) "" else m.roomLabel, 20)
                o.writeByte(if (m.loc != null) 1 else 0); m.loc?.let { o.pos(it) }
                o.writeInt((m.time / 1000).toInt()); o.str(m.text, MAX_TEXT)
            }
            is CotEvent.Receipt -> {
                if (e.msgId.length != 16) return null
                o.writeByte(4); o.write(id8(e.msgId)); o.writeByte(e.state.ordinal); o.writeInt(hash(e.fromUid))
            }
            is CotEvent.Alert -> {
                o.writeByte(3); o.writeInt(hash(e.uid)); o.str(e.callsign, 16); o.pos(e.pos)
                o.writeInt((e.time / 1000).toInt()); o.writeByte(if (e.cancel) 1 else 0)
            }
            is CotEvent.ItemChange -> return null
        }
        return bo.toByteArray().also { check(it.size <= MAX) }
    }

    /** [resolve] maps a uid hash back to a full uid when the sender is already known from another link. */
    fun decode(bytes: ByteArray, resolve: (Int) -> String? = { null }): CotEvent? = runCatching {
        val b = ByteBuffer.wrap(bytes)
        fun uid(h: Int) = resolve(h) ?: meshUid(h)
        when (b.get().toInt()) {
            1 -> {
                val h = b.int; val cs = b.str(); val team = b.str(); val p = b.pos(); val alt = b.short.toInt()
                val course = (b.get().toInt() and 0xFF) * 2.0; val speed = (b.get().toInt() and 0xFF).toDouble()
                CotEvent.Pli(Contact(uid(h), cs, team, "Team Member", p, if (alt == -32768) null else alt.toDouble(), course, speed, (b.int.toLong() and 0xFFFFFFFFL) * 1000))
            }
            2 -> {
                val id = b.id8(); val from = b.int; val cs = b.str(); val kind = RoomKind.values()[b.get().toInt()]
                val room = if (kind == RoomKind.DIRECT) uid(b.int) else b.str().ifEmpty { "All Chat Rooms" }
                val loc = if (b.get().toInt() == 1) b.pos() else null
                val t = (b.int.toLong() and 0xFFFFFFFFL) * 1000
                CotEvent.Chat(ChatMsg(id, kind, room, uid(from), cs, b.str(), t, loc))
            }
            3 -> { val h = b.int; val cs = b.str(); val p = b.pos(); val t = (b.int.toLong() and 0xFFFFFFFFL) * 1000
                CotEvent.Alert(uid(h), cs, p, t, b.get().toInt() == 1) }
            4 -> { val id = b.id8(); val st = MsgState.values()[b.get().toInt()]; CotEvent.Receipt(id, st, uid(b.int)) }
            else -> null
        }
    }.getOrNull()
}
