package app.pquest

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pquest.coords.Dtg
import app.pquest.data.CustomSymbol
import app.pquest.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun SecuritySheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("") } // "", "pin1", "pin2", "duress"
    var first by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var hasDuress by remember { mutableStateOf(Vault.hasDuress(app)) }
    var hasBio by remember { mutableStateOf(Vault.hasBiometric(app)) }
    val done = stringResource(R.string.done); val mismatch = stringResource(R.string.pin_mismatch)
    Title(stringResource(R.string.security))
    Tip(app, "security", R.string.tip_security)
    SwitchRow(stringResource(R.string.go_dark), app.settings.dark) { app.setDark(it) }
    Text(stringResource(R.string.dark_explain))
    Big(stringResource(R.string.lock_now), Modifier.fillMaxWidth(), filled = true) { ui.sheet = Sheet.NONE; app.locked = true }
    if (session.duress) return // the duress profile cannot change PINs or reveal that another profile exists
    when (step) {
        "pin1" -> PinPad(stringResource(R.string.pin_create), note, false) { first = it; step = "pin2"; note = null }
        "pin2" -> PinPad(stringResource(R.string.pin_repeat), note, false) {
            if (it == first) scope.launch { withContext(Dispatchers.Default) { Vault.changePin(app, session.passphrase, it) }; step = ""; note = done } else { step = "pin1"; note = mismatch }
        }
        "duress" -> PinPad(stringResource(R.string.duress_set), note, false) { pin -> scope.launch { withContext(Dispatchers.Default) { Vault.setDuress(app, pin) }; hasDuress = true; step = ""; note = done } }
        else -> {
            Big(stringResource(R.string.change_pin), Modifier.fillMaxWidth()) { step = "pin1"; note = null }
            Big(stringResource(if (hasDuress) R.string.duress_remove else R.string.duress_add), Modifier.fillMaxWidth()) {
                if (hasDuress) { Vault.setDuress(app, null); hasDuress = false } else { step = "duress"; note = null }
            }
            Text(stringResource(R.string.duress_explain))
            Big(stringResource(if (hasBio) R.string.biometric_off else R.string.biometric_on), Modifier.fillMaxWidth()) {
                if (hasBio) { Vault.clearBiometric(app); hasBio = false }
                else biometric(activity, runCatching { Vault.biometricEncryptCipher() }.getOrNull()) { c -> Vault.storeBiometric(app, c, session.passphrase); hasBio = true }
            }
            note?.let { Text(it, fontWeight = FontWeight.Bold) }
        }
    }
    HorizontalDivider()
    Text(stringResource(R.string.wipe_explain))
    HoldButton(stringResource(R.string.wipe_hold), Modifier.fillMaxWidth()) { app.wipe() }
}

