package app.pquest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.pquest.coords.CoordFormat
import app.pquest.coords.CoordText
import app.pquest.coords.Geodesy
import app.pquest.coords.LatLon
import app.pquest.coords.North
import app.pquest.cot.Contact
import app.pquest.cot.CotEvent
import app.pquest.cot.Item
import app.pquest.cot.Kind
import app.pquest.cot.RoomKind
import app.pquest.cot.Sidc
import app.pquest.data.CustomSymbol
import app.pquest.map.BBox
import app.pquest.map.MapHub
import app.pquest.map.StyleBuilder
import app.pquest.symbology.Entry
import app.pquest.symbology.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Sheet { NONE, LAYERS, GOTO, MAPS, DOWNLOAD, TRACKS, SETTINGS, PROFILE, PICKER, ITEM, CHAT, MISSIONS, MENU, MESH, SERVER, COMPASS, SHARE, HELP, SECURITY, ADMIN, FEEDBACK }
enum class Mode { BROWSE, DRAW, PLACE, MOVE, BOX_FIRST, BOX_SECOND }

/** Everything the single map screen needs to remember. */
class Ui {
    var sheet by mutableStateOf(Sheet.NONE)
    var mode by mutableStateOf(Mode.BROWSE)
    var map by mutableStateOf<MapLibreMap?>(null)
    var cameraTick by mutableIntStateOf(0)
    var idleTick by mutableIntStateOf(0)
    var zoom by mutableFloatStateOf(0f)
    var centre by mutableStateOf(LatLon(0.0, 0.0))
    var fix by mutableStateOf<LatLon?>(null)
    var hasLocation by mutableStateOf(false)
    var target by mutableStateOf<LatLon?>(null)
    /** Points tapped so far in DRAW mode (also the measure tool and the profile / line-of-sight input). */
    val measure: SnapshotStateList<LatLon> = mutableStateListOf()
    var drawing by mutableStateOf<Entry?>(null)
    var placing by mutableStateOf<Entry?>(null)
    var placingCustom by mutableStateOf<CustomSymbol?>(null)
    var editing by mutableStateOf<String?>(null)
    var activeMission by mutableStateOf("")
    var follow by mutableStateOf<String?>(null)
    var followIndex by mutableIntStateOf(0)
    var room by mutableStateOf<Triple<RoomKind, String, String>?>(null)
    var shownTrack by mutableStateOf<Long?>(null)
    var boxCorner by mutableStateOf<LatLon?>(null)
    var box by mutableStateOf<BBox?>(null)
    var dataTick by mutableIntStateOf(0)
    var styleTick by mutableIntStateOf(0)
    var progress by mutableStateOf<MapHub.Progress?>(null)
    var downloadJob by mutableStateOf<Job?>(null)
    var message by mutableStateOf<String?>(null)
    var admin by mutableStateOf(false)
    val images = HashSet<String>()
    var imagesStyle = -1
    val tour = TourState()
    /** Screen positions of the controls the tour points at. */
    val targets = mutableStateMapOf<String, Rect>()
    var view: MapView? = null

    fun flyTo(p: LatLon, minZoom: Double = 14.0) {
        val m = map ?: return
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), max(m.cameraPosition.zoom, minZoom)))
    }

    fun fit(pts: List<LatLon>) {
        val m = map ?: return
        if (pts.isEmpty()) return
        if (pts.distinct().size < 2) return flyTo(pts[0])
        val b = LatLngBounds.Builder().includes(pts.map { LatLng(it.lat, it.lon) }).build()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 120))
    }

    fun stopDrawing() { mode = Mode.BROWSE; measure.clear(); drawing = null; placing = null; placingCustom = null }
}

fun Modifier.tourTarget(ui: Ui, key: String) = onGloballyPositioned { ui.targets[key] = it.boundsInRoot() }

@Composable
fun distanceText(m: Double): String =
    if (m < 1000) stringResource(R.string.metres, m.roundToInt())
    else stringResource(R.string.kilometres, "%.2f".format(Locale.ROOT, m / 1000))

