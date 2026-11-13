package app.pquest.map

import android.database.sqlite.SQLiteDatabase
import app.pquest.coords.LatLon
import java.io.File
import kotlin.math.*

data class BBox(val south: Double, val west: Double, val north: Double, val east: Double)

/** Web-mercator XYZ tile arithmetic. */
object TileMath {
    fun fx(lon: Double, z: Int) = (lon + 180) / 360 * (1 shl z)
    fun fy(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return (1 - asinh(tan(r)) / PI) / 2 * (1 shl z)
    }
    fun x(lon: Double, z: Int) = floor(fx(lon, z)).toInt().coerceIn(0, (1 shl z) - 1)
    fun y(lat: Double, z: Int) = floor(fy(lat, z)).toInt().coerceIn(0, (1 shl z) - 1)

    /** Inclusive tile range x0, y0, x1, y1 covering [b] at zoom [z]. */
    fun range(b: BBox, z: Int) = intArrayOf(x(b.west, z), y(b.north, z), x(b.east, z), y(b.south, z))

    fun count(b: BBox, zMin: Int, zMax: Int): Long = (zMin..zMax).sumOf { z ->
        val r = range(b, z)
        (r[2] - r[0] + 1).toLong() * (r[3] - r[1] + 1)
    }

    fun around(c: LatLon, radiusM: Double): BBox {
        val dLat = radiusM / 111320.0
        val dLon = radiusM / (111320.0 * cos(Math.toRadians(c.lat)))
        return BBox(c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon)
    }

    /** Terrarium elevation encoding: metres = R*256 + G + B/256 - 32768. */
    fun terrarium(argb: Int) = ((argb shr 16 and 255) * 256 + (argb shr 8 and 255) + (argb and 255) / 256.0) - 32768
}

interface TileSource {
    fun get(z: Int, x: Int, y: Int): ByteArray?
    val minZoom: Int
    val maxZoom: Int
    val bounds: BBox?
    fun close()
}

/** MBTiles raster archive. Also the on-device format for downloaded and auto-cached tiles. */
class MbTiles(val file: File, private val writable: Boolean) : TileSource {
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.path, null,
        if (writable) SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY else SQLiteDatabase.OPEN_READONLY
    )

    init {
        if (writable) {
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB, PRIMARY KEY (zoom_level, tile_column, tile_row))")
        } else {
            db.rawQuery("SELECT 1 FROM tiles LIMIT 1", null).close() // throws if this is not an MBTiles file
        }
    }

    private fun row(z: Int, y: Int) = (1 shl z) - 1 - y

    fun meta(key: String): String? = try {
        db.rawQuery("SELECT value FROM metadata WHERE name = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) { null }

    override fun get(z: Int, x: Int, y: Int): ByteArray? =
        db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=$z AND tile_column=$x AND tile_row=${row(z, y)}", null)
            .use { if (it.moveToFirst()) it.getBlob(0) else null }

    fun has(z: Int, x: Int, y: Int): Boolean =
        db.rawQuery("SELECT 1 FROM tiles WHERE zoom_level=$z AND tile_column=$x AND tile_row=${row(z, y)}", null).use { it.moveToFirst() }

    fun put(z: Int, x: Int, y: Int, data: ByteArray) =
        db.execSQL("INSERT OR REPLACE INTO tiles VALUES (?,?,?,?)", arrayOf<Any>(z, x, row(z, y), data))

    fun setMeta(key: String, value: String) = db.execSQL("INSERT OR REPLACE INTO metadata VALUES (?, ?)", arrayOf<Any>(key, value))

    private fun zoom(fn: String): Int =
        db.rawQuery("SELECT $fn(zoom_level) FROM tiles", null).use { if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else -1 }

    override val minZoom get() = zoom("MIN")
    override val maxZoom get() = zoom("MAX")
    override val bounds: BBox?
        get() = meta("bounds")?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 4 }
            ?.let { BBox(it[1], it[0], it[3], it[2]) }

    override fun close() = db.close()
}

/** GeoPackage raster tiles. Supports the common case: one tile table on the global web-mercator (EPSG:3857) grid. */
class Gpkg(val file: File) : TileSource {
    private val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    private val table: String
    override val minZoom: Int
    override val maxZoom: Int
    override val bounds: BBox?

    init {
        val c = db.rawQuery(
            "SELECT c.table_name, c.min_x, c.min_y, c.max_x, c.max_y, s.organization_coordsys_id FROM gpkg_contents c " +
                "JOIN gpkg_spatial_ref_sys s ON s.srs_id = c.srs_id WHERE c.data_type = 'tiles' LIMIT 1", null
        )
        c.use {
            require(it.moveToFirst()) { "no tile table" }
            require(it.getInt(5) == 3857 || it.getInt(5) == 900913) { "not EPSG:3857" }
            table = it.getString(0).replace("\"", "")
            bounds = if (it.isNull(1)) null else BBox(lat(it.getDouble(2)), lon(it.getDouble(1)), lat(it.getDouble(4)), lon(it.getDouble(3)))
        }
        var lo = Int.MAX_VALUE
        var hi = -1
        db.rawQuery("SELECT zoom_level, matrix_width FROM gpkg_tile_matrix WHERE table_name = ?", arrayOf(table)).use {
            while (it.moveToNext()) {
                require(it.getLong(1) == 1L shl it.getInt(0)) { "not the global XYZ grid" }
                lo = min(lo, it.getInt(0)); hi = max(hi, it.getInt(0))
            }
        }
        require(hi >= 0) { "no zoom levels" }
        minZoom = lo; maxZoom = hi
    }

    private fun lon(x: Double) = x / 20037508.342789244 * 180
    private fun lat(y: Double) = Math.toDegrees(atan(sinh(y / 6378137.0)))

    override fun get(z: Int, x: Int, y: Int): ByteArray? =
        db.rawQuery("SELECT tile_data FROM \"$table\" WHERE zoom_level=$z AND tile_column=$x AND tile_row=$y", null)
            .use { if (it.moveToFirst()) it.getBlob(0) else null }

    override fun close() = db.close()
}
