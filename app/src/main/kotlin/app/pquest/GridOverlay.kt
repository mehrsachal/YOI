package app.pquest

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import app.pquest.coords.Geodesy
import app.pquest.coords.LatLon
import app.pquest.coords.Mgrs
import app.pquest.coords.Utm
import app.pquest.coords.UtmMath
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the MGRS grid over the map. The line spacing follows the zoom (100 km, 10 km, 1 km, 100 m) and labels
 * carry the digits that matter at that spacing (e.g. two digits on a 1 km grid).
 */
fun DrawScope.drawMgrsGrid(map: MapLibreMap, night: Boolean) = drawMgrsGrid(drawContext.canvas.nativeCanvas, size.width, size.height, density, fontScale, map, night)

fun drawMgrsGrid(canvas: android.graphics.Canvas, w: Float, h: Float, density: Float, fontScale: Float, map: MapLibreMap, night: Boolean) {
    val proj = map.projection
    fun ll(x: Float, y: Float) = proj.fromScreenLocation(PointF(x, y)).let { LatLon(it.latitude, it.longitude) }
    fun px(p: LatLon) = proj.toScreenLocation(LatLng(p.lat, p.lon))

    val c = ll(w / 2, h / 2)
    val mpp = Geodesy.distance(c, ll(w / 2 + 100, h / 2)) / 100
    if (mpp <= 0 || mpp.isNaN()) return
    val interval = listOf(100, 1000, 10_000, 100_000).firstOrNull { it / mpp >= 70 * density } ?: return
    val corners = listOf(ll(0f, 0f), ll(w, 0f), ll(0f, h), ll(w, h))
    val south = max(-80.0, corners.minOf { it.lat }); val north = min(84.0, corners.maxOf { it.lat })
    val west = corners.minOf { it.lon }; val east = corners.maxOf { it.lon }
    if (south >= north || east - west > 18) return

    val ink = if (night) 0xFFFF5A4F.toInt() else 0xFF000000.toInt()
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ink; alpha = 150; strokeWidth = 1.2f * density }
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4 * density; color = if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        textSize = 14 * density * fontScale; typeface = Typeface.DEFAULT_BOLD
    }
    val text = Paint(halo).apply { style = Paint.Style.FILL; color = ink }
    fun label(s: String, x: Float, y: Float) { canvas.drawText(s, x, y, halo); canvas.drawText(s, x, y, text) }
    val digits = when (interval) { 100 -> 3; 1000 -> 2; else -> 1 }
    fun num(v: Double) = "%0${digits}d".format(java.util.Locale.ROOT, Math.floorMod(Math.round(v), 100_000L) / interval)

    for (zone in Utm.zoneOf(c.lat, west)..Utm.zoneOf(c.lat, east)) {
        val lo = max(west, Utm.centralMeridian(zone) - 3); val hi = min(east, Utm.centralMeridian(zone) + 3)
        if (lo >= hi) continue
        var eMin = Double.MAX_VALUE; var eMax = -Double.MAX_VALUE; var nMin = Double.MAX_VALUE; var nMax = -Double.MAX_VALUE
        for (lat in listOf(south, (south + north) / 2, north)) for (lon in listOf(lo, (lo + hi) / 2, hi)) {
            val (e, n) = UtmMath.forward(zone, lat, lon)
            eMin = min(eMin, e); eMax = max(eMax, e); nMin = min(nMin, n); nMax = max(nMax, n)
        }
        fun inZone(p: LatLon) = p.lon >= lo - 1e-9 && p.lon <= hi + 1e-9
        // Draws one grid line, sampled so that it follows the projection and stops at the zone edge.
        fun stroke(at: (Double) -> LatLon) {
            val path = Path(); var pen = false
            for (i in 0..24) {
                val p = at(i / 24.0)
                if (!inZone(p)) { pen = false; continue }
                val s = px(p)
                if (pen) path.lineTo(s.x, s.y) else path.moveTo(s.x, s.y)
                pen = true
            }
            canvas.drawPath(path, line)
        }
        val labelN = UtmMath.forward(zone, ll(w / 2, h - 30 * density).lat, (lo + hi) / 2).second
        val labelE = UtmMath.forward(zone, c.lat, ll(6 * density, h / 2).lon.coerceIn(lo, hi)).first
        var e = ceil(eMin / interval) * interval
        while (e <= eMax) {
            stroke { t -> UtmMath.inverse(zone, e, nMin + (nMax - nMin) * t) }
            val p = UtmMath.inverse(zone, e, labelN)
            if (interval < 100_000 && inZone(p)) label(num(e), px(p).x + 3 * density, h - 30 * density)
            e += interval
        }
        var n = ceil(nMin / interval) * interval
        while (n <= nMax) {
            stroke { t -> UtmMath.inverse(zone, eMin + (eMax - eMin) * t, n) }
            val p = UtmMath.inverse(zone, labelE, n)
            if (interval < 100_000 && inZone(p)) label(num(n), px(p).x + 4 * density, px(p).y - 4 * density)
            n += interval
        }
        if (interval == 100_000) { // name each 100 km square at its centre
            var ce = ceil(eMin / interval) * interval - interval / 2
            while (ce <= eMax + interval) {
                var cn = ceil(nMin / interval) * interval - interval / 2
                while (cn <= nMax + interval) {
                    val p = UtmMath.inverse(zone, ce, cn)
                    if (inZone(p) && p.lat in south..north) px(p).let { label(Mgrs.square(zone, ce, cn), it.x - 12 * density, it.y) }
                    cn += interval
                }
                ce += interval
            }
        }
    }
}