/** A true bearing shown in the user's north reference (grid, magnetic or true) and unit (degrees or mils). */
fun bearingText(app: App, trueDeg: Double, at: LatLon): String {
    val s = app.settings
    val decl = GeomagneticField(at.lat.toFloat(), at.lon.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()
    val b = North.fromTrue(trueDeg, s.north, at, decl)
    val tag = s.north.take(1)
    return if (s.mils) "%04d mils %s".format(Locale.ROOT, North.mils(b), tag) else "%03d° %s".format(Locale.ROOT, b.roundToInt() % 360, tag)
}

fun newItem(app: App, ui: Ui, kind: Kind, sidc: String, pts: List<LatLon>, name: String = ""): Item {
    val s = app.session!!
    val mission = s.repo.missions().firstOrNull { it.id == ui.activeMission && it.role.canEdit }?.id ?: ""
    return Item(UUID.randomUUID().toString(), mission, kind, sidc, name, "", pts, "#000000", System.currentTimeMillis(), s.repo.self.uid)
}

@Composable
fun MapScreen(app: App, activity: ComponentActivity, session: Session) {
    val ui = remember { Ui() }
    val s = app.settings
    val packs by app.hub.packs.collectAsState()
    val recording by TrackService.recording.collectAsState()
    val trackTick by session.tracks.changes.collectAsState()
    val repoTick by session.repo.tick.collectAsState()
    val contacts by session.engine.contacts.collectAsState()
    val alerts by session.engine.alerts.collectAsState()
    val serviceFix by TrackService.fix.collectAsState()
    val demo = !s.tourDone || ui.tour.replay

    LaunchedEffect(demo) { session.engine.demo = demo }
    LaunchedEffect(serviceFix) { serviceFix?.let { ui.fix = LatLon(it.latitude, it.longitude) } }

    val fine = Manifest.permission.ACCESS_FINE_LOCATION
    LaunchedEffect(Unit) { ui.hasLocation = activity.checkSelfPermission(fine) == PackageManager.PERMISSION_GRANTED }
    var afterGrant by remember { mutableStateOf({}) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        ui.hasLocation = r[fine] == true
        if (ui.hasLocation) afterGrant()
    }
    fun withLocation(action: () -> Unit) {
        if (ui.hasLocation) return action()
        afterGrant = action
        val wanted = mutableListOf(fine, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        askLocation.launch(wanted.toTypedArray())
    }
    LocationFeed(activity, ui)
    // Position reports need fixes with the screen off, so the foreground service runs while we may transmit.
    LaunchedEffect(ui.hasLocation, s.dark, demo, repoTick) {
        val wanted = ui.hasLocation && !s.dark && !demo && !session.duress && (session.repo.server != null || session.repo.missions().isNotEmpty())
        TrackService.reportPosition(app, wanted)
    }

    // Rebuild the style whenever what is drawn changes; everything is served from the device.
    LaunchedEffect(ui.map, s.base, s.hillshade, s.night, s.slope, s.contours, packs, ui.dataTick, s.online, s.dark, s.urlTopo, s.urlSatellite, s.urlDem) {
        val m = ui.map ?: return@LaunchedEffect
        app.applyOnline()
        val json = withContext(Dispatchers.IO) { app.hub.style(s.base.takeIf { it != "none" }, s.hillshade, s.night, s.slope, s.contours) }
        m.setStyle(Style.Builder().fromJson(json)) { ui.styleTick++ }
    }
    // Own position, target, drawn points and track.
    val trackId = ui.shownTrack ?: recording
    LaunchedEffect(ui.styleTick, ui.fix, ui.target, ui.measure.toList(), trackId, trackTick, alerts) {
        val style = ui.map?.style ?: return@LaunchedEffect
        val track = if (trackId == null) emptyList() else withContext(Dispatchers.IO) { session.tracks.points(trackId) }
        style.getSourceAs<GeoJsonSource>("track")?.setGeoJson(StyleBuilder.line(if (track.size < 2) emptyList() else track))
        style.getSourceAs<GeoJsonSource>("measure")?.setGeoJson(StyleBuilder.line(ui.measure))
        style.getSourceAs<GeoJsonSource>("marks")?.setGeoJson(StyleBuilder.marks(
            alerts.map { it.pos to "#ff0000" } + listOfNotNull(ui.target?.let { it to "#e00000" }, ui.fix?.let { it to "#0a66ff" })
        ))
    }
    // Symbols, tactical graphics and other units' positions.
    LaunchedEffect(ui.styleTick, repoTick, ui.idleTick, contacts) {
        val m = ui.map ?: return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        if (ui.imagesStyle != ui.styleTick) { ui.images.clear(); ui.imagesStyle = ui.styleTick }
        val box = viewBox(m) ?: return@LaunchedEffect
        val density = activity.resources.displayMetrics.density
        val scale = Geodesy.distance(LatLon(ui.centre.lat, box.west), LatLon(ui.centre.lat, box.east)) / (m.width / density) * 3779.5
        val known = HashSet(ui.images)
        val r = withContext(Dispatchers.Default) { renderOverlay(session, box, scale, density, known, contacts.values) }
        for ((k, b) in r.images) { style.addImage(k, b); ui.images += k }
        style.getSourceAs<GeoJsonSource>("gfx")?.setGeoJson(r.gfx)
        style.getSourceAs<GeoJsonSource>("labels")?.setGeoJson(r.labels)
        style.getSourceAs<GeoJsonSource>("items")?.setGeoJson(r.items)
        style.getSourceAs<GeoJsonSource>("contacts")?.setGeoJson(r.contacts)
    }

    fun tap(p: LatLon) {
        when (ui.mode) {
            Mode.DRAW -> ui.measure.add(p)
            Mode.PLACE -> {
                val item = ui.placingCustom?.let { newItem(app, ui, Kind.MARKER, "custom:${it.id}", listOf(p)) } ?: ui.placing?.let { newItem(app, ui, Kind.MARKER, it.sidc, listOf(p)) }
                if (item != null && session.engine.saveItem(item)) { ui.editing = item.uid; ui.sheet = Sheet.ITEM }
                ui.stopDrawing()
            }
            Mode.BOX_FIRST -> { ui.boxCorner = p; ui.mode = Mode.BOX_SECOND }
            Mode.BOX_SECOND -> {
                val a = ui.boxCorner ?: p
                ui.box = BBox(min(a.lat, p.lat), min(a.lon, p.lon), max(a.lat, p.lat), max(a.lon, p.lon))
                ui.boxCorner = null; ui.mode = Mode.BROWSE; ui.sheet = Sheet.DOWNLOAD
            }
            else -> {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapLayer(app, activity, ui, ::tap) { uid, isContact ->
            if (isContact) contacts[uid]?.let { ui.room = Triple(RoomKind.DIRECT, uid, it.callsign); ui.sheet = Sheet.CHAT }
            else { ui.editing = uid; ui.sheet = Sheet.ITEM }
        }
        val night = s.night
        Canvas(Modifier.fillMaxSize()) {
            ui.cameraTick // redraw as the camera moves
            val m = ui.map ?: return@Canvas
            if (s.grid) drawMgrsGrid(m, night)
            val c = Offset(size.width / 2, size.height / 2); val r = 14.dp.toPx()
            for ((col, wd) in listOf(Color.White to 5.dp.toPx(), (if (night) Color.Red else Color.Black) to 2.dp.toPx())) {
                drawLine(col, c - Offset(r, 0f), c + Offset(r, 0f), wd); drawLine(col, c - Offset(0f, r), c + Offset(0f, r), wd)
            }
            ui.box?.takeIf { ui.sheet == Sheet.DOWNLOAD || ui.downloadJob != null }?.let { b ->
                val a = m.projection.toScreenLocation(LatLng(b.north, b.west)); val z = m.projection.toScreenLocation(LatLng(b.south, b.east))
                drawRect(Color(0xFFE00000), Offset(a.x, a.y), Size(z.x - a.x, z.y - a.y), style = Stroke(3.dp.toPx()))
            }
        }
        if (ui.mode == Mode.MOVE) MoveLayer(ui, session)
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            Banners(app, ui, session, demo, alerts)
            TopBar(app, ui, session)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f)) {
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
                    SideButton(stringResource(if (s.dark) R.string.dark_on else R.string.go_dark), s.dark, Modifier.tourTarget(ui, "dark")) { app.setDark(!s.dark) }
                    HoldButton(stringResource(R.string.sos), Modifier.width(68.dp).tourTarget(ui, "sos")) {
                        val mine = alerts.any { it.uid == session.repo.self.uid }
                        session.engine.sendAlert(mine, ui.fix ?: ui.centre); ui.tour.sos++
                        vibrate(app, 400)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SideButton(stringResource(R.string.locate), false, Modifier.tourTarget(ui, "locate")) {
                        ui.tour.locates++
                        if (demo && ui.fix == null) { ui.fix = DEMO_POSITION; ui.flyTo(DEMO_POSITION, 13.0) }
                        else withLocation { ui.fix?.let { ui.flyTo(it) } ?: run { ui.message = activity.localized(s.lang, R.string.no_fix) } }
                    }
                    SideButton(stringResource(R.string.layers), false, Modifier.tourTarget(ui, "layers")) { ui.sheet = Sheet.LAYERS }
                    SideButton(stringResource(R.string.mark), ui.mode == Mode.DRAW || ui.mode == Mode.PLACE, Modifier.tourTarget(ui, "mark")) {
                        if (ui.mode != Mode.BROWSE) ui.stopDrawing() else ui.sheet = Sheet.PICKER
                    }
                    val unread = remember(repoTick) { session.repo.unread() }
                    SideButton(stringResource(R.string.messages) + if (unread > 0) " ($unread)" else "", unread > 0, Modifier.tourTarget(ui, "chat")) { ui.sheet = Sheet.CHAT }
                    SideButton(stringResource(R.string.missions), false, Modifier.tourTarget(ui, "missions")) { ui.sheet = Sheet.MISSIONS }
                    SideButton(stringResource(R.string.menu), recording != null, Modifier.tourTarget(ui, "menu")) { ui.sheet = Sheet.MENU }
                }
            }
            BottomBar(app, ui, session)
        }
        TourOverlay(app, activity, ui, session, ::withLocation)
    }
    Sheets(app, activity, ui, session, ::withLocation)
    FollowWatcher(app, ui, session)
}

/** A spot on the fictional demo island, used as "your position" during the tour. */
val DEMO_POSITION = LatLon(20.045, 64.055)

fun vibrate(ctx: Context, ms: Long) = runCatching { ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }

fun Context.localized(lang: String, id: Int): String =
    createConfigurationContext(android.content.res.Configuration(resources.configuration).apply { setLocale(Locale(lang)) }).getString(id)

private fun viewBox(m: MapLibreMap): BBox? = runCatching {
    val p = m.projection; val w = m.width; val h = m.height
    val c = listOf(android.graphics.PointF(0f, 0f), android.graphics.PointF(w, 0f), android.graphics.PointF(0f, h), android.graphics.PointF(w, h)).map { p.fromScreenLocation(it) }
    BBox(c.minOf { it.latitude }, c.minOf { it.longitude }, c.maxOf { it.latitude }, c.maxOf { it.longitude })
}.getOrNull()

private class Overlay(val items: String, val gfx: String, val labels: String, val contacts: String, val images: Map<String, Bitmap>)

/** Turns stored items into map features. Point symbols and labels become images; multi-point graphics come from mil-sym. */
private fun renderOverlay(session: Session, box: BBox, scale: Double, density: Float, known: Set<String>, contacts: Collection<Contact>): Overlay {
    val repo = session.repo
    val missions = repo.missions().associateBy { it.id }
    val custom = repo.customSymbols.associateBy { it.id }
    val images = HashMap<String, Bitmap>()
    val pts = JSONArray(); val gfx = JSONArray(); val labels = JSONArray(); val people = JSONArray()
    val size = (36 * density).toInt()
    fun image(key: String, make: () -> Bitmap?): String? {
        if (key in known || key in images) return key
        return make()?.let { images[key] = it; key }
    }
    fun point(p: LatLon, icon: String, uid: String, angle: Double = 0.0, contact: Boolean = false) = JSONObject().put("type", "Feature")
        .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray(listOf(p.lon, p.lat))))
        .put("properties", JSONObject().put("i", icon).put("uid", uid).put("angle", angle).put("contact", contact))
    fun text(label: String, color: String) = image("t|$label|$color") { Symbols.label(label, 15 * density, runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(android.graphics.Color.BLACK)) }
    // Grow the drawing area so graphics stay complete while panning between redraws.
    val dx = (box.east - box.west) / 2; val dy = (box.north - box.south) / 2

    for (i in repo.items()) {
        if (i.points.isEmpty() || (i.mission.isNotEmpty() && missions[i.mission]?.visible != true)) continue
        if (i.kind == Kind.MARKER) {
            val key = image("m|${i.sidc}|${i.name}") {
                if (i.sidc.startsWith("custom:")) Symbols.badge(custom[i.sidc.substringAfter(':')]?.label ?: "?", i.name, size)
                else Symbols.icon(i.sidc, i.name, size) ?: Symbols.badge("?", i.name, size)
            } ?: continue
            pts.put(point(i.points[0], key, i.uid))
            continue
        }
        var drawn = false
        if (i.kind != Kind.ROUTE && Sidc.isStandard(i.sidc)) runCatching {
            val json = Symbols.graphic(i.uid, i.sidc, i.name, i.points, box.west - dx, box.south - dy, box.east + dx, box.north + dy, scale, i.color.takeIf { it != "#000000" }) ?: return@runCatching
            val feats = JSONObject(json).getJSONArray("features")
            for (k in 0 until feats.length()) {
                val f = feats.getJSONObject(k); val g = f.optJSONObject("geometry") ?: continue; val p = f.optJSONObject("properties") ?: continue
                val co = g.optJSONArray("coordinates") ?: continue
                if (co.length() == 0) continue
                if (g.getString("type") == "Point") {
                    val label = p.optString("label"); if (label.isBlank()) continue
                    val key = text(label, p.optString("fontColor", "#000000")) ?: continue
                    labels.put(point(LatLon(co.getDouble(1), co.getDouble(0)), key, i.uid, p.optDouble("angle", 0.0)))
                } else {
                    p.put("uid", i.uid)
                    if (!p.has("strokeColor") && !p.has("fillColor")) p.put("strokeColor", "#000000")
                    gfx.put(JSONObject().put("type", "Feature").put("geometry", g).put("properties", p)); drawn = true
                }
            }
        }
        if (!drawn) { // plain route, imported shape, or a graphic the renderer could not draw
            val color = if (i.color == "#000000" && i.kind == Kind.ROUTE) "#D4007A" else i.color
            val ring = JSONArray((if (i.kind == Kind.AREA) i.points + i.points[0] else i.points).map { JSONArray(listOf(it.lon, it.lat)) })
            val geom = if (i.kind == Kind.AREA) JSONObject().put("type", "Polygon").put("coordinates", JSONArray(listOf(ring))) else JSONObject().put("type", "LineString").put("coordinates", ring)
            val props = JSONObject().put("uid", i.uid).put("strokeColor", color).put("strokeWidth", 4)
            if (i.kind == Kind.AREA) props.put("fillColor", color).put("fillOpacity", 0.15)
            gfx.put(JSONObject().put("type", "Feature").put("geometry", geom).put("properties", props))
            if (i.name.isNotBlank()) text(i.name, color)?.let { labels.put(point(i.points[0], it, i.uid)) }
        }
    }
    val now = System.currentTimeMillis()
    for (c in contacts) {
        val stale = now - c.time > 5 * 60_000
        val key = image("c|${c.callsign}|$stale") { Symbols.icon(Sidc.make(if (stale) '1' else '3', "10", "000000"), c.callsign, size) ?: Symbols.badge("•", c.callsign, size) } ?: continue
        people.put(point(c.pos, key, c.uid, contact = true))
    }
    fun fc(a: JSONArray) = JSONObject().put("type", "FeatureCollection").put("features", a).toString()
    return Overlay(fc(pts), fc(gfx), fc(labels), fc(people), images)
}

