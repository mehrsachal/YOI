package app.pquest

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pquest.coords.CoordFormat
import app.pquest.coords.CoordText
import app.pquest.coords.Geodesy
import app.pquest.map.MapHub
import app.pquest.map.TileMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private fun mb(bytes: Long) = "%.1f".format(Locale.ROOT, bytes / 1e6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheets(app: App, activity: ComponentActivity, ui: Ui, withLocation: (() -> Unit) -> Unit) {
    if (ui.sheet == Sheet.NONE) return
    ModalBottomSheet(
        onDismissRequest = { ui.sheet = Sheet.NONE },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (ui.sheet) {
                Sheet.LAYERS -> LayersSheet(app)
                Sheet.GOTO -> GoToSheet(app, ui)
                Sheet.MAPS -> MapsSheet(app, activity, ui)
                Sheet.DOWNLOAD -> DownloadSheet(app, ui)
                Sheet.TRACKS -> TracksSheet(app, activity, ui, withLocation)
                Sheet.SETTINGS -> SettingsSheet(app)
                Sheet.PROFILE -> ProfileSheet(app, ui)
                Sheet.NONE -> {}
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Label(text: String) = Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)

/** A row of equal-width choice buttons; the selected one is filled. */
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, label) in options) Big(label, Modifier.weight(1f), filled = v == selected) { onPick(v) }
    }

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 17.sp)
        Switch(checked, onChange)
    }

@Composable
private fun LayersSheet(app: App) {
    val s = app.settings
    val packs by app.hub.packs.collectAsState()
    Title(stringResource(R.string.layers))
    Label(stringResource(R.string.base_map))
    Choice(listOf("topo" to stringResource(R.string.topo), "satellite" to stringResource(R.string.satellite), "none" to stringResource(R.string.none)), s.base) { s.base = it }
    SwitchRow(stringResource(R.string.hillshade), s.hillshade) { s.hillshade = it }
    SwitchRow(stringResource(R.string.grid) + " (MGRS)", s.grid) { s.grid = it }
    Label(stringResource(R.string.map_packs))
    if (packs.isEmpty()) Text(stringResource(R.string.no_packs))
    for (p in packs) {
        if (p.dem) { Text("${p.name} · ${stringResource(R.string.elevation_pack)}"); continue }
        SwitchRow(p.name, p.visible) { app.hub.update(p.copy(visible = it)) }
        if (p.visible) {
            var o by remember(p.id) { mutableFloatStateOf(p.opacity) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.opacity), Modifier.width(96.dp))
                Slider(o, { o = it }, Modifier.weight(1f).heightIn(min = 56.dp), valueRange = 0.1f..1f, onValueChangeFinished = { app.hub.update(p.copy(opacity = o)) })
            }
        }
    }
}

@Composable
private fun GoToSheet(app: App, ui: Ui) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { CoordText.parse(text) }
    Title(stringResource(R.string.go_to))
    OutlinedTextField(
        text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.enter_coordinate)) },
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        isError = text.isNotBlank() && parsed == null,
    )
    Text(
        when {
            parsed != null -> CoordText.format(parsed, CoordFormat.MGRS) + "\n" + CoordText.format(parsed, CoordFormat.DD)
            text.isBlank() -> stringResource(R.string.goto_hint)
            else -> stringResource(R.string.not_understood)
        },
        fontFamily = if (parsed != null) FontFamily.Monospace else null,
    )
    Big(stringResource(R.string.go), Modifier.fillMaxWidth(), enabled = parsed != null, filled = true) {
        parsed?.let { ui.target = it; ui.flyTo(it); ui.sheet = Sheet.NONE }
    }
}

