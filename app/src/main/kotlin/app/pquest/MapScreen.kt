package app.pquest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.pquest.map.BBox
import app.pquest.map.MapHub
import app.pquest.map.StyleBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Sheet { NONE, LAYERS, GOTO, MAPS, DOWNLOAD, TRACKS, SETTINGS, PROFILE }
enum class Mode { BROWSE, MEASURE, BOX_FIRST, BOX_SECOND }

/** Everything the single map screen needs to remember. */
class Ui {
    var sheet by mutableStateOf(Sheet.NONE)
    var mode by mutableStateOf(Mode.BROWSE)
    var map by mutableStateOf<MapLibreMap?>(null)
    var cameraTick by mutableIntStateOf(0)
    var centre by mutableStateOf(LatLon(0.0, 0.0))
    var fix by mutableStateOf<LatLon?>(null)
    var hasLocation by mutableStateOf(false)
    var target by mutableStateOf<LatLon?>(null)
    val measure: SnapshotStateList<LatLon> = mutableStateListOf()
    var shownTrack by mutableStateOf<Long?>(null)
    var boxCorner by mutableStateOf<LatLon?>(null)
    var box by mutableStateOf<BBox?>(null)
    var dataTick by mutableIntStateOf(0)
    var styleTick by mutableIntStateOf(0)
    var progress by mutableStateOf<MapHub.Progress?>(null)
    var downloadJob by mutableStateOf<Job?>(null)
    var message by mutableStateOf<String?>(null)

    fun tap(p: LatLon) {
        when (mode) {
            Mode.MEASURE -> measure.add(p)
            Mode.BOX_FIRST -> { boxCorner = p; mode = Mode.BOX_SECOND }
            Mode.BOX_SECOND -> {
                val a = boxCorner ?: p
                box = BBox(min(a.lat, p.lat), min(a.lon, p.lon), max(a.lat, p.lat), max(a.lon, p.lon))
                boxCorner = null; mode = Mode.BROWSE; sheet = Sheet.DOWNLOAD
            }
            Mode.BROWSE -> {}
        }
    }

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
}

@Composable
fun distanceText(m: Double): String =
    if (m < 1000) stringResource(R.string.metres, m.roundToInt())
    else stringResource(R.string.kilometres, "%.2f".format(java.util.Locale.ROOT, m / 1000))

@Composable
fun MapScreen(app: App, activity: ComponentActivity) {
    val ui = remember { Ui() }
    val s = app.settings
    val packs by app.hub.packs.collectAsState()
    val recording by TrackService.recording.collectAsState()
    val trackTick by app.tracks.changes.collectAsState()

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

    // Rebuild the style whenever what is drawn changes; everything is served from the device.
    LaunchedEffect(ui.map, s.base, s.hillshade, s.night, packs, ui.dataTick, s.online, s.urlTopo, s.urlSatellite, s.urlDem) {
        val m = ui.map ?: return@LaunchedEffect
        app.applyOnline()
        val json = withContext(Dispatchers.IO) { app.hub.style(s.base.takeIf { it != "none" }, s.hillshade, s.night) }
        m.setStyle(Style.Builder().fromJson(json)) { ui.styleTick++ }
    }
    // Push overlays (own position, target, measured route, track) into the style's GeoJSON sources.
    val trackId = ui.shownTrack ?: recording
    LaunchedEffect(ui.styleTick, ui.fix, ui.target, ui.measure.toList(), trackId, trackTick) {
        val style = ui.map?.style ?: return@LaunchedEffect
        val track = if (trackId == null) emptyList() else withContext(Dispatchers.IO) { app.tracks.points(trackId) }
        style.getSourceAs<GeoJsonSource>("track")?.setGeoJson(StyleBuilder.line(track).let { if (track.size < 2) StyleBuilder.line(emptyList()) else it })
        style.getSourceAs<GeoJsonSource>("measure")?.setGeoJson(StyleBuilder.line(ui.measure))
        style.getSourceAs<GeoJsonSource>("marks")?.setGeoJson(
            StyleBuilder.marks(listOfNotNull(ui.target?.let { it to "#e00000" }, ui.fix?.let { it to "#0a66ff" }))
        )
    }

    Box(Modifier.fillMaxSize()) {
        MapLayer(app, activity, ui)
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
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            TopBar(app, ui)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f)) {
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SideButton(stringResource(R.string.locate)) {
                        withLocation { ui.fix?.let { ui.flyTo(it) } ?: run { ui.message = activity.localized(s.lang, R.string.no_fix) } }
                    }
                    SideButton(stringResource(R.string.layers)) { ui.sheet = Sheet.LAYERS }
                    SideButton(stringResource(R.string.measure), ui.mode == Mode.MEASURE) {
                        ui.mode = if (ui.mode == Mode.MEASURE) Mode.BROWSE else Mode.MEASURE
                    }
                    SideButton(stringResource(R.string.track), recording != null) { ui.sheet = Sheet.TRACKS }
                    SideButton(stringResource(R.string.maps)) { ui.sheet = Sheet.MAPS }
                    SideButton(stringResource(R.string.settings)) { ui.sheet = Sheet.SETTINGS }
                }
            }
            BottomBar(ui)
        }
    }
    Sheets(app, activity, ui, ::withLocation)
}

