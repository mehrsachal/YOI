package app.pquest.map

import android.graphics.BitmapFactory
import android.graphics.Color
import app.pquest.coords.LatLon
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Exercises real SQLite and bitmap code off-device: pack import, the tile gateway, elevation, GeoTIFF. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapHubTest {
    private val demo = File("../app/src/main/assets/demo")
    private fun res(n: String) = File(javaClass.getResource("/$n")!!.toURI())
    private fun hub() = MapHub(RuntimeEnvironment.getApplication())
    private val peakArea = LatLon(20.06, 64.07) // on the fictional demo island

    private fun gateway(h: MapHub, path: String): okhttp3.Response =
        h.mapClient.newCall(okhttp3.Request.Builder().url("https://${StyleBuilder.HOST}/$path").build()).execute()

    @Test fun importsMbtilesAndServesTilesThroughTheGateway() {
        val h = hub()
        val topo = demo.resolve("demo-topo.mbtiles").inputStream().use { h.importPack(it, "demo-topo.mbtiles") }
        assertFalse(topo.dem); assertEquals(8, topo.minZoom); assertEquals(14, topo.maxZoom); assertNotNull(topo.bounds)
        val x = TileMath.x(peakArea.lon, 12); val y = TileMath.y(peakArea.lat, 12)
        gateway(h, "${topo.id}/12/$x/$y").use { r ->
            assertEquals(200, r.code)
            val bmp = BitmapFactory.decodeStream(r.body!!.byteStream())
            assertEquals(256, bmp.width)
        }
        gateway(h, "${topo.id}/12/0/0").use { assertEquals(404, it.code) }
        gateway(h, "topo/12/$x/$y").use { assertEquals("offline and empty store", 404, it.code) }
        // Anything that is not our own tile URL is refused without touching the network.
        h.mapClient.newCall(okhttp3.Request.Builder().url("https://example.com/12/1/1.png").build()).execute().use { assertEquals(403, it.code) }
        val style = h.style("topo", hillshade = true, night = false)
        assertTrue(style.contains("r_${topo.id}")); assertFalse(style.contains("\"dem\""))
        h.remove(topo.id); assertTrue(h.packs.value.isEmpty())
    }

    @Test fun elevationSlopeAndContoursFromDemPack() {
        val h = hub()
        assertNull(h.elevation(peakArea))
        val dem = demo.resolve("demo-dem.mbtiles").inputStream().use { h.importPack(it, "demo-dem.mbtiles") }
        assertTrue(dem.dem)
        val e = h.elevation(peakArea)!!
        assertTrue("on the island: $e", e > 50 && e < 3000)
        assertEquals(0.0, h.elevation(LatLon(20.0, 64.005))!!, 0.01) // sea, inside the pack
        assertNull(h.elevation(LatLon(30.0, 70.0)))                   // no data there
        val x = TileMath.x(peakArea.lon, 13); val y = TileMath.y(peakArea.lat, 13)
        for (id in listOf("slope", "contour")) gateway(h, "$id/13/$x/$y").use { r ->
            assertEquals(id, 200, r.code)
            val bmp = BitmapFactory.decodeStream(r.body!!.byteStream())
            var inked = 0; var clear = 0
            for (py in 0 until 256 step 3) for (px in 0 until 256 step 3) if (Color.alpha(bmp.getPixel(px, py)) > 0) inked++ else clear++
            assertTrue("$id inked=$inked clear=$clear", inked > 50 && clear > 50)
        }
        val style = h.style(null, hillshade = true, night = true, slope = true, contours = true)
        for (k in listOf("\"dem\"", "\"slope\"", "\"contour\"", "hillshade")) assertTrue(k, style.contains(k))
        org.json.JSONObject(style)
    }

    @Test fun demoDownloadFillsTheOfflineStoreWithoutNetwork() = kotlinx.coroutines.runBlocking {
        val h = hub()
        demo.resolve("demo-topo.mbtiles").inputStream().use { h.importPack(it, "demo-topo.mbtiles") }
        assertFalse(h.canFetch("topo"))
        h.demoDownload = true
        val box = TileMath.around(peakArea, 3000.0)
        assertTrue(h.estimateBytes(listOf("topo"), box, 10, 13) > 0)
        val p = h.download(listOf("topo"), box, 10, 13) {}
        assertEquals(0, p.failed); assertEquals(TileMath.count(box, 10, 13).toInt(), p.done)
        h.demoDownload = false
        gateway(h, "topo/13/${TileMath.x(peakArea.lon, 13)}/${TileMath.y(peakArea.lat, 13)}").use { assertEquals(200, it.code) }
        assertTrue(h.style("topo", false, false).contains("\"r_topo\""))
        assertTrue(h.storedBytes("topo") > 10_000)
    }

    @Test fun rejectsFilesThatAreNotMapPacks() {
        val h = hub()
        for (bad in listOf("hello world".toByteArray(), ByteArray(4096))) {
            try { h.importPack(bad.inputStream(), "x.mbtiles"); fail() } catch (e: IllegalArgumentException) {}
        }
        try { res("plain.tif").inputStream().use { h.importPack(it, "plain.tif") }; fail("ungeoreferenced TIFF") } catch (e: IllegalArgumentException) { assertTrue(e.message, e.message!!.contains("georeference")) }
        assertTrue(h.packs.value.isEmpty())
    }

    @Test fun geoTiffPixelsAndGeoreference() {
        for (name in listOf("geo_none.tif", "geo_lzw.tif", "utm_deflate.tif")) GeoTiff(res(name)).use { t ->
            assertEquals(300, t.width); assertEquals(200, t.height)
            for ((x, y) in listOf(0 to 0, 299 to 199, 123, 45).let { listOf(0 to 0, 299 to 199, 123 to 45, 7 to 150) }) {
                val c = t.argb(x, y)
                assertEquals("$name alpha", 255, Color.alpha(c)); assertEquals("$name red", x % 256, Color.red(c)); assertEquals("$name green", y, Color.green(c))
                assertEquals("$name blue", (x / 7 * 13 + y / 5 * 29) % 256, Color.blue(c))
            }
            assertEquals(0, t.argb(-1, 0)); assertEquals(0, t.argb(300, 0))
        }
        GeoTiff(res("geo_none.tif")).use { t ->
            assertEquals(4326, t.epsg)
            val (px, py) = t.pixel(20.1 - 0.0005 * 50, 64.0 + 0.0005 * 100)
            assertEquals(100.0, px, 1e-6); assertEquals(50.0, py, 1e-6)
            assertEquals(20.0, t.bounds.south, 1e-9); assertEquals(64.15, t.bounds.east, 1e-9)
            assertEquals(12, t.nativeZoom) // 0.0005 deg is about 52 m per pixel at this latitude
        }
        GeoTiff(res("utm_deflate.tif")).use { t ->
            assertEquals(32641, t.epsg)
            // Easting 600000 + 30*100, northing 2216000 - 30*50 in zone 41N must land on pixel (100, 50).
            val ll = app.pquest.coords.Utm(41, true, 603000.0, 2214500.0).toLatLon()
            val (px, py) = t.pixel(ll.lat, ll.lon)
            assertEquals(100.0, px, 1e-3); assertEquals(50.0, py, 1e-3)
        }
        GeoTiff(res("merc_palette.tif")).use { assertEquals(3857, it.epsg); assertEquals(255, Color.alpha(it.argb(10, 10))) }
        GeoTiff(res("grey.tif")).use { val c = it.argb(40, 40); assertEquals(Color.red(c), Color.green(c)); assertEquals(Color.red(c), Color.blue(c)) }
    }

    @Test fun geoTiffImportProducesCorrectlyPlacedTiles() {
        val h = hub()
        var last = 0f
        val pack = res("geo_lzw.tif").inputStream().use { h.importPack(it, "sheet 43.tif") { p -> last = p } }
        assertEquals("sheet 43", pack.name); assertEquals(12, pack.maxZoom); assertEquals(6, pack.minZoom); assertEquals(1f, last, 1e-6f)
        // Source pixel (100, 50) is at 64.05E 20.075N; the tile pixel there must carry that pixel's colour.
        val lon = 64.0 + 0.0005 * 100.5; val lat = 20.1 - 0.0005 * 50.5; val z = 12
        val bmp = h.local(pack.id, z, TileMath.x(lon, z), TileMath.y(lat, z))!!.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val c = bmp.getPixel(((TileMath.fx(lon, z) % 1) * 256).toInt(), ((TileMath.fy(lat, z) % 1) * 256).toInt())
        assertEquals(100.0, Color.red(c).toDouble(), 1.0); assertEquals(50.0, Color.green(c).toDouble(), 1.0)
        assertNull(h.local(pack.id, 12, 0, 0))
    }
}