@Composable
private fun MapLayer(app: App, activity: ComponentActivity, ui: Ui, onTap: (LatLon) -> Unit, onLongPress: (uid: String, contact: Boolean) -> Unit) {
    val s = app.settings
    val tap by rememberUpdatedState(onTap); val long by rememberUpdatedState(onLongPress)
    val view = remember {
        MapView(activity).apply {
            onCreate(null)
            ui.view = this
            getMapAsync { m ->
                m.uiSettings.apply {
                    // North-up and flat: no rotate or tilt gestures to trigger by accident with gloves.
                    isRotateGesturesEnabled = false; isTiltGesturesEnabled = false
                    isCompassEnabled = false; isLogoEnabled = false; isAttributionEnabled = false
                }
                m.setMaxZoomPreference(19.0)
                val demoStart = app.hub.packs.value.firstNotNullOfOrNull { it.bounds }?.let { LatLon((it.south + it.north) / 2, (it.west + it.east) / 2) }
                val start = if (!s.tourDone && demoStart != null) demoStart
                else if (!s.camLat.isNaN()) LatLon(s.camLat.toDouble(), s.camLon.toDouble()) else demoStart ?: LatLon(30.0, 70.0)
                m.cameraPosition = CameraPosition.Builder().target(LatLng(start.lat, start.lon)).zoom(if (!s.tourDone) 11.5 else s.camZoom.toDouble()).build()
                ui.centre = start; ui.zoom = m.cameraPosition.zoom.toFloat()
                m.addOnCameraMoveListener {
                    m.cameraPosition.target?.let { ui.centre = LatLon(it.latitude, it.longitude) }
                    ui.cameraTick++
                }
                m.addOnCameraIdleListener {
                    val c = m.cameraPosition
                    c.target?.let { s.camLat = it.latitude.toFloat(); s.camLon = it.longitude.toFloat() }
                    s.camZoom = c.zoom.toFloat(); ui.zoom = c.zoom.toFloat()
                    ui.cameraTick++; ui.idleTick++
                }
                m.addOnMapClickListener { tap(LatLon(it.latitude, it.longitude)); true }
                m.addOnMapLongClickListener { ll ->
                    if (ui.mode == Mode.BROWSE) {
                        val pt = m.projection.toScreenLocation(ll); val d = 28 * resources.displayMetrics.density
                        val hit = runCatching { m.queryRenderedFeatures(RectF(pt.x - d, pt.y - d, pt.x + d, pt.y + d), "items", "contacts", "labels", "gfx_line", "gfx_fill") }.getOrDefault(emptyList())
                            .firstOrNull { it.hasProperty("uid") }
                        hit?.let { long(it.getStringProperty("uid"), it.hasProperty("contact") && it.getBooleanProperty("contact") == true) }
                    }
                    true
                }
                ui.map = m
            }
        }
    }
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                Lifecycle.Event.ON_DESTROY -> view.onDestroy()
                else -> {}
            }
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs); ui.map = null; ui.view = null; runCatching { view.onStop(); view.onDestroy() } }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}

