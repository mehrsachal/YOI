package app.pquest

import android.Manifest
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pquest.coords.Geodesy
import app.pquest.coords.LatLon
import app.pquest.coords.Mgrs
import app.pquest.cot.Kind
import java.io.ByteArrayOutputStream
import java.util.Locale

/** The grid the user is asked to type in step 4. A point on the fictional demo island. */
val TOUR_GRID_POINT = LatLon(20.07, 64.08)

val TOUR_TITLES = listOf(
    R.string.tour_1, R.string.tour_2, R.string.tour_3, R.string.tour_4, R.string.tour_5, R.string.tour_6,
    R.string.tour_7, R.string.tour_8, R.string.tour_9, R.string.tour_10, R.string.tour_11,
)

/** One thing the user must actually do. [target] is the control to spotlight; [manual] tasks finish with Next. */
class TourTask(val text: Int, val target: String?, val manual: Boolean = false, val done: (Ui, App, Session, TourState.Snap) -> Boolean = { _, _, _, _ -> false })

val TOUR_TASKS: Map<Int, List<TourTask>> = mapOf(
    2 to listOf(
        TourTask(R.string.task_zoom, null) { ui, _, _, b -> ui.zoom > b.zoom + 0.6f },
        TourTask(R.string.task_locate, "locate") { ui, _, _, b -> ui.tour.locates > b.locates },
        TourTask(R.string.task_layers, "layers") { ui, _, _, b -> ui.tour.layers > b.layers },
    ),
    3 to listOf(
        TourTask(R.string.task_read_grid, "coords", manual = true),
        TourTask(R.string.task_goto, "goto") { ui, _, _, _ -> ui.target?.let { Geodesy.distance(it, TOUR_GRID_POINT) < 150 } == true },
    ),
    4 to listOf(TourTask(R.string.task_mark, "mark") { ui, _, s, b -> ui.tour.named > b.named && s.repo.items().any { it.kind == Kind.MARKER && it.name.isNotBlank() && it.updated >= b.time } }),
    5 to listOf(TourTask(R.string.task_route, "mark") { ui, _, s, b -> ui.tour.profiles > b.profiles && s.repo.items().any { it.kind == Kind.ROUTE && it.points.size >= 3 } }),
    6 to listOf(TourTask(R.string.task_download, "menu") { ui, _, _, b -> ui.tour.downloads > b.downloads && ui.downloadJob == null }),
    7 to listOf(
        TourTask(R.string.task_join, "missions") { _, _, s, _ -> s.repo.missions().any { it.id == "demo" } },
        TourTask(R.string.task_toggle, "missions") { ui, _, _, b -> ui.tour.toggles > b.toggles },
    ),
    8 to listOf(
        TourTask(R.string.task_quick, "chat") { ui, _, _, b -> ui.tour.quick > b.quick },
        TourTask(R.string.task_sos, "sos") { ui, _, _, b -> ui.tour.sos > b.sos },
    ),
    9 to listOf(
        TourTask(R.string.task_dark, "dark") { _, app, _, _ -> app.settings.dark },
        TourTask(R.string.task_lock_wipe, null, manual = true),
    ),
)

class TourState {
    /** Counters bumped by the real controls, so a step completes only when the user has done the action. */
    var sos by mutableIntStateOf(0); var locates by mutableIntStateOf(0); var profiles by mutableIntStateOf(0); var layers by mutableIntStateOf(0)
    var downloads by mutableIntStateOf(0); var named by mutableIntStateOf(0); var quick by mutableIntStateOf(0); var toggles by mutableIntStateOf(0)
    var replay by mutableStateOf(false)
    var only by mutableStateOf<Int?>(null)
    var hidden by mutableStateOf(false)
    var sub by mutableIntStateOf(0)
    var started = System.currentTimeMillis()

    class Snap(val sos: Int, val locates: Int, val profiles: Int, val layers: Int, val downloads: Int, val named: Int, val quick: Int, val toggles: Int, val zoom: Float, val time: Long)
    fun snap(ui: Ui) = Snap(sos, locates, profiles, layers, downloads, named, quick, toggles, ui.zoom, System.currentTimeMillis())

    /** Replays the whole tour, or one step, from Settings > Help. Always in demo mode. */
    fun start(app: App, session: Session, step: Int?) {
        started = System.currentTimeMillis(); replay = true; only = step; hidden = false; sub = 0
        app.settings.tourStep = (step ?: 0).toString()
        session.engine.demo = true
    }

