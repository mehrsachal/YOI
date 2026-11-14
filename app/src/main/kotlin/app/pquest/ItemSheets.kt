package app.pquest

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pquest.coords.CoordFormat
import app.pquest.coords.CoordText
import app.pquest.coords.Dtg
import app.pquest.coords.Geodesy
import app.pquest.cot.Item
import app.pquest.cot.Kind
import app.pquest.cot.Sidc
import app.pquest.missions.DataPackage
import app.pquest.missions.Formats
import app.pquest.missions.Links
import app.pquest.symbology.Catalog
import app.pquest.symbology.Entry
import app.pquest.symbology.Symbols
import app.pquest.symbology.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun EntryButton(e: Entry, badge: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { 40.dp.toPx().toInt() }
    val icon = remember(e.sidc, badge) { if (badge != null) Symbols.badge(badge, "", px) else if (e.kind == Kind.MARKER) Symbols.icon(e.sidc, "", px) else null }
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(10.dp), color = cs.surface, border = BorderStroke(2.dp, cs.outline)) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Symbols keep their standard colours on a white tile so they read the same in day and night themes.
            Surface(color = androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (icon != null) Image(icon.asImageBitmap(), null, Modifier.padding(2.dp))
                    else Text(if (e.kind == Kind.AREA) "▱" else "╱", fontSize = 24.sp, color = androidx.compose.ui.graphics.Color.Black)
                }
            }
            Text(e.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp)
        }
    }
}