/** Drag anywhere to move the selected item: the finger does not have to hit the symbol itself. */
@Composable
private fun MoveLayer(ui: Ui, session: Session) {
    var from by remember { mutableStateOf<Offset?>(null) }
    var at by remember { mutableStateOf<Offset?>(null) }
    Canvas(Modifier.fillMaxSize().pointerInput(ui.editing) {
        detectDragGestures(
            onDragStart = { from = it; at = it },
            onDrag = { change, _ -> at = change.position },
            onDragCancel = { from = null; at = null },
            onDragEnd = {
                val m = ui.map; val a = from; val b = at; val item = ui.editing?.let { session.repo.item(it) }
                if (m != null && a != null && b != null && item != null) {
                    val p0 = m.projection.fromScreenLocation(android.graphics.PointF(a.x, a.y)); val p1 = m.projection.fromScreenLocation(android.graphics.PointF(b.x, b.y))
                    val moved = if (item.kind == Kind.MARKER) listOf(LatLon(p1.latitude, p1.longitude))
                    else item.points.map { LatLon(it.lat + p1.latitude - p0.latitude, it.lon + p1.longitude - p0.longitude) }
                    session.engine.saveItem(item.copy(points = moved, updated = System.currentTimeMillis()))
                }
                from = null; at = null; ui.mode = Mode.BROWSE
            },
        )
    }) {
        at?.let { drawCircle(Color(0xFFE00000), 22.dp.toPx(), it, style = Stroke(4.dp.toPx())) }
    }
}

