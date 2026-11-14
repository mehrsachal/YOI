package app.pquest

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pquest.coords.Dtg
import app.pquest.cot.Kind
import app.pquest.cot.MsgState
import app.pquest.cot.RoomKind
import app.pquest.cot.Sidc
import app.pquest.cot.Item
import app.pquest.mesh.MeshCrypto
import app.pquest.missions.DataPackage
import app.pquest.missions.Links
import app.pquest.missions.Mission
import app.pquest.missions.Role
import app.pquest.net.Enrolment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID

val ALL_ROOM = Triple(RoomKind.ALL, "All Chat Rooms", "All Chat Rooms")

/** Shows which links are up. The user never chooses one: the engine picks server, then Wi-Fi, then mesh. */
@Composable
fun LinkStatus(session: Session) {
    val server by session.engine.serverUp.collectAsState(); val wifi by session.engine.wifi.up.collectAsState(); val mesh by session.engine.radio.connected.collectAsState()
    val queued = remember(session.repo.tick.collectAsState().value) { session.repo.queued() }
    fun mark(b: Boolean) = if (b) "●" else "○"
    Text(
        "${mark(server)} ${stringResource(R.string.server)}   ${mark(wifi)} Wi-Fi   ${mark(mesh)} ${stringResource(R.string.mesh)}" +
            if (queued > 0) "   ·   " + stringResource(R.string.queued_count, queued) else "",
        fontSize = 14.sp, fontWeight = FontWeight.Bold,
    )
}

@Composable
fun ChatSheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    val scope = rememberCoroutineScope()
    val tick by session.repo.tick.collectAsState()
    val contacts by session.engine.contacts.collectAsState()
    val self = remember(tick) { session.repo.self }
    val room = ui.room
    LinkStatus(session)
    if (room == null) {
        Title(stringResource(R.string.messages))
        val existing = remember(tick) { session.repo.rooms() }
        val missions = remember(tick) { session.repo.missions() }
        val rooms = (listOf(ALL_ROOM, Triple(RoomKind.TEAM, self.team, self.team)) + missions.map { Triple(RoomKind.MISSION, it.name, it.name) } +
            existing.filter { it.kind == RoomKind.DIRECT }.map { Triple(it.kind, it.room, it.label) } + contacts.values.map { Triple(RoomKind.DIRECT, it.uid, it.callsign) }).distinctBy { it.first to it.second }
        for (r in rooms) {
            val unread = existing.firstOrNull { it.kind == r.first && it.room == r.second }?.unread ?: 0
            val kind = stringResource(when (r.first) { RoomKind.ALL -> R.string.room_all; RoomKind.TEAM -> R.string.team; RoomKind.MISSION -> R.string.mission; RoomKind.DIRECT -> R.string.room_direct })
            Big("$kind: ${if (r.first == RoomKind.ALL) "" else r.third}" + if (unread > 0) "  ($unread)" else "", Modifier.fillMaxWidth(), filled = unread > 0) { ui.room = r }
        }
        return
    }
    LaunchedEffect(room, tick) { session.engine.markRead(room.first, room.second) }
    val msgs = remember(room, tick) { session.repo.messages(room.first, room.second).takeLast(40) }
    var text by remember { mutableStateOf("") }
    var withLoc by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }
    val blocked = stringResource(R.string.attachment_blocked)
    fun send(body: String) { session.engine.sendChat(room.first, room.second, room.third, body, if (withLoc) ui.fix ?: ui.centre else null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (room.first == RoomKind.ALL) stringResource(R.string.room_all) else room.third, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Big(stringResource(R.string.rooms)) { ui.room = null }
    }
    if (msgs.isEmpty()) Text(stringResource(R.string.no_messages))
    for (m in msgs) Surface(
        color = if (m.mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            val state = if (!m.mine) "" else "  ·  " + stringResource(when (m.state) { MsgState.QUEUED -> R.string.state_queued; MsgState.SENT -> R.string.state_sent; MsgState.DELIVERED -> R.string.state_delivered; MsgState.READ -> R.string.state_read })
            Text(m.msg.fromCallsign + "  ·  " + Dtg.format(m.msg.time, app.settings.zulu) + state, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(m.msg.text, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                m.msg.loc?.let { p -> TextButton({ ui.flyTo(p); ui.target = p; ui.sheet = Sheet.NONE }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.show_on_map)) } }
                Regex("https://\\S+/Marti/sync/content\\?hash=\\S+").find(m.msg.text)?.value?.let { url ->
                    TextButton({ scope.launch { withContext(Dispatchers.IO) { session.engine.download(url) }?.let { shareFile(activity, "attachment.png", "image/png", it) } ?: run { note = blocked } } }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.open_attachment)) }
                }
            }
        }
    }
    Label(stringResource(R.string.quick_messages))
    for (pair in remember(tick) { session.repo.quick }.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (q in pair) Big(q, Modifier.weight(1f).tourTarget(ui, "quick")) { send(q); ui.tour.quick++ }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
    OutlinedTextField(text, { text = it.take(400) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.message)) })
    SwitchRow(stringResource(R.string.attach_location), withLoc) { withLoc = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Big(stringResource(R.string.send_map_picture), Modifier.weight(1f)) {
            val m = ui.map ?: return@Big
            m.snapshot { shot ->
                val img = png(decorate(app, m, shot, ui.centre, activity.resources.displayMetrics.density))
                scope.launch {
                    val url = withContext(Dispatchers.IO) { session.engine.upload("map-${System.currentTimeMillis()}.png", img) }
                    if (url == null) note = blocked else send("[map] $url")
                }
            }
        }
        Big(stringResource(R.string.send), Modifier.weight(1f), enabled = text.isNotBlank(), filled = true) { send(text.trim()); text = "" }
    }
    note?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
}

