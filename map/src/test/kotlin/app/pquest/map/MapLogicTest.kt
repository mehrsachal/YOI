package app.pquest.map

import app.pquest.coords.Geodesy
import app.pquest.coords.LatLon
import org.json.JSONObject
import kotlin.test.*

class MapLogicTest {
    @Test fun tileIndices() {
        assertEquals(0, TileMath.x(-180.0, 5)); assertEquals(31, TileMath.x(179.999, 5))
        // Known tile: z10 over Islamabad-area longitude/latitude (OSM slippy-map convention).
        assertEquals(719, TileMath.x(73.0479, 10)); assertEquals(410, TileMath.y(33.6844, 10))
        assertEquals(0, TileMath.y(89.0, 3)); assertEquals(7, TileMath.y(-89.0, 3))
    }

    @Test fun radiusBoxAndCount() {
        val c = LatLon(35.0, 74.0)
        val b = TileMath.around(c, 10_000.0)
        assertEquals(10_000.0, Geodesy.distance(c, LatLon(b.north, c.lon)), 60.0)
        assertEquals(10_000.0, Geodesy.distance(c, LatLon(c.lat, b.east)), 60.0)
        assertEquals(1, TileMath.count(b, 5, 5))
        val r = TileMath.range(b, 15)
        assertEquals((r[2] - r[0] + 1).toLong() * (r[3] - r[1] + 1), TileMath.count(b, 15, 15))
        assertTrue(TileMath.count(b, 8, 15) in 300..900, "${TileMath.count(b, 8, 15)}")
    }

    @Test fun terrariumDecoding() {
        assertEquals(0.0, TileMath.terrarium(0xFF800000.toInt()))
        assertEquals(8848.0, TileMath.terrarium((0xFF shl 24) or (162 shl 16) or (144 shl 8)))
        assertEquals(-10.5, TileMath.terrarium((0xFF shl 24) or (127 shl 16) or (245 shl 8) or 128))
    }

    @Test fun styleIsValidAndOfflineOnly() {
        val json = StyleBuilder.build(
            StyleBuilder.Raster("topo", 0, 15), listOf(StyleBuilder.Raster("p1", 9, 14, 0.6f, BBox(19.0, 63.0, 21.0, 65.0))),
            demMaxZoom = 13, hillshade = true, night = true, version = 7, slope = true, contours = true
        )
        java.io.File("build/style-sample.json").apply { parentFile.mkdirs() }.writeText(json)
        for (id in listOf("gfx_fill", "gfx_line", "labels", "items", "contacts", "slope", "contour")) assertTrue(json.contains("\"id\":\"$id\""), id)
        val o = JSONObject(json)
        val src = o.getJSONObject("sources")
        assertEquals("terrarium", src.getJSONObject("dem").getString("encoding"))
        assertEquals(14, src.getJSONObject("r_p1").getInt("maxzoom"))
        for (k in src.keys()) src.getJSONObject(k).optJSONArray("tiles")?.let {
            assertTrue(it.getString(0).startsWith("https://${StyleBuilder.HOST}/"), it.getString(0))
        }
        assertFalse(json.contains("glyphs")); assertFalse(json.contains("http://"))
        val noDem = JSONObject(StyleBuilder.build(null, emptyList(), null, true, false, 1))
        assertFalse(noDem.getJSONObject("sources").has("dem"))
    }

    @Test fun geoJsonHelpers() {
        val l = JSONObject(StyleBuilder.line(listOf(LatLon(1.0, 2.0), LatLon(3.0, 4.0))))
        assertEquals(2, l.getJSONArray("features").length())
        assertEquals(0, JSONObject(StyleBuilder.line(emptyList())).getJSONArray("features").length())
        assertEquals("#f00", JSONObject(StyleBuilder.marks(listOf(LatLon(1.0, 2.0) to "#f00"))).getJSONArray("features")
            .getJSONObject(0).getJSONObject("properties").getString("c"))
    }
}