/** Own position from the device GPS only (no Google Play Services), while the app is on screen. */
@SuppressLint("MissingPermission")
@Composable
private fun LocationFeed(activity: ComponentActivity, ui: Ui) {
    if (!ui.hasLocation) return
    DisposableEffect(Unit) {
        val lm = activity.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) { ui.fix = LatLon(l.latitude, l.longitude); TrackService.fix.value = l }
            @Deprecated("Required below API 29") override fun onStatusChanged(p: String?, st: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        val obs = LifecycleEventObserver { _, e ->
            runCatching {
                if (e == Lifecycle.Event.ON_START) {
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { ui.fix = LatLon(it.latitude, it.longitude) }
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, listener)
                } else if (e == Lifecycle.Event.ON_STOP) lm.removeUpdates(listener)
            }
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs); runCatching { lm.removeUpdates(listener) } }
    }
}

/** Route following: advances to the next waypoint and raises the off-route alert. */
@Composable
private fun FollowWatcher(app: App, ui: Ui, session: Session) {
    val uid = ui.follow ?: return
    val pos = ui.fix ?: return
    val route = remember(uid, session.repo.tick.collectAsState().value) { session.repo.item(uid) } ?: run { ui.follow = null; return }
    var off by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(pos, route) {
        if (ui.followIndex < route.points.lastIndex && Geodesy.distance(pos, route.points[ui.followIndex]) < 30) ui.followIndex++
        val now = North.offRoute(pos, route.points) > 50
        if (now && !off) { vibrate(app, 800); ui.message = app.localized(app.settings.lang, R.string.off_route) }
        off = now
    }
}