@Composable
private fun MapsSheet(app: App, activity: ComponentActivity, ui: Ui) {
    val scope = rememberCoroutineScope()
    val packs by app.hub.packs.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val lang = app.settings.lang
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            status = try {
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "map"
                val pack = withContext(Dispatchers.IO) { activity.contentResolver.openInputStream(uri)!!.use { app.hub.importPack(it, name) } }
                pack.bounds?.let { b -> ui.map?.let { ui.flyTo(app.pquestCentre(b), pack.minZoom.toDouble() + 1) } }
                activity.localized(lang, R.string.import_ok).format(pack.name)
            } catch (e: Exception) { activity.localized(lang, R.string.import_failed) }
            busy = false
        }
    }
    val canDownload = MapHub.BUILT_IN.any { app.hub.canFetch(it) }
    Title(stringResource(R.string.maps))
    Big(stringResource(R.string.download_area), Modifier.fillMaxWidth(), enabled = canDownload, filled = true) {
        ui.box = null; ui.sheet = Sheet.DOWNLOAD
    }
    if (!canDownload) Text(stringResource(R.string.online_off_hint))
    Big(stringResource(R.string.import_pack), Modifier.fillMaxWidth(), enabled = !busy) { picker.launch(arrayOf("*/*")) }
    Text(if (busy) stringResource(R.string.importing) else status ?: stringResource(R.string.import_hint))
    Label(stringResource(R.string.map_packs))
    if (packs.isEmpty()) Text(stringResource(R.string.no_packs))
    for (p in packs) Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${p.name}\nz${p.minZoom}–${p.maxZoom} · ${mb(File(p.file).length())} MB", Modifier.weight(1f))
        Big(stringResource(R.string.delete)) { app.hub.remove(p.id); ui.dataTick++ }
    }
    key(ui.dataTick) { Text(stringResource(R.string.stored, mb(MapHub.BUILT_IN.sumOf { app.hub.storedBytes(it) }))) }
}

private fun App.pquestCentre(b: app.pquest.map.BBox) = app.pquest.coords.LatLon((b.south + b.north) / 2, (b.west + b.east) / 2)

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label: $value", Modifier.weight(1f), fontSize = 17.sp)
        Big("−", enabled = value > range.first) { onChange(value - 1) }
        Big("+", enabled = value < range.last) { onChange(value + 1) }
    }

@Composable
private fun DownloadSheet(app: App, ui: Ui) {
    var radius by remember { mutableIntStateOf(if (ui.box == null) 10 else 0) }
    var zMin by remember { mutableIntStateOf(8) }
    var zMax by remember { mutableIntStateOf(15) }
    val centre = remember { ui.centre }
    val layers = MapHub.BUILT_IN.filter { app.hub.canFetch(it) }
    val box = if (radius > 0) TileMath.around(centre, radius * 1000.0) else ui.box ?: TileMath.around(centre, 10_000.0)
    val tiles = TileMath.count(box, zMin, zMax) * layers.size
    val job = ui.downloadJob
    val scope = remember { kotlinx.coroutines.CoroutineScope(Dispatchers.Main) } // outlives the sheet so a download keeps running

    Title(stringResource(R.string.download_area))
    Label(stringResource(R.string.around_centre))
    Choice(listOf(5, 10, 25, 50).map { it to stringResource(R.string.radius_km, it) }, radius) { radius = it; ui.box = TileMath.around(centre, it * 1000.0) }
    Big(stringResource(if (radius == 0) R.string.box_selected else R.string.draw_box), Modifier.fillMaxWidth(), filled = radius == 0) {
        ui.mode = Mode.BOX_FIRST; ui.sheet = Sheet.NONE
    }
    Stepper(stringResource(R.string.min_zoom), zMin, 0..zMax) { zMin = it }
    Stepper(stringResource(R.string.max_zoom), zMax, zMin..17) { zMax = it }
    Text(stringResource(R.string.estimate, tiles.toInt(), mb(app.hub.estimateBytes(layers, box, zMin, zMax))), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    if (tiles > 150_000) Text(stringResource(R.string.too_many_tiles), color = MaterialTheme.colorScheme.error)
    ui.progress?.let { p ->
        LinearProgressIndicator({ if (p.total == 0) 0f else p.done / p.total.toFloat() }, Modifier.fillMaxWidth().height(10.dp))
        Text(if (job != null) stringResource(R.string.downloading, p.done, p.total) else stringResource(R.string.download_done, p.done - p.failed, p.failed))
    }
    if (job == null) Big(stringResource(R.string.download), Modifier.fillMaxWidth(), enabled = layers.isNotEmpty() && tiles in 1..150_000, filled = true) {
        ui.box = box
        ui.progress = MapHub.Progress(0, tiles.toInt(), 0)
        ui.downloadJob = scope.launch {
            try { ui.progress = app.hub.download(layers, box, zMin, zMax) { ui.progress = it } }
            finally { ui.downloadJob = null; ui.dataTick++ }
        }
    } else Big(stringResource(R.string.cancel), Modifier.fillMaxWidth()) { job.cancel() }
}

@Composable
private fun TracksSheet(app: App, activity: ComponentActivity, ui: Ui, withLocation: (() -> Unit) -> Unit) {
    val recording by TrackService.recording.collectAsState()
    val tick by app.tracks.changes.collectAsState()
    val list by produceState(emptyList<Triple<app.pquest.map.TrackInfo, Double, Unit>>(), tick) {
        value = withContext(Dispatchers.IO) { app.tracks.list().map { Triple(it, Geodesy.pathLength(app.tracks.points(it.id)), Unit) } }
    }
    Title(stringResource(R.string.track))
    if (recording == null) Big(stringResource(R.string.start_recording), Modifier.fillMaxWidth(), filled = true) {
        withLocation { ui.shownTrack = null; TrackService.start(activity) }
    } else Big(stringResource(R.string.stop_recording), Modifier.fillMaxWidth(), filled = true) { TrackService.stop(activity) }
    Text(stringResource(R.string.recording_note))
    if (list.isEmpty()) Text(stringResource(R.string.no_tracks))
    for ((t, dist, _) in list) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t.name + "\n" + stringResource(R.string.track_summary, distanceText(dist), t.points), Modifier.weight(1f), fontWeight = if (t.id == recording) FontWeight.Bold else null)
        Big(stringResource(R.string.show), filled = ui.shownTrack == t.id) {
            ui.shownTrack = t.id; ui.sheet = Sheet.NONE
            ui.fit(app.tracks.points(t.id))
        }
        if (t.id != recording) Big(stringResource(R.string.delete)) { if (ui.shownTrack == t.id) ui.shownTrack = null; app.tracks.delete(t.id) }
    }
}

