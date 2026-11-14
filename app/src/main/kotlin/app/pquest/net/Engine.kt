package app.pquest.net

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.pquest.R
import app.pquest.coords.LatLon
import app.pquest.cot.*
import app.pquest.data.Repo
import app.pquest.mesh.MeshCrypto
import app.pquest.mesh.MeshRadio
import app.pquest.missions.Mission
import app.pquest.missions.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Base64

/**
 * The CoT engine: the only part of the app that talks to the outside. Every feature hands it events; it sends them
 * over the best link that is up (TAK Server, then local Wi-Fi multicast, then LoRa mesh) and queues them when none is.
 */
class Engine(private val ctx: Context, val repo: Repo, private val fix: () -> android.location.Location?) {
    val contacts = MutableStateFlow<Map<String, Contact>>(emptyMap())
    val alerts = MutableStateFlow<List<CotEvent.Alert>>(emptyList())
    val serverUp = MutableStateFlow(false)
    val serverMissions = MutableStateFlow<List<String>>(emptyList())
    val wifi = WifiLink(ctx) { receive(it, null, null) }
    val radio = MeshRadio(ctx) { payload, _, hops -> receiveMesh(payload, hops) }

    /** Go dark: nothing is transmitted and every network link is closed. */
    @Volatile var dark = false
        private set
    /** Demo mode (the familiarisation tour): nothing leaves the device. */
    @Volatile var demo = false
        set(v) { if (field != v) { field = v; if (started) { if (silent) closeLinks() else { openLinks(); scope.launch { flush() } } } } }
    @Volatile private var started = false
    @Volatile var pliSeconds = 10

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var tak: TakLink? = null
    @Volatile private var self = repo.self
    private val silent get() = dark || demo

    fun start(goDark: Boolean) {
        dark = goDark; started = true
        if (!silent) openLinks()
        scope.launch { while (true) { delay(pliSeconds.coerceIn(10, 60) * 1000L); if (!silent) sendPli() } }
        scope.launch { while (true) { delay(8000); flush() } }
    }

    fun stop() { closeLinks(); radio.disconnect(); scope.cancel() }

    fun setDark(on: Boolean) {
        dark = on
        if (silent) closeLinks() else { openLinks(); scope.launch { flush() } }
    }

    private fun openLinks() {
        wifi.start()
        connectServer()
        repo.kv("radio")?.let { radio.connect(it) }
    }

    private fun closeLinks() {
        tak?.stop(); tak = null; serverUp.value = false
        wifi.stop(); radio.disconnect()
    }

    fun connectServer() {
        tak?.stop(); tak = null; serverUp.value = false
        if (silent) return
        val cfg = repo.server ?: return
        tak = runCatching {
            TakLink(cfg, { receive(it, null, null) }) { up ->
                serverUp.value = up
                if (up) scope.launch { sendPli(); syncMissions(); flush() }
            }.also { it.start() }
        }.getOrNull()
    }

    fun identityChanged() { self = repo.self }

    // ---- sending -----------------------------------------------------------------------------------------

    private fun meshKey(missionId: String): ByteArray? {
        val all = repo.missions()
        val m = all.firstOrNull { it.id == missionId } ?: all.firstOrNull() ?: return null
        return runCatching { Base64.getDecoder().decode(m.key) }.getOrNull()?.takeIf { it.size == 32 }
    }

    /** Sends on the best available link, or on every link when [everyLink]. Returns true if it left the device. */
    private fun transmit(xml: String, compact: ByteArray?, missionId: String, everyLink: Boolean, isChat: Boolean): Boolean {
        if (silent) return false
        var sent = false
        if (tak?.send(xml) == true) sent = true
        if ((!sent || everyLink) && wifi.up.value && wifi.send(xml, isChat)) sent = true
        if ((!sent || everyLink) && compact != null && radio.connected.value) {
            val key = meshKey(missionId)
            if (key != null && radio.send(MeshCrypto.seal(key, compact))) sent = true
        }
        return sent
    }

    private fun missionName(id: String) = repo.missions().firstOrNull { it.id == id }?.name