@Composable
fun SideButton(label: String, active: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, modifier = modifier.size(68.dp, 58.dp), shape = RoundedCornerShape(10.dp),
        color = if (active) cs.primary else cs.surface, contentColor = if (active) cs.onPrimary else cs.onSurface,
        border = BorderStroke(2.dp, cs.outline), shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun Banners(app: App, ui: Ui, session: Session, demo: Boolean, alerts: List<CotEvent.Alert>) {
    val cs = MaterialTheme.colorScheme
    @Composable fun bar(text: String, bg: Color, fg: Color, onClick: (() -> Unit)? = null) = Surface(
        onClick = { onClick?.invoke() }, enabled = onClick != null, color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) { Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 14.sp) }
    if (demo) bar(stringResource(R.string.demo_banner), Color(0xFFFFD400), Color.Black)
    if (session.duress) Spacer(Modifier.height(0.dp)) // the duress profile looks exactly like an empty normal one
    if (app.settings.dark) bar(stringResource(R.string.dark_banner), Color.Black, Color.White)
    for (a in alerts) bar(stringResource(R.string.emergency) + ": " + a.callsign, cs.error, cs.onError) { ui.flyTo(a.pos) }
}

@Composable
private fun TopBar(app: App, ui: Ui, session: Session) {
    val s = app.settings
    val cs = MaterialTheme.colorScheme
    val elevation by produceState<Double?>(null, ui.centre, ui.dataTick, ui.styleTick) {
        value = withContext(Dispatchers.Default) { app.hub.elevation(ui.centre) }
    }
    Surface(shape = RoundedCornerShape(10.dp), color = cs.surface, border = BorderStroke(2.dp, cs.outline), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).tourTarget(ui, "coords")) {
                    // Coordinates always read left to right, also in Urdu.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(CoordText.format(ui.centre, s.coordFormat), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
                    }
                    Text(
                        s.format + "  ·  " + (elevation?.let { stringResource(R.string.elevation, it.roundToInt().toString()) } ?: stringResource(R.string.no_elevation)),
                        fontSize = 14.sp,
                    )
                }
                Button(
                    onClick = { ui.sheet = Sheet.GOTO }, modifier = Modifier.heightIn(min = 56.dp).tourTarget(ui, "goto"), shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.go_to), fontWeight = FontWeight.Bold) }
            }
            val from = ui.fix ?: ui.centre
            when {
                ui.mode == Mode.BOX_FIRST || ui.mode == Mode.BOX_SECOND -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (ui.mode == Mode.BOX_FIRST) R.string.tap_first_corner else R.string.tap_second_corner),
                        Modifier.weight(1f), fontWeight = FontWeight.Bold,
                    )
                    TextButton({ ui.mode = Mode.BROWSE; ui.boxCorner = null }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.cancel)) }
                }
                ui.follow != null -> {
                    val route = remember(ui.follow, session.repo.tick.collectAsState().value) { ui.follow?.let { session.repo.item(it) } }
                    val wp = route?.points?.getOrNull(ui.followIndex.coerceAtMost((route.points.size - 1).coerceAtLeast(0)))
                    if (wp != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        val (d, b) = Geodesy.inverse(from, wp)
                        Text(
                            stringResource(R.string.follow_line, ui.followIndex + 1, route.points.size, bearingText(app, b, from), distanceText(d), bearingText(app, North.back(b), from)),
                            Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        )
                        TextButton({ ui.follow = null }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.stop)) }
                    }
                }
                ui.target != null -> ui.target?.let { t ->
                    val (d, b) = Geodesy.inverse(from, t)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.to_target, bearingText(app, b, from), distanceText(d)), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        TextButton({ ui.target = null }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.clear)) }
                    }
                }
            }
            ui.message?.let { msg ->
                Text(msg, color = cs.error, fontWeight = FontWeight.Bold)
                LaunchedEffect(msg) { kotlinx.coroutines.delay(5000); ui.message = null }
            }
        }
    }
}