/** Acts on any pquest:// link from a QR code: join a mission, receive a small overlay, or enrol with a server. */
suspend fun handleLink(app: App, ui: Ui, session: Session, text: String?): String {
    val lang = app.settings.lang
    if (text == null) return app.localized(lang, R.string.qr_not_read)
    Links.parseMission(text)?.let { m ->
        session.engine.join(session.repo.missions().firstOrNull { it.id == m.id } ?: m); ui.activeMission = m.id
        return app.localized(lang, R.string.joined_mission).format(m.name)
    }
    Links.parseItems(text)?.let { items ->
        val mission = session.repo.missions().firstOrNull { it.id == ui.activeMission && it.role.canEdit }?.id ?: ""
        items.forEach { session.engine.saveItem(it.copy(mission = mission)) }
        ui.fit(items.flatMap { it.points })
        return app.localized(lang, R.string.items_imported).format(items.size)
    }
    Links.parseEnrol(text)?.let { e ->
        if (session.engine.demo || app.settings.dark) return app.localized(lang, R.string.enrol_failed)
        return try {
            val cfg = withContext(Dispatchers.IO) { Enrolment.enrol(e, session.repo.self.uid) }
            session.repo.server = cfg; session.repo.setKv("callsign", e.user); session.engine.identityChanged(); session.engine.connectServer()
            app.localized(lang, R.string.server_configured).format(e.host)
        } catch (ex: Exception) { app.localized(lang, R.string.enrol_failed) + (ex.message?.let { "\n($it)" } ?: "") }
    }
    return app.localized(lang, R.string.qr_not_read)
}

/** The demo mission joined in the tour by "scanning" a QR code that is shown on this same screen. */
fun demoMission() = Mission("demo", "OP DEMO", Base64.getEncoder().encodeToString(ByteArray(32) { 7 }), Role.EDITOR)
fun demoItems(uid: String): List<Item> {
    val t = System.currentTimeMillis()
    fun p(lat: Double, lon: Double) = app.pquest.coords.LatLon(lat, lon)
    return listOf(
        Item("demo-1", "demo", Kind.MARKER, Sidc.make('3', "10", "121100", "14"), "1 PL", "", listOf(p(20.050, 64.060)), "#000000", t, "demo"),
        Item("demo-2", "demo", Kind.MARKER, Sidc.make('6', "10", "121100", "12"), "EN SEC", "", listOf(p(20.085, 64.095)), "#000000", t, "demo"),
        Item("demo-3", "demo", Kind.LINE, Sidc.make('3', "25", "140300"), "RED", "", listOf(p(20.060, 64.030), p(20.068, 64.070), p(20.060, 64.110)), "#000000", t, "demo"),
        Item("demo-4", "demo", Kind.AREA, Sidc.make('3', "25", "151700"), "CAT", "", listOf(p(20.080, 64.085), p(20.092, 64.090), p(20.090, 64.105), p(20.078, 64.100)), "#000000", t, "demo"),
    )
}

