package app.pquest.coords

import kotlin.math.*

data class LatLon(val lat: Double, val lon: Double)

/** UTM position. [northing] uses the standard false northing (10 000 000 m) when [north] is false. */
data class Utm(val zone: Int, val north: Boolean, val easting: Double, val northing: Double) {
    fun toLatLon(): LatLon = UtmMath.inverse(zone, easting, if (north) northing else northing - 1e7)

    companion object {
        fun from(p: LatLon, zone: Int = zoneOf(p.lat, p.lon)): Utm {
            val (e, n) = UtmMath.forward(zone, p.lat, p.lon)
            return Utm(zone, p.lat >= 0, e, if (p.lat >= 0) n else n + 1e7)
        }

        /** Standard zone number including the Norway and Svalbard exceptions. */
        fun zoneOf(lat: Double, lon: Double): Int {
            val l = ((lon + 180) % 360 + 360) % 360 - 180
            if (lat >= 56 && lat < 64 && l >= 3 && l < 12) return 32
            if (lat >= 72 && lat < 84) when {
                l >= 0 && l < 9 -> return 31
                l >= 9 && l < 21 -> return 33
                l >= 21 && l < 33 -> return 35
                l >= 33 && l < 42 -> return 37
            }
            return min(60, floor((l + 180) / 6).toInt() + 1)
        }

        fun centralMeridian(zone: Int) = zone * 6.0 - 183.0
    }
}

/** Transverse Mercator on WGS84 using the Krueger n-series (sub-millimetre inside a zone). */
object UtmMath {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private val n = F / (2 - F)
    private val aa = A / (1 + n) * (1 + n * n / 4 + n.pow(4) / 64)
    private val n2 = n * n
    private val n3 = n2 * n
    private val n4 = n3 * n
    private val al = doubleArrayOf(n / 2 - 2 * n2 / 3 + 5 * n3 / 16 + 41 * n4 / 180, 13 * n2 / 48 - 3 * n3 / 5 + 557 * n4 / 1440,
        61 * n3 / 240 - 103 * n4 / 140, 49561 * n4 / 161280)
    private val be = doubleArrayOf(n / 2 - 2 * n2 / 3 + 37 * n3 / 96 - n4 / 360, n2 / 48 + n3 / 15 - 437 * n4 / 1440,
        17 * n3 / 480 - 37 * n4 / 840, 4397 * n4 / 161280)
    private val de = doubleArrayOf(2 * n - 2 * n2 / 3 - 2 * n3 + 116 * n4 / 45, 7 * n2 / 3 - 8 * n3 / 5 - 227 * n4 / 45,
        56 * n3 / 15 - 136 * n4 / 35, 4279 * n4 / 630)

    /** Returns easting and northing; northing is negative south of the equator (no false northing). */
    fun forward(zone: Int, lat: Double, lon: Double): Pair<Double, Double> {
        val phi = Math.toRadians(lat)
        var dl = Math.toRadians(lon - Utm.centralMeridian(zone))
        if (dl > PI) dl -= 2 * PI else if (dl < -PI) dl += 2 * PI
        val c = 2 * sqrt(n) / (1 + n)
        val t = sinh(atanh(sin(phi)) - c * atanh(c * sin(phi)))
        val xi = atan2(t, cos(dl))
        val eta = atanh(sin(dl) / sqrt(1 + t * t))
        var e = eta
        var nn = xi
        for (j in 1..4) {
            e += al[j - 1] * cos(2 * j * xi) * sinh(2 * j * eta)
            nn += al[j - 1] * sin(2 * j * xi) * cosh(2 * j * eta)
        }
        return Pair(500000 + K0 * aa * e, K0 * aa * nn)
    }

