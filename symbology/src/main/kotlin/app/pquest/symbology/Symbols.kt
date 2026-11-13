package app.pquest.symbology

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import app.pquest.coords.LatLon
import app.pquest.cot.Kind
import app.pquest.cot.Sidc
import armyc2.c5isr.renderer.MilStdIconRenderer
import armyc2.c5isr.renderer.utilities.MSLookup
import armyc2.c5isr.renderer.utilities.MilStdAttributes
import armyc2.c5isr.renderer.utilities.Modifiers
import armyc2.c5isr.renderer.utilities.SymbolID
import armyc2.c5isr.web.render.WebRenderer

enum class Tab { FRIENDLY, ENEMY, OBSTACLES, POINTS, CONTROL }

/** One choice in the picker. [points] is the number of taps needed (1 = marker, 2+ = drawn graphic). */
data class Entry(val name: String, val sidc: String, val kind: Kind, val points: Int = 1)

/** The simplified picker: the symbols a section or platoon commander uses most, in five tabs. */
object Catalog {
    private fun unit(aff: Char, name: String, entity: String) = Entry(name, Sidc.make(aff, "10", entity), Kind.MARKER)
    private fun cm(name: String, entity: String, kind: Kind = Kind.MARKER, pts: Int = 1) = Entry(name, Sidc.make('3', "25", entity), kind, pts)

    val ROUTE = Entry("Route", "", Kind.ROUTE, 2)

    val tabs: Map<Tab, List<Entry>> = mapOf(
        Tab.FRIENDLY to listOf(
            "Infantry" to "121100", "Armour" to "120500", "Recce" to "121300", "Artillery" to "130300", "Mortar" to "130800",
            "Engineer" to "140700", "Signals" to "111000", "Medical" to "161300", "Supply" to "160000", "Command post" to "110000",
        ).map { unit('3', it.first, it.second) },
        Tab.ENEMY to listOf(
            "Infantry" to "121100", "Armour" to "120500", "Recce" to "121300", "Artillery" to "130300", "Mortar" to "130800",
            "Air defence" to "130100", "Anti-tank" to "120400", "Sniper" to "121500", "Observer" to "121200", "Unknown unit" to "000000",
        ).map { unit('6', it.first, it.second) },
        Tab.OBSTACLES to listOf(
            cm("Minefield", "270701"), cm("AP mine", "280200"), cm("AT mine", "280300"), cm("Mine (unspecified)", "280600"), cm("Booby trap", "280700"),
            cm("Wire", "290301", Kind.LINE, 2), cm("Obstacle line", "290100", Kind.LINE, 2), cm("AT ditch / obstacle", "290202", Kind.LINE, 2),
            cm("Obstacle belt", "270100", Kind.AREA, 3),
        ),
        Tab.POINTS to listOf(
            cm("Checkpoint", "130300"), cm("Rally point", "131400"), cm("Start point", "131600"), cm("Release point", "131500"),
            cm("Contact point", "130500"), cm("Observation post", "160100"), cm("Target ref point", "160300"),
            cm("Casualty collection", "320500"), cm("Ammo point", "320200"), cm("Point of interest", "131300"),
        ),
        Tab.CONTROL to listOf(
            cm("Phase line", "140300", Kind.LINE, 2), cm("Boundary", "110100", Kind.LINE, 2), cm("Line of departure", "141000", Kind.LINE, 2),
            cm("Forward line (FLOT)", "140100", Kind.LINE, 2), cm("Axis of advance", "151403", Kind.LINE, 3),
            cm("Objective", "151700", Kind.AREA, 3), cm("Assembly area", "150200", Kind.AREA, 3), cm("Battle position", "151200", Kind.AREA, 3),
            cm("Area (NAI)", "120200", Kind.AREA, 3), ROUTE,
        ),
    )

    val ECHELONS = listOf("00" to "—", "11" to "Team", "12" to "Section", "14" to "Platoon", "15" to "Company", "16" to "Battalion", "18" to "Brigade", "21" to "Division")
    val AFFILIATIONS = listOf('3' to "Friendly", '6' to "Enemy", '4' to "Neutral", '1' to "Unknown")
}