    fun finish(app: App, session: Session, ui: Ui) {
        session.repo.purgeDemo(started, session.repo.self.uid)
        app.hub.demoDownload = false
        if (app.settings.dark) app.setDark(false)
        app.settings.tourDone = true; app.settings.tourStep = "0"
        replay = false; only = null; sub = 0
        session.engine.alerts.value = emptyList()
        ui.stopDrawing(); ui.target = null; ui.follow = null; ui.room = ALL_ROOM
        if (ui.fix == DEMO_POSITION) ui.fix = null
        session.engine.demo = false
    }
}

private fun active(app: App, ui: Ui) = (!app.settings.tourDone || ui.tour.replay) && !ui.tour.hidden
private fun stepOf(app: App) = app.settings.tourStep.toIntOrNull()?.coerceIn(0, 10) ?: 0

@Composable
private fun taskText(t: TourTask): String =
    if (t.text == R.string.task_goto) stringResource(t.text, Mgrs.format(TOUR_GRID_POINT, 4)) else stringResource(t.text)

/** The current instruction, repeated at the top of any open sheet (sheets cover the main tour card). */
@Composable
fun TourHint(app: App, ui: Ui, session: Session) {
    if (!active(app, ui)) return
    val task = TOUR_TASKS[stepOf(app)]?.getOrNull(ui.tour.sub) ?: return
    Surface(color = Color(0xFFFFD400), contentColor = Color.Black, shape = RoundedCornerShape(8.dp)) {
        Text(taskText(task), Modifier.fillMaxWidth().padding(10.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TourOverlay(app: App, activity: ComponentActivity, ui: Ui, session: Session, withLocation: (() -> Unit) -> Unit) {
    if (!active(app, ui)) return
    val s = app.settings; val tour = ui.tour
    val step = stepOf(app)
    val tasks = TOUR_TASKS[step]
    val repoTick by session.repo.tick.collectAsState()

    fun advance() {
        tour.sub = 0
        if (tour.only != null || step >= 10) tour.finish(app, session, ui) else s.tourStep = (step + 1).toString()
    }
    LaunchedEffect(step) {
        app.hub.demoDownload = step == 6
        if (step == 6) { app.hub.packs.value.firstOrNull { !it.dem && it.name.startsWith("Demo island") }?.let { app.hub.update(it.copy(visible = false)) }; s.base = "topo" }
        if (step == 8) ui.room = ALL_ROOM
        if (step != 9 && s.dark) app.setDark(false)
    }
    val task = tasks?.getOrNull(tour.sub)
    val base = remember(step, tour.sub) { tour.snap(ui) }
    // Completion is checked whenever anything the tasks look at changes.
    LaunchedEffect(step, tour.sub, repoTick, ui.zoom, ui.target, tour.sos, tour.locates, tour.profiles, tour.layers, tour.downloads, tour.named, tour.quick, tour.toggles, s.dark, ui.downloadJob) {
        if (task != null && !task.manual && task.done(ui, app, session, base)) {
            if (tour.sub < tasks.lastIndex) tour.sub++ else advance()
        }
    }

    // Spotlight: dim everything except the control being taught (touches still pass through to the map and buttons).
    val rect = task?.target?.let { ui.targets[it] }
    if (ui.sheet == Sheet.NONE && rect != null) Canvas(Modifier.fillMaxSize()) {
        val pad = 6.dp.toPx()
        val hole = RoundRect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad, CornerRadius(12.dp.toPx()))
        drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)); addRoundRect(hole) }, Color.Black.copy(alpha = 0.55f))
        drawPath(Path().apply { addRoundRect(hole) }, Color(0xFFFFD400), style = Stroke(4.dp.toPx()))
    }

    val cs = MaterialTheme.colorScheme
    @Composable fun footer(next: (() -> Unit)?) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!tour.replay) {
            TextButton({ tour.finish(app, session, ui) }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.tour_skip)) }
            TextButton({ tour.hidden = true }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.tour_later)) }
        } else TextButton({ tour.finish(app, session, ui) }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.tour_stop)) }
        Spacer(Modifier.weight(1f))
        if (next != null) Big(stringResource(R.string.next), filled = true) { next() }
    }
    @Composable fun header() = Text(stringResource(R.string.tour_step, step + 1, 11) + "  ·  " + stringResource(TOUR_TITLES[step]), fontWeight = FontWeight.Black, fontSize = 17.sp)

    if (tasks != null && task != null) {
        // Sits just under the coordinate bar so it never covers the buttons being taught.
        val top = with(LocalDensity.current) { ((ui.targets["goto"]?.bottom ?: 0f) / density).dp } + 14.dp
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 84.dp, end = 84.dp, top = top), contentAlignment = Alignment.TopCenter) {
            Surface(shape = RoundedCornerShape(10.dp), color = cs.surface, border = BorderStroke(3.dp, Color(0xFFFFD400)), shadowElevation = 6.dp) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    header()
                    Text(taskText(task), fontSize = 16.sp)
                    footer(if (task.manual) ({ if (tour.sub < tasks.lastIndex) tour.sub++ else advance() }) else null)
                }
            }
        }
        return
    }
    // Steps 1, 2 and 11 are full cards.
    Surface(Modifier.fillMaxSize(), color = cs.background.copy(alpha = 0.97f)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            header()
            when (step) {
                0 -> {
                    Label(stringResource(R.string.language))
                    Choice(listOf("en" to "English", "ur" to "اردو"), s.lang) { s.lang = it }
                    Label(stringResource(R.string.bearings))
                    Choice(listOf(false to stringResource(R.string.degrees), true to stringResource(R.string.mils)), s.mils) { s.mils = it }
                    Text(stringResource(R.string.units_metric))
                    Identity(session)
                    Text(stringResource(R.string.tour_intro))
                }
                1 -> {
                    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) ui.hasLocation = true }
                    Text(stringResource(R.string.perm_location_why)); Big(stringResource(R.string.perm_location), Modifier.fillMaxWidth()) { withLocation {} }
                    Text(stringResource(R.string.perm_bluetooth_why))
                    Big(stringResource(R.string.perm_bluetooth), Modifier.fillMaxWidth(), enabled = Build.VERSION.SDK_INT >= 31) { ask.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) }
                    Text(stringResource(R.string.perm_storage_why))
                }
                else -> {
                    Text(stringResource(R.string.tour_done_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    for (i in 2..9) Text("✓  " + stringResource(TOUR_TITLES[i]), fontSize = 17.sp)
                    Text(stringResource(R.string.tour_done_text))
                }
            }
            footer { advance() }
        }
    }
}