    /** [northing] without false northing (negative in the southern hemisphere). */
    fun inverse(zone: Int, easting: Double, northing: Double): LatLon {
        val xi = northing / (K0 * aa)
        val eta = (easting - 500000) / (K0 * aa)
        var xi1 = xi
        var eta1 = eta
        for (j in 1..4) {
            xi1 -= be[j - 1] * sin(2 * j * xi) * cosh(2 * j * eta)
            eta1 -= be[j - 1] * cos(2 * j * xi) * sinh(2 * j * eta)
        }
        val chi = asin(sin(xi1) / cosh(eta1))
        var phi = chi
        for (j in 1..4) phi += de[j - 1] * sin(2 * j * chi)
        val lon = Utm.centralMeridian(zone) + Math.toDegrees(atan2(sinh(eta1), cos(xi1)))
        return LatLon(Math.toDegrees(phi), lon)
    }
}

/** Military Grid Reference System (UTM area only, 80S to 84N; polar UPS is not supported). */
object Mgrs {
    private const val BANDS = "CDEFGHJKLMNPQRSTUVWX"
    private val COLS = arrayOf("STUVWXYZ", "ABCDEFGH", "JKLMNPQR")
    private const val ROWS = "ABCDEFGHJKLMNPQRSTUV"
    private val RE = Regex("^(\\d{1,2})([C-HJ-NP-X])([A-HJ-NP-Z])([A-HJ-NP-V])(\\d*)$")

    fun band(lat: Double): Char = BANDS[min(19, floor((lat + 80) / 8).toInt()).coerceAtLeast(0)]

    fun square(zone: Int, easting: Double, northingNoFalse: Double): String {
        val col = COLS[zone % 3][(floor(easting / 100000).toInt() - 1).coerceIn(0, 7)]
        val r = Math.floorMod(floor(northingNoFalse / 100000).toInt() + if (zone % 2 == 0) 5 else 0, 20)
        return "$col${ROWS[r]}"
    }

    /** [digits] per axis: 5 = 1 m, 4 = 10 m ... 0 = 100 km square only. Values are truncated, per the standard. */
    fun format(p: LatLon, digits: Int = 5, spaced: Boolean = true): String {
        if (p.lat < -80 || p.lat > 84) return "—"
        val zone = Utm.zoneOf(p.lat, p.lon)
        val (e, n) = UtmMath.forward(zone, p.lat, p.lon)
        val div = 10.0.pow(5 - digits)
        val ee = floor(Math.floorMod(floor(e).toLong(), 100000L) / div).toLong()
        val nn = floor(Math.floorMod(floor(n).toLong(), 100000L) / div).toLong()
        val sep = if (spaced) " " else ""
        val gz = "%02d%c".format(java.util.Locale.ROOT, zone, band(p.lat))
        if (digits == 0) return "$gz$sep${square(zone, e, n)}"
        return "$gz$sep${square(zone, e, n)}$sep${"%0${digits}d".format(java.util.Locale.ROOT, ee)}$sep${"%0${digits}d".format(java.util.Locale.ROOT, nn)}"
    }

    /** Parses a grid reference and returns the south-west corner of the referenced cell, or null. */
    fun parse(text: String): LatLon? {
        val m = RE.matchEntire(text.uppercase().filter { it.isLetterOrDigit() }) ?: return null
        val zone = m.groupValues[1].toInt()
        val bandCh = m.groupValues[2][0]
        val digits = m.groupValues[5]
        if (zone !in 1..60 || digits.length % 2 != 0 || digits.length > 10) return null
        val colIdx = COLS[zone % 3].indexOf(m.groupValues[3][0])
        if (colIdx < 0) return null
        val half = digits.length / 2
        val scale = 10.0.pow(5 - half)
        val e = (colIdx + 1) * 100000 + (if (half == 0) 0.0 else digits.take(half).toDouble() * scale)
        val rowIdx = Math.floorMod(ROWS.indexOf(m.groupValues[4][0]) - if (zone % 2 == 0) 5 else 0, 20)
        val nBase = rowIdx * 100000 + (if (half == 0) 0.0 else digits.drop(half).toDouble() * scale)
        // The row letter repeats every 2000 km: choose the repeat whose latitude best fits the band.
        val south = -80.0 + 8 * BANDS.indexOf(bandCh)
        val north = if (bandCh == 'X') 84.0 else south + 8
        var best: LatLon? = null
        var bestErr = Double.MAX_VALUE
        for (k in -5..4) {
            val ll = UtmMath.inverse(zone, e, nBase + k * 2e6)
            if (ll.lat.isNaN()) continue
            val err = max(0.0, max(south - ll.lat, ll.lat - north))
            if (err < bestErr) { bestErr = err; best = ll }
        }
        return if (bestErr < 1.0) best else null
    }
}

