package app.pquest.map

import app.pquest.coords.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import net.zetetic.database.sqlcipher.SQLiteDatabase

data class TrackInfo(val id: Long, val name: String, val start: Long, val points: Int)

/** Recorded tracks, held in the encrypted database. */
class TrackStore(private val db: SQLiteDatabase) {
    /** Incremented on every change so the UI can refresh. */
    val changes = MutableStateFlow(0)

    init {
        db.execSQL("CREATE TABLE IF NOT EXISTS tracks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, start INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS points (track INTEGER NOT NULL, t INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, alt REAL, acc REAL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS points_track ON points (track, t)")
    }

    @Synchronized
    fun start(name: String, t: Long): Long {
        db.execSQL("INSERT INTO tracks (name, start) VALUES (?, ?)", arrayOf<Any>(name, t))
        val id = db.rawQuery("SELECT last_insert_rowid()").use { it.moveToFirst(); it.getLong(0) }
        changes.value++
        return id
    }

    @Synchronized
    fun add(track: Long, t: Long, lat: Double, lon: Double, alt: Double?, acc: Float?) {
        db.execSQL("INSERT INTO points VALUES (?,?,?,?,?,?)", arrayOf<Any?>(track, t, lat, lon, alt, acc?.toDouble()))
        changes.value++
    }

    fun points(track: Long): List<LatLon> = db.rawQuery("SELECT lat, lon FROM points WHERE track = $track ORDER BY t").use {
        val out = ArrayList<LatLon>(it.count)
        while (it.moveToNext()) out.add(LatLon(it.getDouble(0), it.getDouble(1)))
        out
    }

    fun list(): List<TrackInfo> = db.rawQuery(
        "SELECT t.id, t.name, t.start, (SELECT COUNT(*) FROM points p WHERE p.track = t.id) FROM tracks t ORDER BY t.id DESC"
    ).use {
        val out = ArrayList<TrackInfo>()
        while (it.moveToNext()) out.add(TrackInfo(it.getLong(0), it.getString(1), it.getLong(2), it.getInt(3)))
        out
    }

    @Synchronized
    fun delete(track: Long) {
        db.execSQL("DELETE FROM points WHERE track = $track")
        db.execSQL("DELETE FROM tracks WHERE id = $track")
        changes.value++
    }
}