@Composable
private fun SettingsSheet(app: App) {
    val s = app.settings
    Title(stringResource(R.string.settings))
    Label(stringResource(R.string.language))
    Choice(listOf("en" to "English", "ur" to "اردو"), s.lang) { s.lang = it }
    Label(stringResource(R.string.theme))
    Choice(listOf(false to stringResource(R.string.day), true to stringResource(R.string.night)), s.night) { s.night = it }
    Label(stringResource(R.string.coord_format))
    Choice(FORMATS.map { it.name to it.name }, s.format) { s.format = it }
    HorizontalDivider()
    SwitchRow(stringResource(R.string.online_maps), s.online) { s.online = it }
    Text(stringResource(R.string.online_warning))
    if (s.online) {
        UrlField(stringResource(R.string.url_topo), s.urlTopo) { s.urlTopo = it }
        UrlField(stringResource(R.string.url_satellite), s.urlSatellite) { s.urlSatellite = it }
        UrlField(stringResource(R.string.url_dem), s.urlDem) { s.urlDem = it }
    }
    Text(stringResource(R.string.version, BuildInfo.VERSION), fontSize = 13.sp)
}

object BuildInfo { const val VERSION = "0.1.0 (Phase 1)" }

@Composable
private fun UrlField(label: String, value: String, onChange: (String) -> Unit) {
    val bad = value.isNotBlank() && !(value.trim().startsWith("https://") && listOf("{z}", "{x}", "{y}").all { it in value })
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, isError = bad,
        supportingText = if (bad) ({ Text(stringResource(R.string.https_only)) }) else null,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
    )
}

@Composable
private fun ProfileSheet(app: App, ui: Ui) {
    val pts = ui.measure.toList()
    val samples by produceState<List<Pair<Double, Double>>?>(null, pts) {
        value = withContext(Dispatchers.Default) {
            Geodesy.sample(pts, 160).mapNotNull { (d, p) -> app.hub.elevation(p)?.let { d to it } }
        }
    }
    Title(stringResource(R.string.elevation_profile))
    Text(stringResource(R.string.distance, distanceText(Geodesy.pathLength(pts))), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    val data = samples ?: return
    if (data.size < 2) { Text(stringResource(R.string.no_dem_here)); return }
    val lo = data.minOf { it.second }; val hi = data.maxOf { it.second }
    val steps = data.zipWithNext { a, b -> b.second - a.second }
    Text(stringResource(R.string.profile_stats, lo.roundToInt(), hi.roundToInt(), steps.filter { it > 0 }.sum().roundToInt(), -steps.filter { it < 0 }.sum().roundToInt()))
    val ink = MaterialTheme.colorScheme.onSurface
    val fill = MaterialTheme.colorScheme.secondaryContainer
    // Distance runs left to right regardless of UI language.
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val span = (hi - lo).coerceAtLeast(10.0); val total = data.last().first.coerceAtLeast(1.0)
        fun at(d: Double, e: Double) = Offset((d / total * size.width).toFloat(), (size.height - (e - lo) / span * size.height * 0.92).toFloat())
        val line = Path()
        data.forEachIndexed { i, (d, e) -> at(d, e).let { if (i == 0) line.moveTo(it.x, it.y) else line.lineTo(it.x, it.y) } }
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, fill)
        drawPath(line, ink, style = Stroke(3.dp.toPx()))
        drawLine(ink, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
        drawLine(ink, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx())
    }
}
