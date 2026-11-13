package app.pquest.data

import app.pquest.coords.LatLon
import app.pquest.cot.*
import app.pquest.missions.Conflict
import app.pquest.missions.Merge
import app.pquest.missions.Mission
import app.pquest.missions.Role
import app.pquest.missions.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class StoredMsg(val msg: ChatMsg, val state: MsgState, val mine: Boolean, val unread: Boolean, val attachment: String?)
data class Room(val kind: RoomKind, val room: String, val label: String, val last: Long, val unread: Int)
data class Outgoing(val n: Long, val msgId: String?, val xml: String, val compact: ByteArray?, val mission: String, val everyLink: Boolean)
data class CustomSymbol(val id: String, val label: String, val name: String)

/** Everything the user creates or receives, in one SQLCipher database: overlays, missions, messages, certificates. */
class Repo(private val db: SQLiteDatabase) {
    /** Incremented on every change so the UI and the map refresh. */
    val tick = MutableStateFlow(0)
    private val self0 = Self("", "")
    private fun bump() { tick.value++ }

    init {
        for (sql in listOf(
            "CREATE TABLE IF NOT EXISTS items (uid TEXT PRIMARY KEY, mission TEXT NOT NULL, cot TEXT NOT NULL, updated INTEGER NOT NULL, deleted INTEGER NOT NULL, dirty INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS missions (id TEXT PRIMARY KEY, name TEXT NOT NULL, key TEXT NOT NULL, role TEXT NOT NULL, visible INTEGER NOT NULL, server INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS messages (id TEXT PRIMARY KEY, kind INTEGER, room TEXT, label TEXT, fromUid TEXT, fromCs TEXT, body TEXT, time INTEGER, lat REAL, lon REAL, state INTEGER, mine INTEGER, unread INTEGER, attach TEXT)",
            "CREATE TABLE IF NOT EXISTS outbox (n INTEGER PRIMARY KEY AUTOINCREMENT, msgId TEXT, xml TEXT NOT NULL, compact BLOB, mission TEXT NOT NULL, everyLink INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS conflicts (n INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, time INTEGER, kept TEXT, dropped TEXT, name TEXT)",
            "CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT)",
        )) db.execSQL(sql)
    }

    private fun <T> query(sql: String, vararg args: Any?, row: (android.database.Cursor) -> T): List<T> =
        db.rawQuery(sql, *args).use { c -> ArrayList<T>().also { while (c.moveToNext()) it.add(row(c)) } }

    // ---- settings ----------------------------------------------------------------------------------------

    fun kv(k: String): String? = query("SELECT v FROM kv WHERE k = ?", k) { it.getString(0) }.firstOrNull()
    fun setKv(k: String, v: String?) {
        if (v == null) db.execSQL("DELETE FROM kv WHERE k = ?", arrayOf<Any?>(k)) else db.execSQL("INSERT OR REPLACE INTO kv VALUES (?, ?)", arrayOf<Any?>(k, v))
        bump()
    }

    val self: Self
        get() {
            val uid = kv("uid") ?: ("PQUEST-" + Compact.newId().take(12)).also { setKv("uid", it) }
            return Self(uid, kv("callsign") ?: ("PQ-" + uid.takeLast(4).uppercase()), kv("team") ?: "Cyan", kv("role") ?: "Team Member")
        }

    var server: ServerConfig?
        get() = kv("server")?.let { JSONObject(it) }?.let { o ->
            val d = Base64.getDecoder()
            ServerConfig(o.getString("host"), o.getInt("port"), d.decode(o.getString("ca")), o.getString("caPass"), d.decode(o.getString("client")), o.getString("clientPass"))
        }
        set(s) = setKv("server", s?.let {
            val e = Base64.getEncoder()
            JSONObject().put("host", it.host).put("port", it.port).put("ca", e.encodeToString(it.caP12)).put("caPass", it.caPass)
                .put("client", e.encodeToString(it.clientP12)).put("clientPass", it.clientPass).toString()
        })