/** The simplified picker: about 40 common symbols in five tabs, with search across the full 2525 set. */
@Composable
fun PickerSheet(app: App, ui: Ui, session: Session) {
    var tab by remember { mutableStateOf(Tab.FRIENDLY) }
    var query by remember { mutableStateOf("") }
    val missions = remember(session.repo.tick.collectAsState().value) { session.repo.missions() }
    fun pick(e: Entry) {
        ui.sheet = Sheet.NONE; ui.measure.clear()
        if (e.kind == Kind.MARKER) { ui.placing = e; ui.placingCustom = null; ui.mode = Mode.PLACE } else { ui.drawing = e; ui.mode = Mode.DRAW }
    }
    Title(stringResource(R.string.mark))
    val target = missions.firstOrNull { it.id == ui.activeMission && it.role.canEdit }
    Text(stringResource(R.string.draw_into, target?.name ?: stringResource(R.string.my_overlay)))
    val tabs = listOf(Tab.FRIENDLY to R.string.tab_friendly, Tab.ENEMY to R.string.tab_enemy, Tab.OBSTACLES to R.string.tab_obstacles, Tab.POINTS to R.string.tab_points, Tab.CONTROL to R.string.tab_control)
    Choice(tabs.take(3).map { it.first to stringResource(it.second) }, tab) { tab = it; query = "" }
    Choice(tabs.drop(3).map { it.first to stringResource(it.second) }, tab) { tab = it; query = "" }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.search_all_symbols)) })
    val results by produceState(emptyList<Entry>(), query, tab) {
        value = if (query.trim().length < 2) emptyList() else withContext(Dispatchers.Default) { Symbols.search(query, if (tab == Tab.ENEMY) '6' else '3') }
    }
    val list = if (query.trim().length >= 2) results else Catalog.tabs.getValue(tab)
    for (pair in list.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (e in pair) EntryButton(e, modifier = Modifier.weight(1f)) { pick(e) }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
    if (tab == Tab.POINTS && query.isBlank()) {
        Label(stringResource(R.string.unit_signs))
        for (pair in session.repo.customSymbols.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in pair) EntryButton(Entry(c.name, "", Kind.MARKER), c.label, Modifier.weight(1f)) { ui.sheet = Sheet.NONE; ui.placingCustom = c; ui.placing = null; ui.mode = Mode.PLACE }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Opened by long-pressing a symbol or graphic: name, affiliation, echelon, move, delete. */
@Composable
fun ItemSheet(app: App, ui: Ui, session: Session) {
    val tick by session.repo.tick.collectAsState()
    val item = remember(ui.editing, tick) { ui.editing?.let { session.repo.item(it) } }
    if (item == null || item.deleted) { Text(stringResource(R.string.nothing_selected)); return }
    val mission = remember(tick) { session.repo.missions().firstOrNull { it.id == item.mission } }
    val canEdit = item.mission.isEmpty() || mission?.role?.canEdit == true
    var name by remember(item.uid) { mutableStateOf(item.name) }
    var remarks by remember(item.uid) { mutableStateOf(item.remarks) }
    var sidc by remember(item.uid) { mutableStateOf(item.sidc) }
    fun save(extra: Item.() -> Item = { this }) = session.engine.saveItem(item.copy(name = name.trim(), remarks = remarks.trim(), sidc = sidc, updated = System.currentTimeMillis()).extra())

    Title(name.ifBlank { stringResource(R.string.unnamed) })
    Text((mission?.name ?: stringResource(R.string.my_overlay)) + "  ·  " + CoordText.format(item.points[0], CoordFormat.MGRS), fontFamily = FontFamily.Monospace)
    if (!canEdit) Text(stringResource(R.string.view_only), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth().tourTarget(ui, "item_name"), singleLine = true, enabled = canEdit, label = { Text(stringResource(R.string.name)) })
    OutlinedTextField(remarks, { remarks = it.take(300) }, Modifier.fillMaxWidth(), enabled = canEdit, label = { Text(stringResource(R.string.remarks)) })
    if (canEdit && item.kind == Kind.MARKER && Sidc.isStandard(sidc) && Sidc.symbolSet(sidc) in setOf("10", "15", "20")) {
        Label(stringResource(R.string.affiliation))
        Choice(Catalog.AFFILIATIONS.map { it.first to stringResource(when (it.first) { '3' -> R.string.tab_friendly; '6' -> R.string.tab_enemy; '4' -> R.string.neutral; else -> R.string.unknown }) }, Sidc.affiliation(sidc)) { sidc = Sidc.withAffiliation(sidc, it) }
        Label(stringResource(R.string.echelon))
        for (row in Catalog.ECHELONS.chunked(4)) Choice(row, Sidc.echelon(sidc)) { sidc = Sidc.withEchelon(sidc, it) }
    }
    if (item.kind != Kind.MARKER) {
        Text(stringResource(R.string.distance, distanceText(Geodesy.pathLength(item.points))), fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Big(stringResource(R.string.profile), Modifier.weight(1f)) { ui.measure.clear(); ui.measure.addAll(item.points); ui.drawing = null; ui.mode = Mode.DRAW; ui.tour.profiles++; ui.sheet = Sheet.PROFILE }
            Big(stringResource(R.string.follow), Modifier.weight(1f), filled = ui.follow == item.uid) { ui.follow = item.uid; ui.followIndex = 0; ui.sheet = Sheet.NONE; ui.fit(item.points) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Big(stringResource(R.string.go_to), Modifier.weight(1f)) { ui.target = item.points[0]; ui.flyTo(item.points[0]); ui.sheet = Sheet.NONE }
        if (canEdit) Big(stringResource(R.string.move), Modifier.weight(1f)) { save(); ui.mode = Mode.MOVE; ui.sheet = Sheet.NONE }
    }
    if (canEdit) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Big(stringResource(R.string.delete), Modifier.weight(1f)) { save { copy(deleted = true) }; if (ui.follow == item.uid) ui.follow = null; ui.sheet = Sheet.NONE }
        Big(stringResource(R.string.save), Modifier.weight(1f).tourTarget(ui, "item_save"), filled = true) { save(); ui.tour.named++; ui.sheet = Sheet.NONE }
    }
}

/** Import an overlay or package; export the overlay, a mission or a map picture. */
@Composable
fun ShareSheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    val scope = rememberCoroutineScope()
    val tick by session.repo.tick.collectAsState()
    val missions = remember(tick) { session.repo.missions() }
    var scope0 by remember { mutableStateOf(ui.activeMission) }
    var status by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<String?>(null) }
    val lang = app.settings.lang
    val self = remember(tick) { session.repo.self }
    val items = remember(tick, scope0) { session.repo.items().filter { it.mission == scope0 } }
    val stamp = remember { Dtg.format(System.currentTimeMillis(), true).replace(" ", "") }
    val title = missions.firstOrNull { it.id == scope0 }?.name ?: "overlay"

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = try {
                val name = displayName(activity, uri)
                val bytes = withContext(Dispatchers.IO) { activity.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                importAny(app, ui, session, name, bytes)
            } catch (e: Exception) { activity.localized(lang, R.string.overlay_import_failed) + (e.message?.let { "\n($it)" } ?: "") }
        }
    }
    Title(stringResource(R.string.share_export))
    Tip(app, "share", R.string.tip_share)
    Big(stringResource(R.string.import_overlay), Modifier.fillMaxWidth()) { picker.launch(arrayOf("*/*")) }
    Text(status ?: stringResource(R.string.import_overlay_hint))
    HorizontalDivider()
    Label(stringResource(R.string.what_to_export))
    for (row in (listOf("" to stringResource(R.string.my_overlay)) + missions.map { it.id to it.name }).chunked(2)) Choice(row, scope0) { scope0 = it; qr = null }
    Text(stringResource(R.string.items_count, items.size))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Big("KMZ", Modifier.weight(1f), enabled = items.isNotEmpty()) {
            val px = (48 * activity.resources.displayMetrics.density).toInt()
            val icons = HashMap<String, ByteArray>()
            fun iconName(i: Item) = if (Sidc.isStandard(i.sidc)) "s" + (i.sidc + i.name).hashCode().toUInt().toString(16) + ".png" else null
            for (i in items.filter { it.kind == Kind.MARKER }) iconName(i)?.let { n -> if (n !in icons) Symbols.icon(i.sidc, i.name, px)?.let { icons[n] = png(it) } }
            shareFile(activity, "$title-$stamp.kmz", "application/vnd.google-earth.kmz", Formats.toKmz(title, items, icons) { i -> iconName(i)?.takeIf { it in icons } })
        }
        Big("GPX", Modifier.weight(1f), enabled = items.isNotEmpty()) { shareFile(activity, "$title-$stamp.gpx", "application/gpx+xml", Formats.toGpx(items).toByteArray()) }
        Big("GeoJSON", Modifier.weight(1f), enabled = items.isNotEmpty()) { shareFile(activity, "$title-$stamp.geojson", "application/geo+json", Formats.toGeoJson(items).toByteArray()) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Big(stringResource(R.string.tak_package), Modifier.weight(1f), enabled = items.isNotEmpty()) {
            shareFile(activity, "$title-$stamp.zip", "application/zip", DataPackage.write(title, items, self, missions.firstOrNull { it.id == scope0 }))
        }
        Big(stringResource(R.string.qr_code), Modifier.weight(1f), enabled = items.isNotEmpty()) {
            qr = Links.items(items, self); if (qr == null) status = activity.localized(lang, R.string.too_big_for_qr)
        }
    }
    qr?.let { QrImage(it) }
    HorizontalDivider()
    Label(stringResource(R.string.map_snapshot))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (fmt in listOf("PNG", "PDF")) Big(fmt, Modifier.weight(1f)) {
            val m = ui.map ?: return@Big
            m.snapshot { shot ->
                val img = decorate(app, m, shot, ui.centre, activity.resources.displayMetrics.density)
                if (fmt == "PNG") shareFile(activity, "map-$stamp.png", "image/png", png(img)) else shareFile(activity, "map-$stamp.pdf", "application/pdf", pdf(img, "P-Quest  ·  " + Dtg.format(System.currentTimeMillis(), app.settings.zulu)))
            }
        }
    }
}