object Geodesy {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val B = A * (1 - F)

    /** Vincenty inverse on WGS84. Returns distance in metres and initial true bearing in degrees. */
    fun inverse(p1: LatLon, p2: LatLon): Pair<Double, Double> {
        if (p1 == p2) return Pair(0.0, 0.0)
        val u1 = atan((1 - F) * tan(Math.toRadians(p1.lat)))
        val u2 = atan((1 - F) * tan(Math.toRadians(p2.lat)))
        val l = Math.toRadians(p2.lon - p1.lon)
        var lam = l
        var sinS = 0.0; var cosS = 0.0; var sig = 0.0; var cos2A = 0.0; var cos2Sm = 0.0
        for (i in 0 until 100) {
            val sl = sin(lam); val cl = cos(lam)
            sinS = hypot(cos(u2) * sl, cos(u1) * sin(u2) - sin(u1) * cos(u2) * cl)
            if (sinS == 0.0) return Pair(0.0, 0.0)
            cosS = sin(u1) * sin(u2) + cos(u1) * cos(u2) * cl
            sig = atan2(sinS, cosS)
            val sinA = cos(u1) * cos(u2) * sl / sinS
            cos2A = 1 - sinA * sinA
            cos2Sm = if (cos2A == 0.0) 0.0 else cosS - 2 * sin(u1) * sin(u2) / cos2A
            val c = F / 16 * cos2A * (4 + F * (4 - 3 * cos2A))
            val prev = lam
            lam = l + (1 - c) * F * sinA * (sig + c * sinS * (cos2Sm + c * cosS * (-1 + 2 * cos2Sm * cos2Sm)))
            if (abs(lam - prev) < 1e-12) break
        }
        val uu = cos2A * (A * A - B * B) / (B * B)
        val ka = 1 + uu / 16384 * (4096 + uu * (-768 + uu * (320 - 175 * uu)))
        val kb = uu / 1024 * (256 + uu * (-128 + uu * (74 - 47 * uu)))
        val dS = kb * sinS * (cos2Sm + kb / 4 * (cosS * (-1 + 2 * cos2Sm * cos2Sm) -
                kb / 6 * cos2Sm * (-3 + 4 * sinS * sinS) * (-3 + 4 * cos2Sm * cos2Sm)))
        val brg = atan2(cos(u2) * sin(lam), cos(u1) * sin(u2) - sin(u1) * cos(u2) * cos(lam))
        return Pair(B * ka * (sig - dS), (Math.toDegrees(brg) + 360) % 360)
    }

    fun distance(p1: LatLon, p2: LatLon) = inverse(p1, p2).first

    fun pathLength(pts: List<LatLon>) = pts.zipWithNext().sumOf { (a, b) -> distance(a, b) }

    /** Evenly spaced samples along a path (linear in lat/lon within each leg), with distance from start. */
    fun sample(pts: List<LatLon>, count: Int): List<Pair<Double, LatLon>> {
        if (pts.size < 2) return pts.map { Pair(0.0, it) }
        val legs = pts.zipWithNext().map { (a, b) -> distance(a, b) }
        val total = legs.sum()
        if (total == 0.0) return listOf(Pair(0.0, pts[0]))
        val out = ArrayList<Pair<Double, LatLon>>()
        var leg = 0; var before = 0.0
        for (i in 0 until count) {
            val d = total * i / (count - 1)
            while (leg < legs.size - 1 && d > before + legs[leg]) { before += legs[leg]; leg++ }
            val f = if (legs[leg] == 0.0) 0.0 else ((d - before) / legs[leg]).coerceIn(0.0, 1.0)
            val a = pts[leg]; val b = pts[leg + 1]
            out.add(Pair(d, LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)))
        }
        return out
    }
}