@Composable
private fun BottomBar(app: App, ui: Ui, session: Session) {
    val cs = MaterialTheme.colorScheme
    if (ui.mode != Mode.DRAW && ui.mode != Mode.PLACE && ui.mode != Mode.MOVE) return
    Surface(shape = RoundedCornerShape(10.dp), color = cs.surface, border = BorderStroke(2.dp, cs.outline), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            if (ui.mode == Mode.MOVE) {
                Text(stringResource(R.string.drag_to_move), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Big(stringResource(R.string.cancel), Modifier.fillMaxWidth()) { ui.mode = Mode.BROWSE }
                return@Column
            }
            if (ui.mode == Mode.PLACE) {
                Text((ui.placing?.name ?: ui.placingCustom?.name ?: "") + " — " + stringResource(R.string.tap_to_place), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Big(stringResource(R.string.cancel), Modifier.weight(1f)) { ui.stopDrawing() }
                    Big(stringResource(R.string.place_at_centre), Modifier.weight(1f), filled = true) {
                        val item = ui.placingCustom?.let { newItem(app, ui, Kind.MARKER, "custom:${it.id}", listOf(ui.centre)) } ?: ui.placing?.let { newItem(app, ui, Kind.MARKER, it.sidc, listOf(ui.centre)) }
                        if (item != null && session.engine.saveItem(item)) { ui.editing = item.uid; ui.sheet = Sheet.ITEM }
                        ui.stopDrawing()
                    }
                }
                return@Column
            }
            val entry = ui.drawing
            val need = entry?.points ?: 2
            Text(
                (entry?.name?.let { "$it — " } ?: "") + if (ui.measure.size < 2) stringResource(R.string.tap_to_add)
                else stringResource(R.string.distance, distanceText(Geodesy.pathLength(ui.measure))),
                fontWeight = FontWeight.Bold, fontSize = 17.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Big(stringResource(R.string.undo), Modifier.weight(1f), enabled = ui.measure.isNotEmpty()) { ui.measure.removeAt(ui.measure.lastIndex) }
                Big(stringResource(R.string.profile), Modifier.weight(1f), enabled = ui.measure.size >= 2) { ui.tour.profiles++; ui.sheet = Sheet.PROFILE }
                if (entry == null) Big(stringResource(R.string.done), Modifier.weight(1f), filled = true) { ui.stopDrawing() }
                else Big(stringResource(R.string.save), Modifier.weight(1f), enabled = ui.measure.size >= need, filled = true) {
                    val item = newItem(app, ui, entry.kind, entry.sidc, ui.measure.toList())
                    if (session.engine.saveItem(item)) { ui.editing = item.uid; ui.sheet = Sheet.ITEM }
                    ui.mode = Mode.BROWSE; ui.drawing = null; ui.measure.clear()
                }
            }
        }
    }
}

/** Glove-sized button: at least 56 dp tall. */
@Composable
fun Big(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, filled: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val m = modifier.heightIn(min = 56.dp)
    if (filled) Button(onClick, m, enabled, shape) { Text(text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
    else OutlinedButton(onClick, m, enabled, shape, border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)) {
        Text(text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
    }
}

val FORMATS = CoordFormat.values().toList()