    val defaultQuick = listOf("In position", "Moving", "Contact", "All clear", "Need resupply", "Casualty", "Halted", "Objective secured", "Request orders", "Returning to base")
    var quick: List<String>
        get() = kv("quick")?.let { JSONArray(it) }?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: defaultQuick
        set(v) = setKv("quick", JSONArray(v).toString())

    var customSymbols: List<CustomSymbol>
        get() = kv("custom")?.let { JSONArray(it) }?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> CustomSymbol(o.getString("id"), o.getString("label"), o.getString("name")) } } }
            ?: listOf(CustomSymbol("pqt", "PQT", "Picquet"), CustomSymbol("post", "POST", "Post"), CustomSymbol("nx", "NX", "Nullah crossing"))
        set(v) = setKv("custom", JSONArray(v.map { JSONObject().put("id", it.id).put("label", it.label).put("name", it.name) }).toString())

    // ---- missions ----------------------------------------------------------------------------------------

    fun missions(): List<Mission> = query("SELECT id, name, key, role, visible, server FROM missions ORDER BY name") {
        Mission(it.getString(0), it.getString(1), it.getString(2), Role.valueOf(it.getString(3)), it.getInt(4) == 1, it.getInt(5) == 1)
    }

    fun putMission(m: Mission) {
        db.execSQL("INSERT OR REPLACE INTO missions VALUES (?,?,?,?,?,?)", arrayOf<Any?>(m.id, m.name, m.key, m.role.name, if (m.visible) 1 else 0, if (m.onServer) 1 else 0))
        bump()
    }

    fun leaveMission(id: String) {
        db.execSQL("DELETE FROM missions WHERE id = ?", arrayOf<Any?>(id)); db.execSQL("DELETE FROM items WHERE mission = ?", arrayOf<Any?>(id))
        bump()
    }

    // ---- items -------------------------------------------------------------------------------------------

    private fun decode(cot: String) = (Cot.decode(cot) as? CotEvent.ItemChange)?.item

    fun items(includeDeleted: Boolean = false): List<Item> =
        query("SELECT cot FROM items" + if (includeDeleted) "" else " WHERE deleted = 0") { decode(it.getString(0)) }.filterNotNull()

    fun item(uid: String): Item? = query("SELECT cot FROM items WHERE uid = ?", uid) { decode(it.getString(0)) }.firstOrNull()
    private fun isDirty(uid: String) = query("SELECT dirty FROM items WHERE uid = ?", uid) { it.getInt(0) == 1 }.firstOrNull() ?: false

    private fun store(i: Item, dirty: Boolean) = db.execSQL(
        "INSERT OR REPLACE INTO items VALUES (?,?,?,?,?,?)",
        arrayOf<Any?>(i.uid, i.mission, Cot.encode(CotEvent.ItemChange(i), self0), i.updated, if (i.deleted) 1 else 0, if (dirty) 1 else 0),
    )

    /** A local edit: stored and marked as waiting to be sent. */
    fun putLocal(i: Item) { store(i, true); bump() }

    /** An item from another device: merged last-writer-wins, with any discarded concurrent edit logged. */
    fun putRemote(remote: Item): Boolean {
        val r = Merge.resolve(item(remote.uid) ?: query("SELECT cot FROM items WHERE uid = ?", remote.uid) { decode(it.getString(0)) }.firstOrNull(), isDirty(remote.uid), remote)
        r.conflict?.let { db.execSQL("INSERT INTO conflicts (uid, time, kept, dropped, name) VALUES (?,?,?,?,?)", arrayOf<Any?>(it.uid, it.time, it.keptAuthor, it.droppedAuthor, it.name)) }
        r.store?.let { store(it, false) }
        if (r.store != null || r.conflict != null) bump()
        return r.store != null
    }

    fun dirty(): List<Item> = query("SELECT cot FROM items WHERE dirty = 1") { decode(it.getString(0)) }.filterNotNull()
    fun clean(uid: String, updated: Long) = db.execSQL("UPDATE items SET dirty = 0 WHERE uid = ? AND updated = ?", arrayOf<Any?>(uid, updated))
    fun conflicts(): List<Conflict> = query("SELECT uid, time, kept, dropped, name FROM conflicts ORDER BY n DESC LIMIT 100") {
        Conflict(it.getString(0), it.getLong(1), it.getString(2), it.getString(3), it.getString(4))
    }

    // ---- messages ----------------------------------------------------------------------------------------

    fun addMessage(m: ChatMsg, state: MsgState, mine: Boolean, attachment: String? = null) {
        db.execSQL(
            "INSERT OR IGNORE INTO messages VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(m.id, m.roomKind.ordinal, m.room, m.roomLabel, m.fromUid, m.fromCallsign, m.text, m.time, m.loc?.lat, m.loc?.lon, state.ordinal, if (mine) 1 else 0, if (mine) 0 else 1, attachment),
        )
        bump()
    }

    /** Delivery state only ever moves forward: queued, sent, delivered, read. */
    fun setState(msgId: String, state: MsgState) {
        db.execSQL("UPDATE messages SET state = ? WHERE id = ? AND mine = 1 AND state < ?", arrayOf<Any?>(state.ordinal, msgId, state.ordinal)); bump()
    }

    fun messages(kind: RoomKind, room: String): List<StoredMsg> = query(
        "SELECT id, kind, room, label, fromUid, fromCs, body, time, lat, lon, state, mine, unread, attach FROM messages WHERE kind = ? AND room = ? ORDER BY time", kind.ordinal, room,
    ) {
        StoredMsg(
            ChatMsg(it.getString(0), RoomKind.values()[it.getInt(1)], it.getString(2), it.getString(4), it.getString(5), it.getString(6), it.getLong(7),
                if (it.isNull(8)) null else LatLon(it.getDouble(8), it.getDouble(9)), it.getString(3)),
            MsgState.values()[it.getInt(10)], it.getInt(11) == 1, it.getInt(12) == 1, it.getString(13),
        )
    }

    fun rooms(): List<Room> = query("SELECT kind, room, MAX(label), MAX(time), SUM(unread) FROM messages GROUP BY kind, room ORDER BY MAX(time) DESC") {
        Room(RoomKind.values()[it.getInt(0)], it.getString(1), it.getString(2), it.getLong(3), it.getInt(4))
    }

    /** Marks a room read and returns the incoming message ids that were unread (for read receipts). */
    fun markRead(kind: RoomKind, room: String): List<Pair<String, String>> {
        val ids = query("SELECT id, fromUid FROM messages WHERE kind = ? AND room = ? AND unread = 1", kind.ordinal, room) { it.getString(0) to it.getString(1) }
        if (ids.isNotEmpty()) { db.execSQL("UPDATE messages SET unread = 0 WHERE kind = ? AND room = ?", arrayOf<Any?>(kind.ordinal, room)); bump() }
        return ids
    }

    fun unread(): Int = query("SELECT COALESCE(SUM(unread), 0) FROM messages") { it.getInt(0) }.first()

    // ---- store and forward -------------------------------------------------------------------------------

    fun enqueue(msgId: String?, xml: String, compact: ByteArray?, mission: String, everyLink: Boolean) =
        db.execSQL("INSERT INTO outbox (msgId, xml, compact, mission, everyLink) VALUES (?,?,?,?,?)", arrayOf<Any?>(msgId, xml, compact, mission, if (everyLink) 1 else 0))

    fun outbox(): List<Outgoing> = query("SELECT n, msgId, xml, compact, mission, everyLink FROM outbox ORDER BY n LIMIT 200") {
        Outgoing(it.getLong(0), it.getString(1), it.getString(2), it.getBlob(3), it.getString(4), it.getInt(5) == 1)
    }

    fun dequeue(n: Long) = db.execSQL("DELETE FROM outbox WHERE n = $n")
    fun queued(): Int = query("SELECT COUNT(*) FROM outbox") { it.getInt(0) }.first()

    fun close() = runCatching { db.close() }
}