    fun sendPli() {
        val l = fix() ?: return
        val c = Contact(self.uid, self.callsign, self.team, self.role, LatLon(l.latitude, l.longitude), if (l.hasAltitude()) l.altitude else null,
            if (l.hasBearing()) l.bearing.toDouble() else null, if (l.hasSpeed()) l.speed.toDouble() else null, System.currentTimeMillis())
        val e = CotEvent.Pli(c)
        transmit(Cot.encode(e, self), Compact.encode(e), "", false, false)
    }

    /** Stores the message as queued, then tries to send; store-and-forward delivers it later if no link is up. */
    fun sendChat(kind: RoomKind, room: String, label: String, text: String, loc: LatLon?, attachment: String? = null): ChatMsg {
        val m = ChatMsg(Compact.newId(), kind, room, self.uid, self.callsign, text, System.currentTimeMillis(), loc, label)
        repo.addMessage(m, MsgState.QUEUED, true, attachment)
        if (demo) { repo.setState(m.id, MsgState.SENT); return m } // tour: shown as sent, nothing transmitted
        val e = CotEvent.Chat(m)
        val missionId = if (kind == RoomKind.MISSION) repo.missions().firstOrNull { it.name == label }?.id ?: "" else ""
        val xml = Cot.encode(e, self); val compact = Compact.encode(e)
        if (transmit(xml, compact, missionId, false, true)) repo.setState(m.id, MsgState.SENT) else repo.enqueue(m.id, xml, compact, missionId, false)
        return m
    }

    /** Emergency alert: position and alert to everyone, over every link that is up. */
    fun sendAlert(cancel: Boolean, pos: LatLon) {
        val e = CotEvent.Alert(self.uid, self.callsign, pos, System.currentTimeMillis(), cancel)
        alerts.value = alerts.value.filter { it.uid != self.uid } + if (cancel) emptyList() else listOf(e)
        if (demo) return
        val xml = Cot.encode(e, self); val compact = Compact.encode(e)
        if (!transmit(xml, compact, "", true, false)) repo.enqueue(null, xml, compact, "", true)
    }

    /** A local edit to a map item. Viewers of a mission may not change it. */
    fun saveItem(item: Item): Boolean {
        val role = repo.missions().firstOrNull { it.id == item.mission }?.role
        if (item.mission.isNotEmpty() && role?.canEdit != true) return false
        repo.putLocal(item.copy(updated = maxOf(System.currentTimeMillis(), item.updated), author = self.uid))
        scope.launch { pushDirty() }
        return true
    }

    private fun pushDirty() {
        if (silent || (tak == null || !serverUp.value) && !wifi.up.value) return
        for (i in repo.dirty()) {
            if (i.mission.isEmpty()) { repo.clean(i.uid, i.updated); continue } // personal overlay stays on the device
            if (transmit(Cot.encode(CotEvent.ItemChange(i), self, missionName(i.mission)), null, i.mission, false, false)) repo.clean(i.uid, i.updated)
        }
    }

    fun flush() {
        if (silent) return
        for (o in repo.outbox()) {
            if (!transmit(o.xml, o.compact, o.mission, o.everyLink, o.msgId != null)) break
            repo.dequeue(o.n); o.msgId?.let { repo.setState(it, MsgState.SENT) }
        }
        pushDirty()
    }

    fun markRead(kind: RoomKind, room: String) {
        val unread = repo.markRead(kind, room)
        if (kind == RoomKind.DIRECT && !silent) scope.launch {
            for ((id, from) in unread) CotEvent.Receipt(id, MsgState.READ, self.uid).let { transmit(Cot.encode(it, self).replace("</detail>", "<marti><dest uid=\"$from\"/></marti></detail>"), Compact.encode(it), "", false, true) }
        }
    }

    // ---- missions ----------------------------------------------------------------------------------------

    private fun marti(): Marti? = tak?.let { t -> repo.server?.let { Marti(it.host, t.tls.http()) } }

    /** Subscribes to every joined server mission and merges its current contents. */
    fun syncMissions() {
        val api = marti() ?: return
        api.missions()?.let { serverMissions.value = it }
        for (m in repo.missions().filter { it.onServer }) {
            api.subscribe(m.name, self.uid)
            api.events(m.name)?.forEach { xml -> (Cot.decode(xml) as? CotEvent.ItemChange)?.let { repo.putRemote(it.item.copy(mission = m.id)) } }
        }
        pushDirty()
    }

