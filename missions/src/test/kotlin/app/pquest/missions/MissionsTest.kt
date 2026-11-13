package app.pquest.missions

import app.pquest.coords.LatLon
import app.pquest.cot.Item
import app.pquest.cot.Kind
import app.pquest.cot.Self
import app.pquest.cot.Sidc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class MissionsTest {
    private val self = Self("ANDROID-a", "ALPHA")
    private val ctx = Formats.Ctx("ANDROID-a", 1000)
    private fun marker(uid: String, updated: Long, author: String, name: String = "OP 1") =
        Item(uid, "m1", Kind.MARKER, Sidc.make('3', "10", "121100", "14"), name, "", listOf(LatLon(20.0, 64.0)), "#000000", updated, author)
    private val sample = listOf(
        marker("u1", 1000, "ANDROID-a", "1 <Pl> & Coy"),
        Item("u2", "m1", Kind.LINE, "10032500001403000000", "PL RED", "hold here", listOf(LatLon(20.0, 64.0), LatLon(20.1, 64.1)), "#FF0000", 1000, "ANDROID-a"),
        Item("u3", "m1", Kind.AREA, "10032500001517000000", "OBJ CAT", "", listOf(LatLon(20.0, 64.0), LatLon(20.1, 64.0), LatLon(20.1, 64.1)), "#0044FF", 1000, "ANDROID-a"),
        Item("u4", "m1", Kind.ROUTE, "", "RTE 1", "", listOf(LatLon(20.0, 64.0), LatLon(20.05, 64.05), LatLon(20.1, 64.0)), "#D4007A", 1000, "ANDROID-a"),
    )
    private fun shape(i: Item) = listOf(i.kind, i.name, i.points)

    // ---- mission sync merge: last writer wins per item, with a conflict log --------------------------------

    @Test fun newRemoteItemIsStored() = assertEquals(Merge.Result(marker("u1", 5, "b"), null), Merge.resolve(null, false, marker("u1", 5, "b")))

    @Test fun newerRemoteReplacesCleanLocalWithoutConflict() {
        val r = Merge.resolve(marker("u1", 5, "a"), false, marker("u1", 9, "b", "moved"))
        assertEquals("moved", r.store!!.name); assertNull(r.conflict)
    }

    @Test fun olderRemoteIsIgnored() = assertEquals(Merge.Result(null, null), Merge.resolve(marker("u1", 9, "a", "new"), false, marker("u1", 5, "b", "old")))

    @Test fun offlineEditLosesToNewerRemoteAndIsLogged() {
        val r = Merge.resolve(marker("u1", 5, "a", "mine"), true, marker("u1", 9, "b", "theirs"), now = 42)
        assertEquals("theirs", r.store!!.name)
        assertEquals(Conflict("u1", 42, "b", "a", "mine"), r.conflict)
    }

    @Test fun offlineEditWinsOverOlderRemoteAndIsLogged() {
        val r = Merge.resolve(marker("u1", 9, "a", "mine"), true, marker("u1", 5, "b", "theirs"), now = 42)
        assertNull(r.store); assertEquals(Conflict("u1", 42, "a", "b", "mine"), r.conflict)
    }

    @Test fun equalTimestampsResolveTheSameWayOnEveryDevice() {
        val a = marker("u1", 7, "ANDROID-a", "A"); val b = marker("u1", 7, "ANDROID-b", "B")
        assertEquals("B", Merge.resolve(a, true, b).store!!.name) // device A takes B
        assertNull(Merge.resolve(b, true, a).store)               // device B keeps B
        assertNotNull(Merge.resolve(a, false, b).conflict)
    }

    @Test fun identicalContentIsNotAConflict() = assertEquals(Merge.Result(null, null), Merge.resolve(marker("u1", 5, "a"), true, marker("u1", 9, "b")))

    @Test fun deletionIsAnEditLikeAnyOther() {
        val gone = marker("u1", 9, "b").copy(deleted = true)
        assertTrue(Merge.resolve(marker("u1", 5, "a"), false, gone).store!!.deleted)
        assertNull(Merge.resolve(marker("u1", 12, "a"), false, gone).store)
    }

    // ---- packages and links ------------------------------------------------------------------------------

    @Test fun dataPackageRoundTrip() {
        val m = Mission("m1", "OP DEMO & Co", "a2V5a2V5a2V5", Role.OWNER)
        val back = DataPackage.read(DataPackage.write("OP DEMO", sample, self, m))
        assertEquals(sample.toSet(), back.items.toSet())
        assertEquals(m.copy(role = Role.EDITOR), back.mission); assertNull(back.server)
        val names = Formats.unzip(DataPackage.write("x", sample, self)).keys
        assertTrue("MANIFEST/manifest.xml" in names && "u1/u1.cot" in names)
    }

    @Test fun connectionPackageIsRecognised() {
        val pref = """<preferences><preference version="1" name="cot_streams"><entry key="count" class="class java.lang.Integer">1</entry><entry key="connectString0" class="class java.lang.String">tak.unit.example:8089:ssl</entry></preference><preference version="1" name="com.atakmap.app_preferences"><entry key="caLocation" class="class java.lang.String">cert/truststore.p12</entry><entry key="caPassword" class="class java.lang.String">capass</entry><entry key="clientPassword" class="class java.lang.String">clpass</entry><entry key="certificateLocation" class="class java.lang.String">cert/alpha.p12</entry></preference></preferences>"""
        val bo = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bo).use { z ->
            for ((n, b) in listOf("certs/config.pref" to pref.toByteArray(), "certs/truststore.p12" to byteArrayOf(1, 2), "certs/alpha.p12" to byteArrayOf(3))) {
                z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(b); z.closeEntry()
            }
        }
        val s = DataPackage.read(bo.toByteArray()).server!!
        assertEquals("tak.unit.example", s.host); assertEquals(8089, s.port); assertEquals("capass", s.caPass); assertEquals("clpass", s.clientPass)
        assertContentEquals(byteArrayOf(1, 2), s.caP12); assertContentEquals(byteArrayOf(3), s.clientP12)
    }

    @Test fun qrLinks() {
        val m = Mission("m-1", "OP DEMO & Co", "a+b/c==", Role.OWNER)
        assertEquals(m.copy(role = Role.EDITOR), Links.parseMission(Links.mission(m)))
        val e = Links.Enrol("tak.unit.example", 8089, 8446, "alpha 1", "p@ss&word", "ab".repeat(32))
        assertEquals(e, Links.parseEnrol(Links.enrol(e)))
        assertNull(Links.parseMission("https://example.com")); assertNull(Links.parseEnrol("pquest://enroll?h=x&u=a&t=b&ca=short"))
        val qr = Links.items(sample.take(2), self)!!
        assertTrue(qr.length <= Links.MAX_QR)
        assertEquals(sample.take(2), Links.parseItems(qr))
        assertNull(Links.items((1..300).map { marker("u$it", 1, "a", "name $it") }, self))
        assertNull(Links.parseItems("pquest://items?z=%%%"))
    }

    // ---- formats -----------------------------------------------------------------------------------------

    @Test fun kmlAndKmzRoundTrip() {
        val kml = Formats.toKml("Overlay", sample) { if (it.uid == "u1") "u1.png" else null }
        assertTrue(kml.contains("<href>icons/u1.png</href>") && kml.contains("<color>ff0000ff</color>"), kml)
        val back = Formats.import("x.kml", kml.toByteArray(), ctx)
        assertEquals(sample.map(::shape), back.map(::shape))
        assertEquals(sample.map { it.sidc }, back.map { it.sidc }); assertEquals("hold here", back[1].remarks); assertEquals("#FF0000", back[1].color)
        val kmz = Formats.toKmz("Overlay", sample, mapOf("u1.png" to byteArrayOf(1))) { null }
        assertEquals(4, Formats.import("x.kmz", kmz, ctx).size)
    }

    @Test fun foreignKml() {
        val kml = """<kml xmlns="http://www.opengis.net/kml/2.2"><Document><Folder><Placemark><name>Camp</name><Point><coordinates>64.5,20.5,120</coordinates></Point></Placemark>
            <Placemark><name>Track</name><LineString><coordinates>
              64.0,20.0,0 64.1,20.1,0
              64.2,20.2,0 </coordinates></LineString></Placemark></Folder></Document></kml>"""
        val it = Formats.import("a.KML", kml.toByteArray(), ctx)
        assertEquals(listOf(LatLon(20.5, 64.5)), it[0].points); assertEquals(Sidc.POINT_OF_INTEREST, it[0].sidc); assertEquals(3, it[1].points.size)
    }

    @Test fun gpxRoundTrip() {
        val back = Formats.import("x.gpx", Formats.toGpx(sample).toByteArray(), ctx)
        assertEquals(listOf(Kind.MARKER, Kind.ROUTE, Kind.LINE, Kind.LINE), back.map { it.kind })
        assertEquals(sample[3].points, back[1].points); assertEquals("1 <Pl> & Coy", back[0].name); assertEquals(4, back[3].points.size)
    }

    @Test fun geoJsonRoundTrip() {
        val back = Formats.import("x.geojson", Formats.toGeoJson(sample).toByteArray(), ctx)
        assertEquals(sample.map(::shape), back.map(::shape)); assertEquals(sample.map { it.sidc }, back.map { it.sidc })
        val bare = Formats.geoJson("""{"type":"MultiPolygon","coordinates":[[[[64,20],[64.1,20],[64.1,20.1],[64,20]]]]}""", ctx)
        assertEquals(Kind.AREA, bare[0].kind); assertEquals(3, bare[0].points.size)
    }

    @Test fun csvWithLatLonOrGrid() {
        val a = Formats.import("p.csv", "Name,Lat,Lon,Notes\n\"Post, north\",20.5,64.5,\"say \"\"hi\"\"\"\nbad,x,y,\n".toByteArray(), ctx)
        assertEquals(1, a.size); assertEquals("Post, north", a[0].name); assertEquals("say \"hi\"", a[0].remarks); assertEquals(LatLon(20.5, 64.5), a[0].points[0])
        val g = Formats.import("p.csv", "name,grid\nPicquet 4,41Q PC 04609 11793\n".toByteArray(), ctx)
        assertEquals(20.0, g[0].points[0].lat, 1e-4); assertEquals(64.0, g[0].points[0].lon, 1e-4)
        assertFailsWith<IllegalArgumentException> { Formats.import("p.csv", "a,b\n1,2\n".toByteArray(), ctx) }
    }

    @Test fun shapefilePointsLinesPolygons() {
        fun file(type: Int, body: ByteBuffer.() -> Unit): ByteArray {
            val rec = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN).apply { putInt(type); body() }
            val len = rec.position()
            val f = ByteBuffer.allocate(100 + 8 + len)
            f.putInt(0, 9994); f.order(ByteOrder.BIG_ENDIAN).putInt(100, 1).putInt(104, len / 2)
            f.position(108); f.put(rec.array(), 0, len)
            return f.array()
        }
        val p = Formats.import("a.shp", file(1) { putDouble(64.5); putDouble(20.5) }, ctx)
        assertEquals(listOf(LatLon(20.5, 64.5)), p[0].points)
        val ring = listOf(64.0 to 20.0, 64.1 to 20.0, 64.1 to 20.1, 64.0 to 20.0)
        fun ByteBuffer.poly() { repeat(4) { putDouble(0.0) }; putInt(1); putInt(4); putInt(0); ring.forEach { putDouble(it.first); putDouble(it.second) } }
        assertEquals(4, Formats.import("a.shp", file(3) { poly() }, ctx)[0].points.size)
        val area = Formats.import("a.shp", file(5) { poly() }, ctx)[0]
        assertEquals(Kind.AREA, area.kind); assertEquals(3, area.points.size)
        assertFailsWith<IllegalArgumentException> { Formats.import("a.shp", file(1) { putDouble(500000.0); putDouble(3700000.0) }, ctx) }
        assertFailsWith<IllegalArgumentException> { Formats.import("a.shp", ByteArray(50), ctx) }
        assertFailsWith<IllegalArgumentException> { Formats.import("a.docx", ByteArray(5), ctx) }
    }
}
