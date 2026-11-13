package app.pquest.map

import app.pquest.coords.LatLon
import app.pquest.coords.UtmMath
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.math.*

/**
 * Reader for georeferenced TIFF map sheets and imagery, without native libraries.
 * Supports 8-bit grey, palette, RGB and RGBA; strips or tiles; no compression, LZW or Deflate;
 * georeferenced by pixel scale and tie point in WGS84 lat/long, web mercator or WGS84 UTM.
 * Anything else (JPEG compression, BigTIFF, other projections) is rejected with a reason.
 */
class GeoTiff(file: File) : Closeable {
    private val raf = RandomAccessFile(file, "r")
    private val order: ByteOrder
    val width: Int
    val height: Int
    private val spp: Int
    private val photometric: Int
    private val compression: Int
    private val predictor: Int
    private val palette: IntArray?
    private val chunkW: Int
    private val chunkH: Int
    private val across: Int
    private val offsets: LongArray
    private val counts: LongArray
    private val scaleX: Double
    private val scaleY: Double
    private val originX: Double
    private val originY: Double
    /** 4326 = lat/long, 3857 = web mercator, 326xx / 327xx = UTM north / south. */
    val epsg: Int
    private val cache = object : LinkedHashMap<Int, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Int, ByteArray>?) = size > 6
    }

    private fun read(at: Long, n: Int): ByteBuffer { val b = ByteArray(n); raf.seek(at); raf.readFully(b); return ByteBuffer.wrap(b).order(order) }

    init {
        try {
            val head = ByteArray(8); raf.readFully(head)
            order = when (String(head, 0, 2)) { "II" -> ByteOrder.LITTLE_ENDIAN; "MM" -> ByteOrder.BIG_ENDIAN; else -> throw IllegalArgumentException("not a TIFF file") }
            val hb = ByteBuffer.wrap(head).order(order)
            require(hb.getShort(2).toInt() == 42) { "BigTIFF is not supported" }
            val ifd = hb.getInt(4).toLong() and 0xFFFFFFFFL
            val n = read(ifd, 2).short.toInt() and 0xFFFF
            val entries = read(ifd + 2, n * 12)
            val tags = HashMap<Int, DoubleArray>()
            for (i in 0 until n) {
                val tag = entries.getShort(i * 12).toInt() and 0xFFFF; val type = entries.getShort(i * 12 + 2).toInt()
                val count = entries.getInt(i * 12 + 4)
                val size = when (type) { 1, 2, 6, 7 -> 1; 3, 8 -> 2; 4, 9, 11 -> 4; 5, 10, 12 -> 8; else -> 0 }
                if (size == 0 || count <= 0 || count > 4_000_000) continue
                val b = if (size.toLong() * count <= 4) ByteBuffer.wrap(ByteArray(4).also { entries.position(i * 12 + 8); entries.get(it) }).order(order)
                else read(entries.getInt(i * 12 + 8).toLong() and 0xFFFFFFFFL, size * count)
                tags[tag] = DoubleArray(count) { k ->
                    when (type) {
                        1, 2, 7 -> (b.get(k).toInt() and 0xFF).toDouble()
                        3 -> (b.getShort(k * 2).toInt() and 0xFFFF).toDouble()
                        4 -> (b.getInt(k * 4).toLong() and 0xFFFFFFFFL).toDouble()
                        5 -> (b.getInt(k * 8).toLong() and 0xFFFFFFFFL).toDouble() / (b.getInt(k * 8 + 4).toLong() and 0xFFFFFFFFL).coerceAtLeast(1)
                        11 -> b.getFloat(k * 4).toDouble()
                        12 -> b.getDouble(k * 8)
                        else -> 0.0
                    }
                }
            }
            fun int(tag: Int, def: Int) = tags[tag]?.get(0)?.toInt() ?: def
            width = int(256, 0); height = int(257, 0)
            require(width > 0 && height > 0) { "no image" }
            spp = int(277, 1); photometric = int(262, 1); compression = int(259, 1); predictor = int(317, 1)
            require(tags[258]?.all { it == 8.0 } ?: false) { "only 8-bit images are supported" }
            require(compression in setOf(1, 5, 8, 32946)) { if (compression == 7 || compression == 6) "JPEG-compressed TIFF is not supported" else "unsupported compression" }
            require(int(284, 1) == 1 && spp in 1..4) { "unsupported pixel layout" }
            palette = if (photometric == 3) tags[320]?.let { m -> val k = m.size / 3; IntArray(k) { i -> (0xFF shl 24) or ((m[i].toInt() shr 8) shl 16) or ((m[k + i].toInt() shr 8) shl 8) or (m[2 * k + i].toInt() shr 8) } } else null
            if (tags.containsKey(322)) {
                chunkW = int(322, 0); chunkH = int(323, 0)
                offsets = tags.getValue(324).map { it.toLong() }.toLongArray(); counts = tags.getValue(325).map { it.toLong() }.toLongArray()
            } else {
                chunkW = width; chunkH = min(int(278, height), height)
                offsets = tags.getValue(273).map { it.toLong() }.toLongArray()
                counts = tags[279]?.map { it.toLong() }?.toLongArray() ?: LongArray(offsets.size) { chunkW.toLong() * chunkH * spp }
            }
            across = (width + chunkW - 1) / chunkW
            require(compression == 1 || chunkW.toLong() * chunkH * spp <= 64_000_000) { "image must be tiled or striped to be read on a phone" }
            val scale = tags[33550]; val tie = tags[33922]
            require(scale != null && tie != null && tie.size >= 6 && scale[0] > 0 && scale[1] > 0) { "TIFF has no georeference (pixel scale and tie point)" }
            scaleX = scale[0]; scaleY = scale[1]
            originX = tie[3] - tie[0] * scaleX; originY = tie[4] + tie[1] * scaleY
            var projected = 0; var model = 0
            tags[34735]?.let { g -> for (k in 4 until g.size - 3 step 4) when (g[k].toInt()) { 1024 -> model = g[k + 3].toInt(); 3072 -> projected = g[k + 3].toInt() } }
            epsg = when {
                projected in setOf(3857, 900913, 3785) -> 3857
                projected in 32601..32660 || projected in 32701..32760 -> projected
                model == 2 || (projected == 0 && abs(originX) <= 180 && abs(originY) <= 90) -> 4326
                else -> throw IllegalArgumentException("unsupported projection (EPSG $projected); convert with tools/to_mbtiles.py")
            }
        } catch (e: Exception) {
            raf.close()
            throw if (e is IllegalArgumentException) e else IllegalArgumentException("unreadable TIFF", e)
        }
    }

    private fun toLatLon(x: Double, y: Double): LatLon = when (epsg) {
        4326 -> LatLon(y, x)
        3857 -> LatLon(Math.toDegrees(atan(sinh(y / 6378137.0))), x / 6378137.0 * 180 / PI)
        else -> UtmMath.inverse(epsg % 100, x, if (epsg >= 32700) y - 1e7 else y)
    }

    /** Fractional pixel position of a ground point (may lie outside the image). */
    fun pixel(lat: Double, lon: Double): Pair<Double, Double> {
        val (x, y) = when (epsg) {
            4326 -> Pair(lon, lat)
            3857 -> Pair(lon * PI / 180 * 6378137.0, asinh(tan(Math.toRadians(lat.coerceIn(-85.05, 85.05)))) * 6378137.0)
            else -> UtmMath.forward(epsg % 100, lat, lon).let { Pair(it.first, if (epsg >= 32700) it.second + 1e7 else it.second) }
        }
        return Pair((x - originX) / scaleX, (originY - y) / scaleY)
    }

    val bounds: BBox by lazy {
        val pts = ArrayList<LatLon>()
        for (i in 0..8) for (j in 0..8) if (i % 8 == 0 || j % 8 == 0) pts += toLatLon(originX + width * scaleX * i / 8, originY - height * scaleY * j / 8)
        BBox(pts.minOf { it.lat }, pts.minOf { it.lon }, pts.maxOf { it.lat }, pts.maxOf { it.lon })
    }

    /** Deepest web-mercator zoom at which a tile pixel is no smaller than a source pixel. */
    val nativeZoom: Int by lazy {
        val mid = (bounds.south + bounds.north) / 2
        val res = if (epsg == 4326) scaleX * 111320.0 * cos(Math.toRadians(mid)) else if (epsg == 3857) scaleX * cos(Math.toRadians(mid)) else scaleX
        ceil(ln(156543.03392 * cos(Math.toRadians(mid)) / res) / ln(2.0)).toInt().coerceIn(0, 18)
    }

    private fun chunk(index: Int): ByteArray = cache.getOrPut(index) {
        val raw = ByteArray(counts[index].toInt()); raf.seek(offsets[index]); raf.readFully(raw)
        val want = chunkW * chunkH * spp
        val out = when (compression) {
            1 -> raw
            5 -> lzw(raw, want)
            else -> ByteArray(want).also { o -> Inflater().apply { setInput(raw); var n = 0; while (n < want && !finished()) { val k = inflate(o, n, want - n); if (k == 0 && needsInput()) break; n += k }; end() } }
        }
        if (predictor == 2 && compression != 1) for (row in 0 until chunkH) {
            val base = row * chunkW * spp
            for (i in spp until chunkW * spp) if (base + i < out.size) out[base + i] = (out[base + i] + out[base + i - spp]).toByte()
        }
        out
    }

    /** Colour of one pixel as ARGB, or 0 (transparent) outside the image. */
    @Synchronized
    fun argb(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        val v = IntArray(4)
        if (compression == 1 && chunkW == width) { // uncompressed strips: read the pixel directly, never a whole strip
            val strip = y / chunkH; val key = -1 - y
            val row = cache.getOrPut(key) { ByteArray(width * spp).also { raf.seek(offsets[strip] + (y - strip * chunkH).toLong() * width * spp); raf.readFully(it) } }
            for (s in 0 until spp) v[s] = row[x * spp + s].toInt() and 0xFF
        } else {
            val c = chunk((y / chunkH) * across + x / chunkW)
            val at = ((y % chunkH) * chunkW + x % chunkW) * spp
            if (at + spp > c.size) return 0
            for (s in 0 until spp) v[s] = c[at + s].toInt() and 0xFF
        }
        return when {
            palette != null -> palette.getOrElse(v[0]) { 0 }
            spp >= 3 -> ((if (spp == 4) v[3] else 255) shl 24) or (v[0] shl 16) or (v[1] shl 8) or v[2]
            else -> (if (photometric == 0) 255 - v[0] else v[0]).let { g -> ((if (spp == 2) v[1] else 255) shl 24) or (g shl 16) or (g shl 8) or g }
        }
    }

    /** Renders one 256 px web-mercator tile into [out] (ARGB). Returns false if the tile shows nothing. */
    fun tile(z: Int, x: Int, y: Int, out: IntArray): Boolean {
        val n = (1 shl z).toDouble(); val g = 16
        // Project a coarse grid exactly and interpolate between its nodes: accurate to a fraction of a pixel.
        val px = DoubleArray((g + 1) * (g + 1)); val py = DoubleArray((g + 1) * (g + 1))
        for (j in 0..g) for (i in 0..g) {
            val lon = (x + i / g.toDouble()) / n * 360 - 180
            val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * (y + j / g.toDouble()) / n))))
            val p = pixel(lat, lon); px[j * (g + 1) + i] = p.first; py[j * (g + 1) + i] = p.second
        }
        var any = false; val step = 256 / g
        for (ty in 0 until 256) for (tx in 0 until 256) {
            val i = tx / step; val j = ty / step; val fx = (tx % step + 0.5) / step; val fy = (ty % step + 0.5) / step
            val a = j * (g + 1) + i; val b = a + g + 1
            val sx = (px[a] * (1 - fx) + px[a + 1] * fx) * (1 - fy) + (px[b] * (1 - fx) + px[b + 1] * fx) * fy
            val sy = (py[a] * (1 - fx) + py[a + 1] * fx) * (1 - fy) + (py[b] * (1 - fx) + py[b + 1] * fx) * fy
            val c = argb(floor(sx).toInt(), floor(sy).toInt())
            out[ty * 256 + tx] = c
            if (c ushr 24 != 0) any = true
        }
        return any
    }

    override fun close() = raf.close()

    companion object {
        /** TIFF flavour of LZW: most significant bit first, with the "early change" of code width. */
        fun lzw(src: ByteArray, expected: Int): ByteArray {
            val out = ByteArray(expected); var op = 0
            val prefix = IntArray(4096); val suffix = ByteArray(4096); val stack = ByteArray(4097)
            var buf = 0; var bits = 0; var ip = 0; var width = 9; var next = 258; var old = -1
            while (op < expected) {
                while (bits < width) { if (ip >= src.size) return out; buf = (buf shl 8) or (src[ip++].toInt() and 0xFF); bits += 8 }
                val code = (buf shr (bits - width)) and ((1 shl width) - 1); bits -= width; buf = buf and ((1 shl bits) - 1)
                if (code == 257) break
                if (code == 256) { width = 9; next = 258; old = -1; continue }
                if (old == -1) { out[op++] = code.toByte(); old = code; continue }
                var cur = if (code < next) code else old
                var sp = 0
                while (cur >= 258) { stack[sp++] = suffix[cur]; cur = prefix[cur] }
                val first = cur.toByte(); stack[sp++] = first
                while (sp > 0 && op < expected) out[op++] = stack[--sp]
                if (code >= next && op < expected) out[op++] = first
                if (next < 4096) { prefix[next] = old; suffix[next] = first; next++ }
                if (next >= (1 shl width) - 1 && width < 12) width++
                old = code
            }
            return out
        }
    }
}