/** The card's content, taken from the same step titles and instructions as the tour: (text, isHeading). */
fun quickReferenceLines(ctx: Context, lang: String): List<Pair<String, Boolean>> {
    val c = ctx.createConfigurationContext(android.content.res.Configuration(ctx.resources.configuration).apply { setLocale(Locale(lang)) })
    val out = ArrayList<Pair<String, Boolean>>()
    for (step in 2..9) {
        out += "${step + 1}. " + c.getString(TOUR_TITLES[step]) to true
        for (t in TOUR_TASKS.getValue(step)) out += "•  " + (if (t.text == R.string.task_goto) c.getString(t.text, "41Q PC 1234 5678") else c.getString(t.text)) to false
    }
    out += c.getString(R.string.card_footer) to true
    return listOf(c.getString(R.string.app_name) + " — " + c.getString(R.string.quick_card) to true) + out
}

/** One-page printable quick-reference card (A4), in English or Urdu. */
fun quickReference(ctx: Context, lang: String): ByteArray {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val cv = page.canvas
    val rtl = lang == "ur"
    val face = if (rtl) runCatching { ctx.resources.getFont(R.font.nastaliq) }.getOrDefault(Typeface.DEFAULT) else Typeface.DEFAULT
    var y = 36f
    quickReferenceLines(ctx, lang).forEachIndexed { i, (text, heading) ->
        val size = when { i == 0 -> 18f; heading -> if (rtl) 11f else 12f; else -> if (rtl) 9f else 10f }
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = if (heading && !rtl) Typeface.DEFAULT_BOLD else face; isFakeBoldText = heading && rtl }
        val l = StaticLayout.Builder.obtain(text, 0, text.length, p, 523).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, if (rtl) 1.25f else 1.05f).build()
        if (heading && i > 0) y += 5f
        cv.save(); cv.translate(36f, y); l.draw(cv); cv.restore()
        y += l.height + if (i == 0) 8f else 1f
    }
    doc.finishPage(page)
    return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
}