    /** Attachments travel over the server only; they are never sent over mesh. Returns the link, or null if blocked. */
    fun upload(name: String, bytes: ByteArray): String? = if (silent || !serverUp.value) null else marti()?.upload(name, bytes, self.uid)
    fun download(url: String): ByteArray? = if (silent) null else marti()?.download(url)

    fun refreshServerMissions() = scope.launch { marti()?.missions()?.let { serverMissions.value = it } }

    fun join(m: Mission) {
        repo.putMission(m)
        if (m.onServer) scope.launch { if (m.role == Role.OWNER) marti()?.create(m.name, self.uid); syncMissions() }
    }

    // ---- receiving ---------------------------------------------------------------------------------------

    private fun receiveMesh(packet: ByteArray, hops: Int?) {
        val keys = repo.missions().mapNotNull { m -> runCatching { Base64.getDecoder().decode(m.key) }.getOrNull() }
        val (_, plain) = MeshCrypto.open(keys, packet) ?: return
        val known = contacts.value.keys.associateBy { Compact.hash(it) } + (Compact.hash(self.uid) to self.uid)
        val e = Compact.decode(plain) { known[it] } ?: return
        handle(e, null, hops)
    }

    private fun receive(xml: String, missionId: String?, hops: Int?) {
        if (silent) return
        val e = Cot.decode(xml) ?: return
        handle(e, Cot.missionDest(xml), hops)
    }

    private fun handle(e: CotEvent, missionDest: String?, hops: Int?) {
        when (e) {
            is CotEvent.Pli -> if (e.contact.uid != self.uid) contacts.value = contacts.value + (e.contact.uid to e.contact.copy(hops = hops))
            is CotEvent.Chat -> {
                val m = e.msg
                if (m.fromUid == self.uid) return
                val joined = repo.missions()
                val stored = when (m.roomKind) {
                    RoomKind.DIRECT -> if (m.room == self.uid) m.copy(room = m.fromUid, roomLabel = m.fromCallsign) else return
                    RoomKind.MISSION -> if (joined.any { it.name == m.roomLabel }) m else return
                    RoomKind.TEAM -> if (m.roomLabel == self.team) m else return
                    RoomKind.ALL -> m
                }
                repo.addMessage(stored, MsgState.DELIVERED, false)
                notify(m.fromCallsign, m.text)
                if (m.roomKind == RoomKind.DIRECT && !silent) CotEvent.Receipt(m.id, MsgState.DELIVERED, self.uid).let {
                    transmit(Cot.encode(it, self).replace("</detail>", "<marti><dest uid=\"${m.fromUid}\"/></marti></detail>"), Compact.encode(it), "", false, true)
                }
            }
            is CotEvent.Receipt -> repo.setState(e.msgId, e.state)
            is CotEvent.Alert -> if (e.uid != self.uid) {
                alerts.value = alerts.value.filter { it.uid != e.uid } + if (e.cancel) emptyList() else listOf(e)
                if (!e.cancel) notify(ctx.getString(R.string.emergency), e.callsign, true)
            }
            is CotEvent.ItemChange -> {
                val joined = repo.missions()
                val mission = joined.firstOrNull { it.id == e.item.mission } ?: missionDest?.let { n -> joined.firstOrNull { it.name == n } }
                // Items addressed to a mission we have not joined are ignored; un-addressed ones go to the personal overlay.
                if (e.item.mission.isNotEmpty() && mission == null) return
                if (e.item.author == self.uid && repo.item(e.item.uid) != null) return
                repo.putRemote(e.item.copy(mission = mission?.id ?: ""))
            }
        }
    }

    private fun notify(title: String, text: String, urgent: Boolean = false) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = if (urgent) "alerts" else "messages"
        nm.createNotificationChannel(NotificationChannel(ch, ch, if (urgent) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(if (urgent) 911 else 2, android.app.Notification.Builder(ctx, ch).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text).setAutoCancel(true).build())
    }
}