fun Context.localized(lang: String, id: Int): String =
    createConfigurationContext(android.content.res.Configuration(resources.configuration).apply { setLocale(java.util.Locale(lang)) }).getString(id)

@Composable
private fun MapLayer(app: App, activity: ComponentActivity, ui: Ui) {
    val s = app.settings
    val view = remember {
        MapView(activity).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.apply {
                    // North-up and flat: no rotate or tilt gestures to trigger by accident with gloves.
                    isRotateGesturesEnabled = false; isTiltGesturesEnabled = false
                    isCompassEnabled = false; isLogoEnabled = false; isAttributionEnabled = false
                }
                m.setMaxZoomPreference(19.0)
                val start = if (!s.camLat.isNaN()) LatLon(s.camLat.toDouble(), s.camLon.toDouble())
                else app.hub.packs.value.firstNotNullOfOrNull { it.bounds }?.let { LatLon((it.south + it.north) / 2, (it.west + it.east) / 2) }
                    ?: LatLon(30.0, 70.0)
                m.cameraPosition = CameraPosition.Builder().target(LatLng(start.lat, start.lon)).zoom(s.camZoom.toDouble()).build()
                ui.centre = start
                m.addOnCameraMoveListener {
                    m.cameraPosition.target?.let { ui.centre = LatLon(it.latitude, it.longitude) }
                    ui.cameraTick++
                }
                m.addOnCameraIdleListener {
                    val c = m.cameraPosition
                    c.target?.let { s.camLat = it.latitude.toFloat(); s.camLon = it.longitude.toFloat() }
                    s.camZoom = c.zoom.toFloat()
                    ui.cameraTick++
                }
                m.addOnMapClickListener { ui.tap(LatLon(it.latitude, it.longitude)); true }
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
        onDispose { activity.lifecycle.removeObserver(obs) }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}

/** Own position from the device GPS only (no Google Play Services), while the app is on screen. */
@SuppressLint("MissingPermission")
@Composable
private fun LocationFeed(activity: ComponentActivity, ui: Ui) {
    if (!ui.hasLocation) return
    DisposableEffect(Unit) {
        val lm = activity.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) { ui.fix = LatLon(l.latitude, l.longitude) }
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

@Composable
fun SideButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, modifier = Modifier.size(68.dp, 58.dp), shape = RoundedCornerShape(10.dp),
        color = if (active) cs.primary else cs.surface, contentColor = if (active) cs.onPrimary else cs.onSurface,
        border = BorderStroke(2.dp, cs.outline), shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun TopBar(app: App, ui: Ui) {
    val s = app.settings
    val cs = MaterialTheme.colorScheme
    val elevation by produceState<Double?>(null, ui.centre, ui.dataTick, ui.styleTick) {
        value = withContext(Dispatchers.Default) { app.hub.elevation(ui.centre) }
    }
    Surface(shape = RoundedCornerShape(10.dp), color = cs.surface, border = BorderStroke(2.dp, cs.outline), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
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
                    onClick = { ui.sheet = Sheet.GOTO }, modifier = Modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.go_to), fontWeight = FontWeight.Bold) }
            }
            when (ui.mode) {
                Mode.BOX_FIRST, Mode.BOX_SECOND -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (ui.mode == Mode.BOX_FIRST) R.string.tap_first_corner else R.string.tap_second_corner),
                        Modifier.weight(1f), fontWeight = FontWeight.Bold,
                    )
                    TextButton({ ui.mode = Mode.BROWSE; ui.boxCorner = null }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.cancel)) }
                }
                else -> ui.target?.let { t ->
                    val (d, b) = Geodesy.inverse(ui.fix ?: ui.centre, t)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.to_target, "%03d".format(java.util.Locale.ROOT, b.roundToInt() % 360), distanceText(d)),
                            Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        )
                        TextButton({ ui.target = null }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.clear)) }
                    }
                }
            }
            ui.message?.let { msg ->
                Text(msg, color = cs.error, fontWeight = FontWeight.Bold)
                LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); ui.message = null }
            }
        }
    }
}

@Composable
private fun BottomBar(ui: Ui) {
    val cs = MaterialTheme.colorScheme
    if (ui.mode != Mode.MEASURE) return
    Surface(shape = RoundedCornerShape(10.dp), color = cs.surface, border = BorderStroke(2.dp, cs.outline), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                if (ui.measure.size < 2) stringResource(R.string.tap_to_add)
                else stringResource(R.string.distance, distanceText(Geodesy.pathLength(ui.measure))),
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Big(stringResource(R.string.undo), Modifier.weight(1f), enabled = ui.measure.isNotEmpty()) { ui.measure.removeAt(ui.measure.lastIndex) }
                Big(stringResource(R.string.profile), Modifier.weight(1f), enabled = ui.measure.size >= 2) { ui.sheet = Sheet.PROFILE }
                Big(stringResource(R.string.done), Modifier.weight(1f), filled = true) { ui.mode = Mode.BROWSE; ui.measure.clear() }
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