enum class CoordFormat { MGRS, UTM, DD, DMS }

object CoordText {
    fun format(p: LatLon, f: CoordFormat): String = when (f) {
        CoordFormat.MGRS -> Mgrs.format(p)
        CoordFormat.UTM -> if (p.lat < -80 || p.lat > 84) "—" else Utm.from(p).let {
            "%02d%c %06d %07d".format(java.util.Locale.ROOT, it.zone, Mgrs.band(p.lat), floor(it.easting).toLong(), floor(it.northing).toLong())
        }
        CoordFormat.DD -> "%.5f%c %.5f%c".format(java.util.Locale.ROOT, abs(p.lat), if (p.lat >= 0) 'N' else 'S', abs(p.lon), if (p.lon >= 0) 'E' else 'W')
        CoordFormat.DMS -> dms(p.lat, 'N', 'S') + " " + dms(p.lon, 'E', 'W')
    }

    private fun dms(v: Double, pos: Char, neg: Char): String {
        val t = (abs(v) * 36000).roundToLong()
        return "%d°%02d'%04.1f\"%c".format(java.util.Locale.ROOT, t / 36000, t / 600 % 60, t % 600 / 10.0, if (v >= 0) pos else neg)
    }

    private val UTM_RE = Regex("^(\\d{1,2})\\s*([C-HJ-NP-X])\\s+(\\d{6}(?:\\.\\d+)?)\\s+(\\d{1,8}(?:\\.\\d+)?)$")

    /** Accepts MGRS, UTM ("43S 319050 3728873"), decimal degrees, or degrees-minutes(-seconds) with optional N/S/E/W. */
    fun parse(text: String): LatLon? {
        val s = text.trim().uppercase()
        if (s.isEmpty()) return null
        UTM_RE.matchEntire(s)?.let { m ->
            val zone = m.groupValues[1].toInt()
            if (zone !in 1..60) return null
            val north = m.groupValues[2][0] >= 'N'
            return Utm(zone, north, m.groupValues[3].toDouble(), m.groupValues[4].toDouble()).toLatLon()
        }
        Mgrs.parse(s)?.let { return it }
        val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(s).map { it.value.toDouble() }.toList()
        val per = nums.size / 2
        if (nums.size % 2 != 0 || per !in 1..3 || s.any { it.isLetter() && it !in "NSEW" }) return null
        fun value(part: List<Double>): Double {
            val sign = if (part[0] < 0) -1 else 1
            return sign * (abs(part[0]) + part.getOrElse(1) { 0.0 } / 60 + part.getOrElse(2) { 0.0 } / 3600)
        }
        var lat = value(nums.take(per))
        var lon = value(nums.drop(per))
        if ('S' in s) lat = -abs(lat)
        if ('W' in s) lon = -abs(lon)
        return if (abs(lat) <= 90 && abs(lon) <= 180) LatLon(lat, lon) else null
    }
}

/** Military date-time group, e.g. 131045E NOV 26. PKT (UTC+5) is zone letter E; Zulu is Z. */
object Dtg {
    private val MON = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    fun format(epochMs: Long, zulu: Boolean = false): String {
        val t = java.time.Instant.ofEpochMilli(epochMs).atOffset(java.time.ZoneOffset.ofHours(if (zulu) 0 else 5))
        return "%02d%02d%02d%c %s %02d".format(java.util.Locale.ROOT, t.dayOfMonth, t.hour, t.minute, if (zulu) 'Z' else 'E', MON[t.monthValue - 1], t.year % 100)
    }
}
