package app.pquest.coords

import kotlin.math.abs
import kotlin.test.*

class CoordsTest {
    private val rows = javaClass.getResource("/vectors.csv")!!.readText().trim().lines().map { it.split(",") }

    @Test fun utmForwardMatchesPyproj() = rows.forEach { r ->
        val u = Utm.from(LatLon(r[0].toDouble(), r[1].toDouble()), r[3].toInt())
        assertEquals(r[4].toDouble(), u.easting, 0.002, r[2])
        assertEquals(r[5].toDouble(), u.northing, 0.002, r[2])
    }

    @Test fun utmRoundTripsBelowOneMillimetre() = rows.forEach { r ->
        val p = LatLon(r[0].toDouble(), r[1].toDouble())
        assertTrue(Geodesy.distance(p, Utm.from(p, r[3].toInt()).toLatLon()) < 0.001, r[2])
    }

    @Test fun mgrsFormatMatchesReference() = rows.forEach { r ->
        assertEquals(r[2], Mgrs.format(LatLon(r[0].toDouble(), r[1].toDouble()), 5, spaced = false))
    }

    /** Phase 1 acceptance: go-to MGRS lands within 1 m of the expected point. */
    @Test fun mgrsParseWithinOneMetre() = rows.forEach { r ->
        val got = assertNotNull(Mgrs.parse(r[2]), r[2])
        val corner = LatLon(r[6].toDouble(), r[7].toDouble())
        assertTrue(Geodesy.distance(got, corner) < 1.0, "${r[2]} corner off by ${Geodesy.distance(got, corner)}")
        assertTrue(Geodesy.distance(got, LatLon(r[0].toDouble(), r[1].toDouble())) < 1.5, r[2]) // 1 m cell diagonal
    }

    @Test fun mgrsPrecisionAndSpacing() {
        val p = LatLon(33.6844, 73.0479)
        assertEquals("43S CT 19050 28873", Mgrs.format(p))
        assertEquals("43S CT 190 288", Mgrs.format(p, 3))
        assertEquals("43S CT", Mgrs.format(p, 0))
        val c = assertNotNull(Mgrs.parse("43s ct 190 288"))
        assertEquals("43SCT1900028800", Mgrs.format(LatLon(c.lat + 1e-7, c.lon + 1e-7), 5, false))
        assertNull(Mgrs.parse("43SCT123"))
        assertNull(Mgrs.parse("hello"))
        assertNull(Mgrs.parse("43SIO1234"))
    }

    @Test fun parsesEveryFormat() {
        val p = LatLon(33.6844, 73.0479)
        for (f in CoordFormat.values()) {
            val q = assertNotNull(CoordText.parse(CoordText.format(p, f)), f.name)
            assertTrue(Geodesy.distance(p, q) < 3.5, "$f ${Geodesy.distance(p, q)}")
        }
        assertEquals(LatLon(-33.5, -70.25), CoordText.parse("33.5 S, 70.25 W"))
        assertEquals(LatLon(33.5, 73.25), CoordText.parse("33 30 N 73 15 E"))
        assertNull(CoordText.parse("95.0, 10.0"))
        assertNull(CoordText.parse("1 2 3"))
    }

    @Test fun vincentyKnownLine() {
        // Flinders Peak to Buninyong (Geoscience Australia test line, GRS80 ~ WGS84 at this precision).
        val (d, b) = Geodesy.inverse(LatLon(-37.951033417, 144.424867889), LatLon(-37.652821139, 143.926495528))
        assertTrue(abs(d - 54972.271) < 0.01, "$d")
        assertTrue(abs(b - 306.86816) < 0.0001, "$b")
    }

    @Test fun samplingCoversPath() {
        val pts = listOf(LatLon(20.0, 64.0), LatLon(20.0, 64.01), LatLon(20.01, 64.01))
        val s = Geodesy.sample(pts, 50)
        assertEquals(50, s.size)
        assertEquals(0.0, s.first().first)
        assertEquals(Geodesy.pathLength(pts), s.last().first, 1e-6)
        assertTrue(Geodesy.distance(s.last().second, pts.last()) < 0.01)
    }

    @Test fun dtg() {
        val t = java.time.OffsetDateTime.of(2026, 11, 13, 5, 45, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("131045E NOV 26", Dtg.format(t))
        assertEquals("130545Z NOV 26", Dtg.format(t, zulu = true))
    }
}