/** Handles every kind of file or QR text a user can bring in. Returns a message to show. */
suspend fun importAny(app: App, ui: Ui, session: Session, name: String, bytes: ByteArray): String {
    val lang = app.settings.lang
    val isPackage = name.endsWith(".zip", true) && runCatching { Formats.unzip(bytes).keys.any { it.contains("MANIFEST", true) || it.endsWith(".pref") || it.endsWith(".cot") || it.endsWith(".properties") } }.getOrDefault(false)
    if (isPackage) {
        val c = withContext(Dispatchers.Default) { DataPackage.read(bytes) }
        val out = ArrayList<String>()
        c.server?.let { session.repo.server = it; session.engine.connectServer(); out += app.localized(lang, R.string.server_configured).format(it.host) }
        c.mission?.let { m ->
            val had = session.repo.missions().firstOrNull { it.id == m.id }
            session.engine.join(had ?: m); ui.activeMission = m.id; out += app.localized(lang, R.string.joined_mission).format(m.name)
        }
        val target = c.mission?.id ?: ui.activeMission
        c.items.forEach { session.engine.saveItem(it.copy(mission = if (c.mission != null) target else session.repo.missions().firstOrNull { m -> m.id == target && m.role.canEdit }?.id ?: "")) }
        if (c.items.isNotEmpty()) { out += app.localized(lang, R.string.items_imported).format(c.items.size); ui.fit(c.items.flatMap { it.points }) }
        return out.joinToString("\n").ifEmpty { app.localized(lang, R.string.overlay_import_failed) }
    }
    val mission = session.repo.missions().firstOrNull { it.id == ui.activeMission && it.role.canEdit }?.id ?: ""
    val items = withContext(Dispatchers.Default) { Formats.import(name, bytes, Formats.Ctx(session.repo.self.uid, System.currentTimeMillis(), mission)) }
    require(items.isNotEmpty()) { "no points, lines or areas found" }
    items.forEach { session.engine.saveItem(it) }
    ui.fit(items.flatMap { it.points })
    return app.localized(lang, R.string.items_imported).format(items.size)
}
