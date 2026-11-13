package app.pquest.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.pquest.coords.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** An imported map pack (MBTiles or GeoPackage). [dem] packs feed elevation and hillshade instead of being drawn. */
data class Pack(
    val id: String, val name: String, val file: String, val gpkg: Boolean, val dem: Boolean,
    val visible: Boolean, val opacity: Float, val minZoom: Int, val maxZoom: Int, val bounds: BBox?,
)

/**
 * Single owner of all map data on the device and the only path by which the map engine can reach a network.
 * MapLibre is given [mapClient]; every tile request is answered from local storage, and is forwarded upstream
 * only when an admin has enabled online maps and set an https template for that layer.
 */
class MapHub(private val ctx: Context) {
    companion object { val BUILT_IN = listOf("topo", "satellite", "dem") }

    private val dir = File(ctx.filesDir, "tiles").apply { mkdirs() }
    private val packDir = File(ctx.filesDir, "packs").apply { mkdirs() }
    private val registry = File(ctx.filesDir, "packs.json")
    private val stores = BUILT_IN.associateWith { MbTiles(File(dir, "$it.mbtiles"), true) }
    private val open = HashMap<String, TileSource>()
    private val upstream = OkHttpClient()

    val packs = MutableStateFlow<List<Pack>>(emptyList())
    /** Bumped whenever stored tiles change, so the style is rebuilt with fresh URLs. */
    val version = AtomicInteger(1)
    @Volatile var onlineEnabled = false
    @Volatile var templates: Map<String, String> = emptyMap()

    val mapClient: OkHttpClient = OkHttpClient.Builder().addInterceptor(Gateway()).build()

    init {
        val first = !registry.exists()
        if (!first) packs.value = load().filter { p ->
            runCatching { open[p.id] = openSource(File(p.file), p.gpkg) }.isSuccess
        }
        if (first) {
            save()
            for (n in ctx.assets.list("demo").orEmpty()) runCatching { ctx.assets.open("demo/$n").use { importPack(it, n) } }
        }
    }

    private fun openSource(f: File, gpkg: Boolean): TileSource = if (gpkg) Gpkg(f) else MbTiles(f, false)

    // ---- tile access -------------------------------------------------------------------------------------

    fun local(id: String, z: Int, x: Int, y: Int): ByteArray? {
        if (id == "slope" || id == "contour") return derived(id, z, x, y)
        stores[id]?.get(z, x, y)?.let { return it }
        if (id == "dem") return packs.value.firstNotNullOfOrNull { p -> if (p.dem) open[p.id]?.get(z, x, y) else null }
        return open[id]?.get(z, x, y)
    }

    /** Tour only: "downloads" come from the bundled demo map, so the step works with no network. */
    @Volatile var demoDownload = false
    private fun demoTopo() = packs.value.firstOrNull { !it.dem && it.name.startsWith("Demo island") }?.let { open[it.id] }

    fun canFetch(id: String) = (demoDownload && id == "topo") || (onlineEnabled && templates[id]?.startsWith("https://") == true)

