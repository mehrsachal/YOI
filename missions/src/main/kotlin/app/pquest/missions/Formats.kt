package app.pquest.missions

import app.pquest.coords.CoordText
import app.pquest.coords.LatLon
import app.pquest.cot.Item
import app.pquest.cot.Kind
import app.pquest.cot.Sidc
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Import and export of overlays: KML/KMZ, GPX, GeoJSON, Shapefile and CSV. */
object Formats {
    class Ctx(val author: String, val now: Long, val mission: String = "")

    private fun item(c: Ctx, kind: Kind, name: String, pts: List<LatLon>, sidc: String? = null, remarks: String = "", color: String? = null, uid: String? = null) =
        Item(uid ?: UUID.randomUUID().toString(), c.mission, kind, sidc ?: if (kind == Kind.MARKER) Sidc.POINT_OF_INTEREST else "", name, remarks, pts,
            color ?: if (kind == Kind.MARKER) "#000000" else "#D4007A", c.now, c.author)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun dom(bytes: ByteArray): Element {
        val f = DocumentBuilderFactory.newInstance()
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    private fun Element.all(tag: String): List<Element> = getElementsByTagName(tag).let { n -> (0 until n.length).map { n.item(it) as Element } }
    private fun Element.text(tag: String): String? = all(tag).firstOrNull()?.textContent?.trim()

    /** Picks the parser from the file name. Throws IllegalArgumentException for unknown or unreadable files. */
    fun import(fileName: String, bytes: ByteArray, c: Ctx): List<Item> = try {
        when (fileName.substringAfterLast('.').lowercase()) {
            "kml" -> kml(bytes, c)
            "kmz", "zip" -> {
                val files = unzip(bytes)
                files.entries.firstOrNull { it.key.endsWith(".kml", true) }?.let { kml(it.value, c) }
                    ?: files.entries.firstOrNull { it.key.endsWith(".shp", true) }?.let { shp(it.value, c) }
                    ?: throw IllegalArgumentException("no KML or Shapefile inside")
            }
            "gpx" -> gpx(bytes, c)
            "geojson", "json" -> geoJson(String(bytes), c)
            "csv", "txt" -> csv(String(bytes), c)
            "shp" -> shp(bytes, c)
            else -> throw IllegalArgumentException("unknown file type")
        }
    } catch (e: IllegalArgumentException) { throw e } catch (e: Exception) { throw IllegalArgumentException(e.message ?: "unreadable file", e) }

    fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) { val e = z.nextEntry ?: break; if (!e.isDirectory && out.size < 2000) out[e.name] = z.readBytes() }
        }
        return out
    }

    // ---- KML ---------------------------------------------------------------------------------------------

    private fun coords(s: String) = s.trim().split(Regex("\\s+")).mapNotNull { t ->
        t.split(",").takeIf { it.size >= 2 }?.let { LatLon(it[1].toDouble(), it[0].toDouble()) }
    }

    fun kml(bytes: ByteArray, c: Ctx): List<Item> = dom(bytes).all("Placemark").flatMap { pm ->
        val name = pm.text("name") ?: ""
        val data = pm.all("Data").associate { it.getAttribute("name") to (it.text("value") ?: "") }
        val desc = data["remarks"] ?: pm.text("description") ?: ""
        val out = ArrayList<Item>()
        for (p in pm.all("Point")) coords(p.text("coordinates") ?: "").firstOrNull()?.let {
            out += item(c, Kind.MARKER, name, listOf(it), data["sidc"], desc, data["color"])
        }
        for (l in pm.all("LineString")) if (l.parentNode.nodeName != "outerBoundaryIs") coords(l.text("coordinates") ?: "").takeIf { it.size >= 2 }?.let {
            out += item(c, if (data["kind"] == "ROUTE") Kind.ROUTE else Kind.LINE, name, it, data["sidc"], desc, data["color"])
        }
        for (pg in pm.all("Polygon")) coords(pg.all("outerBoundaryIs").firstOrNull()?.text("coordinates") ?: "").takeIf { it.size >= 3 }?.let {
            out += item(c, Kind.AREA, name, if (it.first() == it.last()) it.dropLast(1) else it, data["sidc"], desc, data["color"])
        }
        out
    }

    private fun kmlColor(hex: String, alpha: String) = (alpha + hex.substring(5, 7) + hex.substring(3, 5) + hex.substring(1, 3)).lowercase()

    /** [iconFor] returns the icon file name (inside the KMZ "icons/" folder) for a marker, or null for a default pin. */
    fun toKml(name: String, items: List<Item>, iconFor: (Item) -> String? = { null }): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><name>${esc(name)}</name>")
        for (i in items.filter { !it.deleted && it.points.isNotEmpty() }) {
            val pts = i.points.joinToString(" ") { "${it.lon},${it.lat},0" }
            sb.append("<Placemark><name>${esc(i.name)}</name><description>${esc(i.remarks)}</description><Style>")
            if (i.kind == Kind.MARKER) iconFor(i)?.let { sb.append("<IconStyle><scale>1.4</scale><Icon><href>icons/$it</href></Icon></IconStyle>") }
            else sb.append("<LineStyle><color>${kmlColor(i.color, "ff")}</color><width>3</width></LineStyle><PolyStyle><color>${kmlColor(i.color, "33")}</color></PolyStyle>")
            sb.append("</Style><ExtendedData>")
            for ((k, v) in listOf("sidc" to i.sidc, "kind" to i.kind.name, "color" to i.color, "remarks" to i.remarks)) sb.append("<Data name=\"$k\"><value>${esc(v)}</value></Data>")
            sb.append("</ExtendedData>")
            sb.append(when (i.kind) {
                Kind.MARKER -> "<Point><coordinates>$pts</coordinates></Point>"
                Kind.AREA -> "<Polygon><outerBoundaryIs><LinearRing><coordinates>$pts ${i.points[0].lon},${i.points[0].lat},0</coordinates></LinearRing></outerBoundaryIs></Polygon>"
                else -> "<LineString><tessellate>1</tessellate><coordinates>$pts</coordinates></LineString>"
            }).append("</Placemark>")
        }
        return sb.append("</Document></kml>").toString()
    }

    fun toKmz(name: String, items: List<Item>, icons: Map<String, ByteArray>, iconFor: (Item) -> String?): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            z.putNextEntry(ZipEntry("doc.kml")); z.write(toKml(name, items, iconFor).toByteArray()); z.closeEntry()
            for ((n, b) in icons) { z.putNextEntry(ZipEntry("icons/$n")); z.write(b); z.closeEntry() }
        }
        return bo.toByteArray()
    }

    // ---- GPX ---------------------------------------------------------------------------------------------

    private fun Element.ll() = LatLon(getAttribute("lat").toDouble(), getAttribute("lon").toDouble())

    fun gpx(bytes: ByteArray, c: Ctx): List<Item> {
        val root = dom(bytes); val out = ArrayList<Item>()
        for (w in root.all("wpt")) out += item(c, Kind.MARKER, w.text("name") ?: "", listOf(w.ll()), remarks = w.text("desc") ?: "")
        for (r in root.all("rte")) r.all("rtept").map { it.ll() }.takeIf { it.size >= 2 }?.let { out += item(c, Kind.ROUTE, r.text("name") ?: "", it) }
        for (t in root.all("trk")) t.all("trkpt").map { it.ll() }.takeIf { it.size >= 2 }?.let { out += item(c, Kind.LINE, t.text("name") ?: "", it) }
        return out
    }

    fun toGpx(items: List<Item>): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"P-Quest\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        val live = items.filter { !it.deleted && it.points.isNotEmpty() }
        for (i in live.filter { it.kind == Kind.MARKER }) sb.append("<wpt lat=\"${i.points[0].lat}\" lon=\"${i.points[0].lon}\"><name>${esc(i.name)}</name><desc>${esc(i.remarks)}</desc></wpt>")
        for (i in live.filter { it.kind == Kind.ROUTE }) sb.append("<rte><name>${esc(i.name)}</name>" + i.points.joinToString("") { "<rtept lat=\"${it.lat}\" lon=\"${it.lon}\"/>" } + "</rte>")
        for (i in live.filter { it.kind == Kind.LINE || it.kind == Kind.AREA }) {
            val pts = if (i.kind == Kind.AREA) i.points + i.points[0] else i.points
            sb.append("<trk><name>${esc(i.name)}</name><trkseg>" + pts.joinToString("") { "<trkpt lat=\"${it.lat}\" lon=\"${it.lon}\"/>" } + "</trkseg></trk>")
        }
        return sb.append("</gpx>").toString()
    }

    // ---- GeoJSON -----------------------------------------------------------------------------------------

    private fun ring(a: JSONArray) = (0 until a.length()).map { a.getJSONArray(it).let { p -> LatLon(p.getDouble(1), p.getDouble(0)) } }

    fun geoJson(text: String, c: Ctx): List<Item> {
        val root = JSONObject(text); val out = ArrayList<Item>()
        val feats = when (root.optString("type")) {
            "FeatureCollection" -> root.getJSONArray("features").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            "Feature" -> listOf(root)
            else -> listOf(JSONObject().put("geometry", root))
        }
        for (f in feats) {
            val g = f.optJSONObject("geometry") ?: continue
            val p = f.optJSONObject("properties") ?: JSONObject()
            val name = p.optString("name", p.optString("title", "")); val sidc = p.optString("sidc", "").ifEmpty { null }
            val rem = p.optString("remarks", p.optString("description", "")); val col = p.optString("color", "").takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }
            val co = g.optJSONArray("coordinates") ?: continue
            fun area(r: List<LatLon>) = if (r.size > 1 && r.first() == r.last()) r.dropLast(1) else r
            when (g.getString("type")) {
                "Point" -> out += item(c, Kind.MARKER, name, listOf(LatLon(co.getDouble(1), co.getDouble(0))), sidc, rem, col)
                "MultiPoint" -> ring(co).forEach { out += item(c, Kind.MARKER, name, listOf(it), sidc, rem, col) }
                "LineString" -> out += item(c, if (p.optString("kind") == "ROUTE") Kind.ROUTE else Kind.LINE, name, ring(co), sidc, rem, col)
                "MultiLineString" -> (0 until co.length()).forEach { out += item(c, Kind.LINE, name, ring(co.getJSONArray(it)), sidc, rem, col) }
                "Polygon" -> out += item(c, Kind.AREA, name, area(ring(co.getJSONArray(0))), sidc, rem, col)
                "MultiPolygon" -> (0 until co.length()).forEach { out += item(c, Kind.AREA, name, area(ring(co.getJSONArray(it).getJSONArray(0))), sidc, rem, col) }
            }
        }
        return out
    }

    fun toGeoJson(items: List<Item>): String {
        val feats = JSONArray()
        for (i in items.filter { !it.deleted && it.points.isNotEmpty() }) {
            fun arr(p: List<LatLon>) = JSONArray(p.map { JSONArray(listOf(it.lon, it.lat)) })
            val geom = when (i.kind) {
                Kind.MARKER -> JSONObject().put("type", "Point").put("coordinates", JSONArray(listOf(i.points[0].lon, i.points[0].lat)))
                Kind.AREA -> JSONObject().put("type", "Polygon").put("coordinates", JSONArray(listOf(arr(i.points + i.points[0]))))
                else -> JSONObject().put("type", "LineString").put("coordinates", arr(i.points))
            }
            feats.put(JSONObject().put("type", "Feature").put("geometry", geom).put("properties",
                JSONObject().put("name", i.name).put("remarks", i.remarks).put("sidc", i.sidc).put("kind", i.kind.name).put("color", i.color)))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", feats).toString()
    }

    // ---- CSV ---------------------------------------------------------------------------------------------

    /** Needs a header row. Columns: name plus either lat and lon, or a grid column (mgrs / grid / coordinate). */
    fun csv(text: String, c: Ctx): List<Item> {
        val rows = text.lines().filter { it.isNotBlank() }.map { splitCsv(it) }
        require(rows.isNotEmpty()) { "empty file" }
        val head = rows[0].map { it.trim().lowercase() }
        fun col(vararg names: String) = head.indexOfFirst { it in names }
        val lat = col("lat", "latitude", "y"); val lon = col("lon", "lng", "long", "longitude", "x")
        val grid = col("mgrs", "grid", "coordinate", "coord", "utm"); val name = col("name", "title", "label", "id")
        val rem = col("remarks", "description", "desc", "notes"); val sidc = col("sidc")
        require((lat >= 0 && lon >= 0) || grid >= 0) { "CSV needs lat and lon columns, or a grid column" }
        return rows.drop(1).mapNotNull { r ->
            val p = if (grid >= 0) CoordText.parse(r.getOrElse(grid) { "" })
            else r.getOrNull(lat)?.trim()?.toDoubleOrNull()?.let { la -> r.getOrNull(lon)?.trim()?.toDoubleOrNull()?.let { LatLon(la, it) } }
            p?.takeIf { kotlin.math.abs(it.lat) <= 90 && kotlin.math.abs(it.lon) <= 180 }?.let {
                item(c, Kind.MARKER, r.getOrElse(name) { "" }.trim(), listOf(it), r.getOrNull(sidc)?.trim()?.takeIf(Sidc::isStandard), r.getOrElse(rem) { "" }.trim())
            }
        }
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                q && ch == '"' && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                ch == '"' -> q = !q
                ch == ',' && !q -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    // ---- Shapefile ---------------------------------------------------------------------------------------

    /** Reads point, polyline and polygon shapes from a .shp file. Coordinates must already be WGS84 lat/long. */
    fun shp(bytes: ByteArray, c: Ctx): List<Item> {
        val b = ByteBuffer.wrap(bytes)
        require(bytes.size >= 100 && b.getInt(0) == 9994) { "not a Shapefile" }
        val out = ArrayList<Item>(); var pos = 100; var n = 0
        while (pos + 8 <= bytes.size && out.size < 20_000) {
            val len = b.order(ByteOrder.BIG_ENDIAN).getInt(pos + 4) * 2
            val rec = pos + 8
            if (rec + len > bytes.size || len < 4) break
            b.order(ByteOrder.LITTLE_ENDIAN)
            val type = b.getInt(rec) % 10 // Z and M variants share the XY layout
            n++
            fun pt(at: Int) = LatLon(b.getDouble(at + 8), b.getDouble(at)).also { require(kotlin.math.abs(it.lat) <= 90 && kotlin.math.abs(it.lon) <= 180) { "Shapefile is not in WGS84 lat/long" } }
            when (type) {
                1 -> out += item(c, Kind.MARKER, "Point $n", listOf(pt(rec + 4)))
                8 -> for (i in 0 until b.getInt(rec + 36)) out += item(c, Kind.MARKER, "Point $n", listOf(pt(rec + 40 + i * 16)))
                3, 5 -> {
                    val parts = b.getInt(rec + 36); val total = b.getInt(rec + 40)
                    val starts = (0 until parts).map { b.getInt(rec + 44 + it * 4) } + total
                    val base = rec + 44 + parts * 4
                    for (p in 0 until parts) {
                        val pts = (starts[p] until starts[p + 1]).map { pt(base + it * 16) }
                        if (type == 3 && pts.size >= 2) out += item(c, Kind.LINE, "Line $n", pts)
                        if (type == 5 && pts.size >= 4) out += item(c, Kind.AREA, "Area $n", pts.dropLast(1))
                    }
                }
            }
            pos = rec + len
        }
        return out
    }
}