/** Admin mode, behind its own PIN: map sources, screenshots, report templates, unit signs, logs. */
@Composable
fun AdminSheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    val s = app.settings
    var error by remember { mutableStateOf<String?>(null) }
    val wrong = stringResource(R.string.pin_wrong)
    Title(stringResource(R.string.admin_mode))
    if (!ui.admin) {
        PinPad(stringResource(if (Vault.hasAdmin(app)) R.string.admin_pin_enter else R.string.admin_pin_create), error, false) { pin ->
            if (!Vault.hasAdmin(app)) { Vault.setAdmin(app, pin); ui.admin = true } else if (Vault.checkAdmin(app, pin)) ui.admin = true else error = wrong
        }
        return
    }
    SwitchRow(stringResource(R.string.online_maps), s.online) { s.online = it }
    Text(stringResource(R.string.online_warning))
    if (s.online) {
        UrlField(stringResource(R.string.url_topo), s.urlTopo) { s.urlTopo = it }
        UrlField(stringResource(R.string.url_satellite), s.urlSatellite) { s.urlSatellite = it }
        UrlField(stringResource(R.string.url_dem), s.urlDem) { s.urlDem = it }
    }
    SwitchRow(stringResource(R.string.block_screenshots), s.secure) { s.secure = it }
    HorizontalDivider()
    Label(stringResource(R.string.quick_messages))
    var quick by remember { mutableStateOf(session.repo.quick) }
    quick.forEachIndexed { i, q ->
        OutlinedTextField(q, { v -> quick = quick.toMutableList().also { it[i] = v.take(60) }; session.repo.quick = quick.map { it.ifBlank { "—" } } }, Modifier.fillMaxWidth(), singleLine = true)
    }
    Big(stringResource(R.string.reset_defaults), Modifier.fillMaxWidth()) { quick = session.repo.defaultQuick; session.repo.quick = quick }
    HorizontalDivider()
    Label(stringResource(R.string.unit_signs))
    Text(stringResource(R.string.unit_signs_hint))
    var signs by remember { mutableStateOf(session.repo.customSymbols) }
    fun store(v: List<CustomSymbol>) { signs = v; session.repo.customSymbols = v.filter { it.name.isNotBlank() && it.label.isNotBlank() } }
    signs.forEachIndexed { i, c ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(c.label, { v -> store(signs.toMutableList().also { it[i] = c.copy(label = v.take(5).uppercase()) }) }, Modifier.width(96.dp), singleLine = true)
            OutlinedTextField(c.name, { v -> store(signs.toMutableList().also { it[i] = c.copy(name = v.take(30)) }) }, Modifier.weight(1f), singleLine = true)
            Big("✕") { store(signs - c) }
        }
    }
    Big(stringResource(R.string.add_sign), Modifier.fillMaxWidth()) { signs = signs + CustomSymbol(java.util.UUID.randomUUID().toString().take(6), "", "") }
    HorizontalDivider()
    val crashes = remember { File(app.filesDir, "crash").listFiles()?.sortedByDescending { it.name }.orEmpty() }
    Big(stringResource(R.string.export_crash_logs, crashes.size), Modifier.fillMaxWidth(), enabled = crashes.isNotEmpty()) {
        shareFile(activity, "pquest-crash-logs.txt", "text/plain", crashes.take(20).joinToString("\n\n=====\n\n") { it.readText() }.toByteArray())
    }
    Big(stringResource(R.string.export_feedback), Modifier.fillMaxWidth(), enabled = session.repo.kv("feedback") != null) {
        shareFile(activity, "pquest-feedback.json", "application/json", (session.repo.kv("feedback") ?: "[]").toByteArray())
    }
    Big(stringResource(R.string.leave_admin), Modifier.fillMaxWidth(), filled = true) { ui.admin = false; ui.sheet = Sheet.NONE }
}

@Composable
fun HelpSheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    Title(stringResource(R.string.help))
    Big(stringResource(R.string.replay_tour), Modifier.fillMaxWidth(), filled = true) { ui.tour.start(app, session, null); ui.sheet = Sheet.NONE }
    Label(stringResource(R.string.replay_step))
    TOUR_TITLES.forEachIndexed { i, t ->
        if (i in 2..9) Big("${i + 1}. " + stringResource(t), Modifier.fillMaxWidth()) { ui.tour.start(app, session, i); ui.sheet = Sheet.NONE }
    }
    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (lang in listOf("en" to "English", "ur" to "اردو")) Big(stringResource(R.string.quick_card) + " · " + lang.second, Modifier.weight(1f)) {
            shareFile(activity, "pquest-quick-reference-${lang.first}.pdf", "application/pdf", quickReference(activity, lang.first))
        }
    }
    Big(stringResource(R.string.feedback), Modifier.fillMaxWidth()) { ui.sheet = Sheet.FEEDBACK }
}

/** Field-trial feedback, kept on the device and exported by an admin. */
@Composable
fun FeedbackSheet(app: App, activity: ComponentActivity, ui: Ui, session: Session) {
    var rating by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    Title(stringResource(R.string.feedback))
    Label(stringResource(R.string.feedback_rating))
    Choice((1..5).map { it to it.toString() }, rating) { rating = it }
    OutlinedTextField(text, { text = it.take(1000) }, Modifier.fillMaxWidth().heightIn(min = 140.dp), label = { Text(stringResource(R.string.feedback_text)) })
    Big(stringResource(R.string.save), Modifier.fillMaxWidth(), enabled = rating > 0 || text.isNotBlank(), filled = true) {
        val all = JSONArray(session.repo.kv("feedback") ?: "[]")
        all.put(JSONObject().put("time", Dtg.format(System.currentTimeMillis(), true)).put("rating", rating).put("text", text.trim()).put("version", BuildInfo.VERSION))
        session.repo.setKv("feedback", all.toString()); ui.sheet = Sheet.NONE
    }
}