    /** Slope shading and contour lines are drawn on the fly from the offline elevation tiles. */
    private fun derived(id: String, z: Int, x: Int, y: Int): ByteArray? {
        val bmp = demTile(z, x, y) ?: return null
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val e = FloatArray(w * h) { TileMath.terrarium(px[it]).toFloat() }
        val out = IntArray(w * h)
        if (id == "slope") {
            val lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * (y + 0.5) / (1 shl z)))))
            val res = (156543.03392 * Math.cos(Math.toRadians(lat)) / (1 shl z) * 256.0 / w).toFloat()
            for (yy in 0 until h) for (xx in 0 until w) {
                val l = e[yy * w + maxOf(xx - 1, 0)]; val r = e[yy * w + minOf(xx + 1, w - 1)]
                val u = e[maxOf(yy - 1, 0) * w + xx]; val d = e[minOf(yy + 1, h - 1) * w + xx]
                val deg = Math.toDegrees(Math.atan(Math.hypot(((r - l) / (2 * res)).toDouble(), ((d - u) / (2 * res)).toDouble())))
                out[yy * w + xx] = when { deg < 15 -> 0; deg < 25 -> 0x66FFE000; deg < 35 -> 0x80FF9000.toInt(); deg < 45 -> 0x99FF3000.toInt(); else -> 0xAAB00060.toInt() }
            }
        } else {
            val step = when { z >= 14 -> 20; z >= 12 -> 50; z >= 10 -> 100; else -> 250 }
            for (yy in 0 until h - 1) for (xx in 0 until w - 1) {
                val a = e[yy * w + xx]; val b = e[yy * w + xx + 1]; val c = e[(yy + 1) * w + xx]
                if (a <= 0 && b <= 0 && c <= 0) continue
                fun lv(v: Float, s: Int) = Math.floor((v / s).toDouble()).toInt()
                if (lv(a, step * 5) != lv(b, step * 5) || lv(a, step * 5) != lv(c, step * 5)) out[yy * w + xx] = 0xE65A3214.toInt()
                else if (lv(a, step) != lv(b, step) || lv(a, step) != lv(c, step)) out[yy * w + xx] = 0x99855A32.toInt()
            }
        }
        return png(Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888), true)
    }

    private fun png(b: Bitmap, alpha: Boolean): ByteArray = java.io.ByteArrayOutputStream().also {
        b.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, it)
    }.toByteArray()

    /** Fetches one tile from the admin-configured source and stores it. Returns null when offline or not allowed. */
    fun fetch(id: String, z: Int, x: Int, y: Int): ByteArray? {
        if (!canFetch(id)) return null
        if (demoDownload && id == "topo") return demoTopo()?.get(z, x, y)?.also { stores.getValue(id).put(z, x, y, it) }
        val url = templates.getValue(id).replace("{z}", "$z").replace("{x}", "$x").replace("{y}", "$y").replace("{s}", "a")
        return try {
            upstream.newCall(Request.Builder().url(url).header("User-Agent", "P-Quest").build()).execute().use { r ->
                val bytes = if (r.isSuccessful) r.body?.bytes() else null
                if (bytes != null && bytes.isNotEmpty()) { stores.getValue(id).put(z, x, y, bytes); bytes } else null
            }
        } catch (e: Exception) { null }
    }

    private inner class Gateway : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val seg = req.url.pathSegments
            var body: ByteArray? = null
            var code = 403 // anything that is not our own tile URL is refused without touching the network
            if (req.url.host == StyleBuilder.HOST && seg.size == 4) {
                val z = seg[1].toIntOrNull(); val x = seg[2].toIntOrNull(); val y = seg[3].toIntOrNull()
                if (z != null && x != null && y != null) body = local(seg[0], z, x, y) ?: fetch(seg[0], z, x, y)
                code = if (body != null) 200 else 404
            }
            return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message(if (code == 200) "OK" else "No tile")
                .header("Cache-Control", "max-age=0")
                .body((body ?: ByteArray(0)).toResponseBody("application/octet-stream".toMediaType())).build()
        }
    }

    // ---- packs -------------------------------------------------------------------------------------------

    /** Copies a pack into private storage and registers it. Throws IllegalArgumentException if unsupported. */
    @Synchronized
    fun importPack(input: InputStream, displayName: String, onProgress: (Float) -> Unit = {}): Pack {
        val id = UUID.randomUUID().toString().take(8)
        var f = File(packDir, "$id.db")
        f.outputStream().use { input.copyTo(it, 1 shl 16) }
        try {
            val magic = ByteArray(4).also { m -> f.inputStream().use { it.read(m) } }
            if ((magic[0] == 'I'.code.toByte() && magic[1] == 'I'.code.toByte()) || (magic[0] == 'M'.code.toByte() && magic[1] == 'M'.code.toByte())) {
                val tiles = File(packDir, "$id.mbtiles")
                try { tileGeoTiff(f, tiles, displayName.substringBeforeLast('.'), onProgress) } finally { f.delete() }
                f = tiles
            }
            val isGpkg = runCatching { Gpkg(f).close() }.isSuccess
            val src = openSource(f, isGpkg)
            val mb = src as? MbTiles
            val fmt = mb?.meta("format")?.lowercase()
            require(fmt != "pbf" && fmt != "mvt") { "vector tiles are not supported" }
            require(src.maxZoom >= 0) { "no tiles" }
            val pack = Pack(
                id, mb?.meta("name") ?: displayName.substringBeforeLast('.'), f.path, isGpkg,
                dem = mb?.meta("encoding")?.lowercase() == "terrarium",
                visible = true, opacity = 1f, minZoom = src.minZoom, maxZoom = src.maxZoom, bounds = src.bounds,
            )
            open[id] = src
            packs.value = packs.value + pack
            save(); version.incrementAndGet()
            return pack
        } catch (e: Exception) {
            f.delete()
            throw IllegalArgumentException(e.message ?: "unsupported file", e)
        }
    }

    /** Cuts a georeferenced TIFF into web-mercator tiles, from its native zoom down six levels. */
    private fun tileGeoTiff(src: File, dst: File, name: String, onProgress: (Float) -> Unit) {
        GeoTiff(src).use { tif ->
            val b = tif.bounds
            var zMax = tif.nativeZoom.coerceAtMost(17)
            while (zMax > 4 && TileMath.count(b, maxOf(zMax - 6, 0), zMax) > 40_000) zMax--
            val zMin = maxOf(zMax - 6, 0)
            val total = TileMath.count(b, zMin, zMax).toFloat()
            val out = MbTiles(dst, true)
            try {
                out.setMeta("name", name); out.setMeta("format", "png"); out.setMeta("bounds", "${b.west},${b.south},${b.east},${b.north}")
                val px = IntArray(256 * 256); var done = 0
                for (z in zMax downTo zMin) {
                    val r = TileMath.range(b, z)
                    for (x in r[0]..r[2]) for (y in r[1]..r[3]) {
                        if (tif.tile(z, x, y, px)) out.put(z, x, y, png(Bitmap.createBitmap(px, 256, 256, Bitmap.Config.ARGB_8888), px.any { it ushr 24 != 255 }))
                        onProgress(++done / total)
                    }
                }
                require(out.maxZoom >= 0) { "image does not cover any map tile" }
            } catch (e: Exception) { out.close(); dst.delete(); throw e }
            out.close()
        }
    }

    @Synchronized
    fun update(p: Pack) { packs.value = packs.value.map { if (it.id == p.id) p else it }; save() }

    @Synchronized
    fun remove(id: String) {
        val p = packs.value.firstOrNull { it.id == id } ?: return
        packs.value = packs.value - p
        open.remove(id)?.close()
        File(p.file).delete()
        save(); version.incrementAndGet()
    }

    private fun save() = registry.writeText(JSONArray(packs.value.map { p ->
        JSONObject().put("id", p.id).put("name", p.name).put("file", p.file).put("gpkg", p.gpkg).put("dem", p.dem)
            .put("visible", p.visible).put("opacity", p.opacity.toDouble()).put("minZoom", p.minZoom).put("maxZoom", p.maxZoom)
            .put("bounds", p.bounds?.let { JSONArray(listOf(it.south, it.west, it.north, it.east)) })
    }).toString())

    private fun load(): List<Pack> = runCatching {
        val a = JSONArray(registry.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val b = o.optJSONArray("bounds")
            Pack(
                o.getString("id"), o.getString("name"), o.getString("file"), o.getBoolean("gpkg"), o.getBoolean("dem"),
                o.getBoolean("visible"), o.getDouble("opacity").toFloat(), o.getInt("minZoom"), o.getInt("maxZoom"),
                b?.let { BBox(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3)) },
            )
        }
    }.getOrDefault(emptyList())

    // ---- style -------------------------------------------------------------------------------------------

    fun demMaxZoom(): Int? {
        val z = (packs.value.filter { it.dem }.map { it.maxZoom } + stores.getValue("dem").maxZoom).max()
        return if (z >= 0) z else if (canFetch("dem")) 12 else null
    }

    fun style(base: String?, hillshade: Boolean, night: Boolean, slope: Boolean = false, contours: Boolean = false): String {
        val b = base?.let { id ->
            val stored = stores[id]?.maxZoom ?: -1
            val max = if (canFetch(id)) 17 else stored
            if (max >= 0) StyleBuilder.Raster(id, 0, max) else null
        }
        val overlays = packs.value.filter { it.visible && !it.dem }
            .map { StyleBuilder.Raster(it.id, it.minZoom, it.maxZoom, it.opacity, it.bounds) }
        return StyleBuilder.build(b, overlays, demMaxZoom(), hillshade, night, version.get(), slope, contours)
    }

    fun storedBytes(id: String) = stores[id]?.file?.length() ?: 0L

    // ---- area download -----------------------------------------------------------------------------------

    data class Progress(val done: Int, val total: Int, val failed: Int)

    /** Rough average tile sizes used for the pre-download estimate. */
    fun estimateBytes(layers: List<String>, box: BBox, zMin: Int, zMax: Int): Long =
        layers.sumOf { TileMath.count(box, zMin, zMax) * if (it == "topo") 25_000L else 40_000L }

    suspend fun download(layers: List<String>, box: BBox, zMin: Int, zMax: Int, onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            val total = (TileMath.count(box, zMin, zMax) * layers.size).toInt()
            val done = AtomicInteger(); val failed = AtomicInteger()
            val gate = Semaphore(6)
            coroutineScope {
                for (layer in layers) for (z in zMin..zMax) {
                    val r = TileMath.range(box, z)
                    for (x in r[0]..r[2]) for (y in r[1]..r[3]) {
                        gate.acquire()
                        launch {
                            try {
                                if (!stores.getValue(layer).has(z, x, y) && fetch(layer, z, x, y) == null) failed.incrementAndGet()
                            } finally { gate.release() }
                            onProgress(Progress(done.incrementAndGet(), total, failed.get()))
                        }
                    }
                }
            }
            version.incrementAndGet()
            Progress(done.get(), total, failed.get())
        }

    // ---- elevation ---------------------------------------------------------------------------------------

    private val demCache = object : LruCache<Long, Any>(16) {}
    private val none = Any()

    private fun demTile(z: Int, x: Int, y: Int): Bitmap? {
        val key = (version.get().toLong() shl 56) or (z.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()
        demCache.get(key)?.let { return it as? Bitmap }
        val bmp = local("dem", z, x, y)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        demCache.put(key, bmp ?: none)
        return bmp
    }

    /** Ground elevation in metres from offline DEM tiles, or null where no DEM is loaded. */
    fun elevation(p: LatLon): Double? {
        val top = minOf(demMaxZoom() ?: return null, 15)
        for (z in top downTo 0) {
            val fx = TileMath.fx(p.lon, z); val fy = TileMath.fy(p.lat, z)
            val x = TileMath.x(p.lon, z); val y = TileMath.y(p.lat, z)
            val bmp = demTile(z, x, y) ?: continue
            val px = ((fx - x) * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val py = ((fy - y) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            return TileMath.terrarium(bmp.getPixel(px, py))
        }
        return null
    }
}