/** Thin wrapper over mil-sym-android (MIL-STD-2525D/E, APP-6D). */
object Symbols {
    @Volatile private var ready = false

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        MilStdIconRenderer.getInstance().init(ctx.applicationContext)
        ready = true
    }

    /**
     * Renders a point symbol, with its unit name and echelon, centred in the returned bitmap so that the bitmap
     * centre sits on the map position. Returns null if the code cannot be drawn.
     */
    fun icon(sidc: String, name: String, sizePx: Int): Bitmap? = runCatching {
        if (!ready || !Sidc.isStandard(sidc)) return null
        val mods = HashMap<String, String>()
        if (name.isNotBlank()) mods[Modifiers.T_UNIQUE_DESIGNATION_1] = name
        val attrs = hashMapOf(MilStdAttributes.PixelSize to sizePx.toString(), MilStdAttributes.KeepUnitRatio to "true")
        val info = MilStdIconRenderer.getInstance().RenderIcon(sidc, mods, attrs) ?: return null
        val src = info.image ?: return null
        val c = info.centerPoint
        val halfW = maxOf(c.x, src.width - c.x); val halfH = maxOf(c.y, src.height - c.y)
        val out = Bitmap.createBitmap(halfW * 2, halfH * 2, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, (halfW - c.x).toFloat(), (halfH - c.y).toFloat(), null)
        out
    }.getOrNull()

    /** A unit-defined sign with no standard symbol: a labelled badge (e.g. "PQT" for picquet). */
    fun badge(label: String, name: String, sizePx: Int, color: Int = Color.BLACK): Bitmap {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sizePx * 0.42f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; this.color = color }
        val small = Paint(text).apply { textSize = sizePx * 0.36f }
        val w = maxOf(sizePx.toFloat(), text.measureText(label) + sizePx * 0.4f, small.measureText(name) + 8f)
        val h = sizePx * 0.8f
        val full = if (name.isBlank()) h else h + small.textSize * 2.6f
        val bmp = Bitmap.createBitmap(w.toInt() + 4, (full * 2 - h).toInt() + 4, Bitmap.Config.ARGB_8888) // keeps the badge at the bitmap centre
        val cv = Canvas(bmp); val cx = bmp.width / 2f; val cy = bmp.height / 2f
        val box = RectF(cx - sizePx / 2f, cy - h / 2, cx + sizePx / 2f, cy + h / 2).apply { inset(-(w - sizePx) / 2 + 2, 0f) }
        cv.drawRoundRect(box, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        cv.drawRoundRect(box, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; this.color = color })
        cv.drawText(label, cx, cy + text.textSize * 0.36f, text)
        if (name.isNotBlank()) {
            val halo = Paint(small).apply { style = Paint.Style.STROKE; strokeWidth = 5f; this.color = Color.WHITE }
            cv.drawText(name, cx, box.bottom + small.textSize * 1.1f, halo); cv.drawText(name, cx, box.bottom + small.textSize * 1.1f, small)
        }
        return bmp
    }

    /** Text as a small haloed image, used to label lines and areas without any font server. */
    fun label(text: String, sizePx: Float, color: Int): Bitmap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sizePx; typeface = Typeface.DEFAULT_BOLD; this.color = color }
        val halo = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = sizePx / 4; this.color = Color.WHITE }
        val bmp = Bitmap.createBitmap(p.measureText(text).toInt() + 12, (sizePx * 1.5f).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawText(text, 6f, sizePx * 1.1f, halo); drawText(text, 6f, sizePx * 1.1f, p) }
        return bmp
    }

    /**
     * Draws a multi-point tactical graphic (phase line, boundary, axis of advance, objective ...) to GeoJSON for the
     * visible area. Returns null when the renderer cannot draw it, in which case the caller draws a plain line or area.
     */
    fun graphic(uid: String, sidc: String, name: String, pts: List<LatLon>, west: Double, south: Double, east: Double, north: Double, scale: Double, lineColor: String? = null): String? =
        runCatching {
            if (!ready || !Sidc.isStandard(sidc) || pts.size < 2) return null
            val mods = HashMap<String, String>()
            if (name.isNotBlank()) { mods[Modifiers.T_UNIQUE_DESIGNATION_1] = name; mods[Modifiers.T1_UNIQUE_DESIGNATION_2] = name }
            val out = WebRenderer.RenderSymbol(
                uid, name, "", sidc, pts.joinToString(" ") { "${it.lon},${it.lat}" }, "clampToGround", scale,
                "$west,$south,$east,$north", mods, HashMap<String, String>().apply { lineColor?.let { put(MilStdAttributes.LineColor, it.removePrefix("#")) } }, WebRenderer.OUTPUT_FORMAT_GEOJSON,
            )
            out?.takeIf { it.contains("\"features\"") && !it.contains("\"type\":\"error\"") }
        }.getOrNull()

    /** Searches the full 2525D symbol set by name. */
    fun search(query: String, affiliation: Char = '3', limit: Int = 60): List<Entry> = runCatching {
        if (!ready) return emptyList()
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val look = MSLookup.getInstance()
        val out = ArrayList<Entry>()
        for (id in look.getIDList(SymbolID.Version_2525Dch1)) {
            val info = look.getMSLInfo(id, SymbolID.Version_2525Dch1) ?: continue
            val name = info.name ?: continue
            if (!name.lowercase().contains(q) && info.path?.lowercase()?.contains(q) != true) continue
            val geom = info.geometry?.lowercase() ?: ""
            val set = "%02d".format(info.symbolSet); val entity = "%06d".format(info.entityCode)
            val control = info.symbolSet == SymbolID.SymbolSet_ControlMeasure
            if (control && geom.isEmpty()) continue // a heading, not a drawable symbol
            val kind = when (geom) { "line" -> Kind.LINE; "area" -> Kind.AREA; else -> Kind.MARKER }
            out += Entry(name, Sidc.make(if (control) '3' else affiliation, set, entity), kind, when (kind) { Kind.MARKER -> 1; Kind.AREA -> maxOf(3, info.minPointCount); else -> maxOf(2, info.minPointCount) })
            if (out.size >= limit) break
        }
        out
    }.getOrDefault(emptyList())
}