@Composable
fun MissionsSheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    val scope = rememberCoroutineScope()
    val tick by session.repo.tick.collectAsState()
    val missions = remember(tick) { session.repo.missions() }
    val serverUp by session.engine.serverUp.collectAsState()
    val onServer by session.engine.serverMissions.collectAsState()
    var sharing by remember { mutableStateOf<Mission?>(null) }
    var shareRole by remember { mutableStateOf(Role.EDITOR) }
    var newName by remember { mutableStateOf("") }
    var publish by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val self = remember(tick) { session.repo.self }
    val demo = session.engine.demo

    Title(stringResource(R.string.missions))
    if (missions.isEmpty()) Text(stringResource(R.string.no_missions))
    for (m in missions) {
        SwitchRow("${m.name}  ·  " + stringResource(when (m.role) { Role.OWNER -> R.string.role_owner; Role.EDITOR -> R.string.role_editor; Role.VIEWER -> R.string.role_viewer }), m.visible) {
            session.repo.putMission(m.copy(visible = it)); ui.tour.toggles++
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (m.role.canEdit) Big(stringResource(R.string.draw_here), Modifier.weight(1f), filled = ui.activeMission == m.id) { ui.activeMission = if (ui.activeMission == m.id) "" else m.id }
            Big(stringResource(R.string.share), Modifier.weight(1f), filled = sharing?.id == m.id) { sharing = if (sharing?.id == m.id) null else m }
            Big(stringResource(R.string.leave), Modifier.weight(1f)) { session.repo.leaveMission(m.id); if (ui.activeMission == m.id) ui.activeMission = "" }
        }
        if (sharing?.id == m.id) {
            Choice(listOf(Role.EDITOR to stringResource(R.string.role_editor), Role.VIEWER to stringResource(R.string.role_viewer)), shareRole) { shareRole = it }
            QrImage(Links.mission(m, shareRole))
            Big(stringResource(R.string.send_package), Modifier.fillMaxWidth()) {
                shareFile(activity, "${m.name}.zip", "application/zip", DataPackage.write(m.name, session.repo.items().filter { it.mission == m.id }, self, m))
            }
        }
    }
    HorizontalDivider()
    Label(stringResource(R.string.join_mission))
    if (demo && missions.none { it.id == "demo" }) {
        // Tour: the code to scan is on this screen, so "scanning" reads it straight from the picture.
        val qr = remember { Links.mission(demoMission()) }
        QrImage(qr)
        Big(stringResource(R.string.scan_qr), Modifier.fillMaxWidth().tourTarget(ui, "scan"), filled = true) {
            scope.launch {
                status = handleLink(app, ui, session, withContext(Dispatchers.Default) { decodeQr(qrBitmap(qr)) })
                demoItems(self.uid).forEach { session.repo.putRemote(it) }
            }
        }
    } else QrScanButton(activity, stringResource(R.string.scan_qr), Modifier.fillMaxWidth()) { t -> scope.launch { status = handleLink(app, ui, session, t) } }
    OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.paste_link)) })
    if (link.startsWith("pquest://")) Big(stringResource(R.string.join), Modifier.fillMaxWidth()) { scope.launch { status = handleLink(app, ui, session, link); link = "" } }
    if (serverUp) {
        LaunchedEffect(Unit) { session.engine.refreshServerMissions() }
        for (name in onServer.filter { n -> missions.none { it.name == n } }) Big(stringResource(R.string.join_from_server, name), Modifier.fillMaxWidth()) {
            session.engine.join(Mission("srv:$name", name, Base64.getEncoder().encodeToString(MeshCrypto.newKey()), Role.EDITOR, onServer = true))
        }
    }
    Text(status ?: stringResource(R.string.join_hint))
    HorizontalDivider()
    Label(stringResource(R.string.new_mission))
    OutlinedTextField(newName, { newName = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.name)) })
    if (serverUp) SwitchRow(stringResource(R.string.publish_on_server), publish) { publish = it }
    Big(stringResource(R.string.create), Modifier.fillMaxWidth(), enabled = newName.isNotBlank() && missions.none { it.name == newName.trim() }) {
        val name = newName.trim()
        val m = Mission(if (publish) "srv:$name" else UUID.randomUUID().toString(), name, Base64.getEncoder().encodeToString(MeshCrypto.newKey()), Role.OWNER, onServer = publish && serverUp)
        session.engine.join(m); ui.activeMission = m.id; newName = ""
    }
    val conflicts = remember(tick) { session.repo.conflicts() }
    if (conflicts.isNotEmpty()) {
        HorizontalDivider()
        Label(stringResource(R.string.conflict_log))
        for (c in conflicts.take(20)) Text(stringResource(R.string.conflict_line, Dtg.format(c.time, app.settings.zulu), c.name.ifBlank { c.uid.take(8) }, c.keptAuthor.takeLast(6), c.droppedAuthor.takeLast(6)), fontSize = 14.sp)
    }
}

@Composable
fun MeshSheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    val radio = session.engine.radio
    val connected by radio.connected.collectAsState(); val name by radio.radioName.collectAsState()
    val found by radio.found.collectAsState(); val nodes by radio.nodes.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    val perms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.all { it }) { radio.scan(true); scanning = true } }
    DisposableEffect(Unit) { onDispose { if (scanning) radio.scan(false) } }
    val tick by session.repo.tick.collectAsState()
    val paired = remember(tick) { session.repo.kv("radio") }

    Title(stringResource(R.string.mesh_radio))
    Tip(app, "mesh", R.string.tip_mesh)
    LinkStatus(session)
    Text(if (connected) stringResource(R.string.radio_connected, name ?: "") else if (paired != null) stringResource(R.string.radio_waiting, name ?: paired) else stringResource(R.string.radio_none), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    if (remember(tick) { session.repo.missions() }.isEmpty()) Text(stringResource(R.string.mesh_needs_mission), color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Big(stringResource(if (scanning) R.string.stop else R.string.find_radios), Modifier.weight(1f), filled = !scanning) {
            if (scanning) { radio.scan(false); scanning = false } else ask.launch(perms)
        }
        if (paired != null) Big(stringResource(R.string.forget_radio), Modifier.weight(1f)) { radio.disconnect(); session.repo.setKv("radio", null) }
    }
    for (f in found) Big("${f.name}\n${f.address}", Modifier.fillMaxWidth()) {
        radio.scan(false); scanning = false; session.repo.setKv("radio", f.address)
        if (!app.settings.dark) radio.connect(f.address)
    }
    Label(stringResource(R.string.nodes_heard, nodes.size))
    val now = System.currentTimeMillis()
    for (n in nodes.sortedByDescending { it.lastHeard }) {
        val age = if (n.lastHeard <= 0) "—" else ((now - n.lastHeard) / 60000).coerceAtLeast(0).let { stringResource(R.string.minutes_ago, it.toInt()) }
        Text("${n.name}  ·  $age  ·  " + (n.hops?.let { stringResource(R.string.hops, it) } ?: "—"), fontSize = 16.sp)
    }
}

@Composable
fun ServerSheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    val scope = rememberCoroutineScope()
    val tick by session.repo.tick.collectAsState()
    val cfg = remember(tick) { session.repo.server }
    val up by session.engine.serverUp.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    val lang = app.settings.lang
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = try {
                val bytes = withContext(Dispatchers.IO) { activity.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                importAny(app, ui, session, displayName(activity, uri).let { if (it.endsWith(".zip", true)) it else "$it.zip" }, bytes)
            } catch (e: Exception) { activity.localized(lang, R.string.overlay_import_failed) }
        }
    }
    Title(stringResource(R.string.server))
    Tip(app, "server", R.string.tip_server)
    Text(
        if (cfg == null) stringResource(R.string.server_none) else "${cfg.host}:${cfg.port}  ·  " + stringResource(if (up) R.string.connected else R.string.not_connected),
        fontWeight = FontWeight.Bold, fontSize = 17.sp,
    )
    Big(stringResource(R.string.import_connection), Modifier.fillMaxWidth()) { picker.launch(arrayOf("*/*")) }
    QrScanButton(activity, stringResource(R.string.scan_enrol_qr), Modifier.fillMaxWidth()) { t -> scope.launch { status = handleLink(app, ui, session, t) } }
    status?.let { Text(it) }
    if (cfg != null) Big(stringResource(R.string.remove_server), Modifier.fillMaxWidth()) { session.repo.server = null; session.engine.connectServer() }
    Label(stringResource(R.string.pli_interval))
    Choice(listOf("10", "20", "30", "60").map { it to "$it s" }, app.settings.pliSeconds) { app.settings.pliSeconds = it; session.engine.pliSeconds = it.toInt() }
    Identity(session)
}
