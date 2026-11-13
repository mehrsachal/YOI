package app.pquest.cot

import app.pquest.coords.LatLon
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.xml.parsers.DocumentBuilderFactory

/** Cursor-on-Target XML, as spoken by TAK Server, ATAK, WinTAK and iTAK. */
object Cot {
    /** 2525D land-unit entity code to the 2525C function id used in CoT types (a-f-G-<function>). */
    private val FUNCTION = linkedMapOf(
        "121100" to "U-C-I", "120500" to "U-C-A", "121300" to "U-C-R", "130300" to "U-C-F", "130800" to "U-C-F-M",
        "140700" to "U-C-E", "130100" to "U-C-D", "120400" to "U-C-A-A", "120600" to "U-C-V-R", "111000" to "U-U-S",
        "161300" to "U-S-M", "160000" to "U-S", "161100" to "U-S-X", "163600" to "U-S-T", "141200" to "U-U-L-M",
        "121700" to "F", "110000" to "U-H", "121500" to "U-C-I-Z", "121000" to "U-C", "121200" to "U-C-R-O",
    )
    private val AFF = mapOf('3' to 'f', '6' to 'h', '4' to 'n', '1' to 'u', '2' to 'a', '5' to 's', '0' to 'p')

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    fun iso(ms: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.MILLIS))
    private fun time(s: String) = runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    private fun event(uid: String, type: String, how: String, t: Long, staleMs: Long, p: LatLon?, alt: Double?, detail: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" uid=\"${esc(uid)}\" type=\"$type\" how=\"$how\" " +
            "time=\"${iso(t)}\" start=\"${iso(t)}\" stale=\"${iso(t + staleMs)}\">" +
            "<point lat=\"${p?.lat ?: 0.0}\" lon=\"${p?.lon ?: 0.0}\" hae=\"${alt ?: 9999999.0}\" ce=\"9999999.0\" le=\"9999999.0\"/>" +
            "<detail>$detail</detail></event>"

    fun typeOf(item: Item): String = when {
        item.kind != Kind.MARKER -> "u-d-f"
        Sidc.isStandard(item.sidc) && Sidc.symbolSet(item.sidc) == "10" ->
            "a-${AFF[Sidc.affiliation(item.sidc)] ?: 'u'}-G-${FUNCTION[Sidc.entity(item.sidc)] ?: "U"}"
        Sidc.isStandard(item.sidc) && Sidc.symbolSet(item.sidc) in setOf("15", "20") ->
            "a-${AFF[Sidc.affiliation(item.sidc)] ?: 'u'}-G-${if (Sidc.symbolSet(item.sidc) == "15") "E" else "I"}"
        else -> "b-m-p-s-m"
    }

    private fun argb(hex: String) = (0xFF000000 or hex.removePrefix("#").toLong(16)).toInt()

    /** Mission destination so that TAK Server files the event under the mission and forwards it to subscribers. */
    private fun dest(missionName: String?) = if (missionName.isNullOrEmpty()) "" else "<marti><dest mission=\"${esc(missionName)}\"/></marti>"

    fun encode(e: CotEvent, self: Self, missionName: String? = null, now: Long = System.currentTimeMillis()): String = when (e) {
        is CotEvent.Pli -> e.contact.let { c ->
            event(
                c.uid, "a-f-G-U-C", "m-g", c.time, 120_000, c.pos, c.alt,
                "<takv device=\"Android\" platform=\"P-Quest\" os=\"Android\" version=\"1\"/>" +
                    "<contact callsign=\"${esc(c.callsign)}\" endpoint=\"*:-1:stcp\"/><uid Droid=\"${esc(c.callsign)}\"/>" +
                    "<__group name=\"${esc(c.team)}\" role=\"${esc(c.role)}\"/>" +
                    "<track course=\"${c.course ?: 0.0}\" speed=\"${c.speed ?: 0.0}\"/>",
            )
        }
        is CotEvent.Chat -> e.msg.let { m ->
            val roomId = if (m.roomKind == RoomKind.DIRECT) m.room else m.roomLabel
            val marti = when (m.roomKind) {
                RoomKind.DIRECT -> "<marti><dest uid=\"${esc(m.room)}\"/></marti>"
                RoomKind.MISSION -> dest(m.roomLabel)
                else -> ""
            }
            event(
                "GeoChat.${m.fromUid}.$roomId.${m.id}", "b-t-f", "h-g-i-g-o", m.time, 86_400_000, m.loc, null,
                "<__chat parent=\"${if (m.roomKind == RoomKind.TEAM) "TeamGroups" else "RootContactGroup"}\" groupOwner=\"false\" messageId=\"${m.id}\" " +
                    "chatroom=\"${esc(m.roomLabel)}\" id=\"${esc(roomId)}\" senderCallsign=\"${esc(m.fromCallsign)}\">" +
                    "<chatgrp uid0=\"${esc(m.fromUid)}\" uid1=\"${esc(roomId)}\" id=\"${esc(roomId)}\"/></__chat>" +
                    "<link uid=\"${esc(m.fromUid)}\" type=\"a-f-G-U-C\" relation=\"p-p\"/>" +
                    "<remarks source=\"BAO.F.ATAK.${esc(m.fromUid)}\" to=\"${esc(roomId)}\" time=\"${iso(m.time)}\">${esc(m.text)}</remarks>" +
                    "<__pquest room=\"${m.roomKind}\" loc=\"${m.loc != null}\"/>$marti",
            )
        }
        is CotEvent.Receipt -> event(
            e.msgId, if (e.state == MsgState.READ) "b-t-f-r" else "b-t-f-d", "h-g-i-g-o", now, 86_400_000, null, null,
            "<__chatreceipt messageId=\"${e.msgId}\" senderCallsign=\"${esc(self.callsign)}\"/><link uid=\"${esc(e.fromUid)}\" type=\"a-f-G-U-C\" relation=\"p-p\"/>",
        )
        is CotEvent.Alert -> event(
            "${e.uid}-9-1-1", if (e.cancel) "b-a-o-can" else "b-a-o-tbl", "m-g", e.time, 600_000, e.pos, null,
            "<link uid=\"${esc(e.uid)}\" type=\"a-f-G-U-C\" relation=\"p-p\"/><contact callsign=\"${esc(e.callsign)}-Alert\"/>" +
                "<emergency ${if (e.cancel) "cancel=\"true\"" else "type=\"911 Alert\""}>${esc(e.callsign)}</emergency>",
        )
        is CotEvent.ItemChange -> e.item.let { i ->
            val hint = "<__pquest kind=\"${i.kind}\" mission=\"${esc(i.mission)}\" author=\"${esc(i.author)}\" updated=\"${i.updated}\"/>"
            if (i.deleted) event(
                i.uid, "t-x-d-d", "h-g-i-g-o", i.updated, 60_000, i.points.firstOrNull(), null,
                "<link uid=\"${esc(i.uid)}\" relation=\"none\" type=\"none\"/><__forcedelete/>$hint${dest(missionName)}",
            ) else {
                val shape = if (i.kind == Kind.MARKER) "" else
                    i.points.joinToString("") { "<link point=\"${it.lat},${it.lon}\"/>" } +
                        "<strokeColor value=\"${argb(i.color)}\"/><strokeWeight value=\"3.0\"/>" +
                        (if (i.kind == Kind.AREA) "<fillColor value=\"${argb(i.color) and 0x33FFFFFF}\"/>" else "")
                event(
                    i.uid, typeOf(i), "h-g-i-g-o", i.updated, 365L * 86_400_000, centre(i.points), null,
                    "<contact callsign=\"${esc(i.name)}\"/><remarks>${esc(i.remarks)}</remarks><color argb=\"${argb(i.color)}\"/>" +
                        "<__milsym id=\"${esc(i.sidc)}\"/>$shape$hint${dest(missionName)}",
                )
            }
        }
    }

    fun centre(pts: List<LatLon>): LatLon? =
        if (pts.isEmpty()) null else LatLon(pts.sumOf { it.lat } / pts.size, pts.sumOf { it.lon } / pts.size)

    private fun Element.child(name: String): Element? {
        val n = getElementsByTagName(name)
        return if (n.length > 0) n.item(0) as Element else null
    }

    private fun Element.attr(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null

    private val factory = DocumentBuilderFactory.newInstance().apply {
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { isExpandEntityReferences = false }
    }

    private fun hex(argb: String?) = argb?.toLongOrNull()?.let { "#%06X".format(java.util.Locale.ROOT, it and 0xFFFFFF) }

    /** Returns null for event types P-Quest does not use. Never throws on malformed input. */
    fun decode(xml: String): CotEvent? = runCatching {
        val ev = synchronized(factory) { factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray())) }.documentElement
        if (ev.tagName != "event") return null
        val uid = ev.getAttribute("uid"); val type = ev.getAttribute("type"); val t = time(ev.getAttribute("time"))
        val pt = ev.child("point")
        val pos = LatLon(pt?.attr("lat")?.toDoubleOrNull() ?: 0.0, pt?.attr("lon")?.toDoubleOrNull() ?: 0.0)
        val hae = pt?.attr("hae")?.toDoubleOrNull()?.takeIf { it < 9_000_000 }
        val detail = ev.child("detail")
        val hint = detail?.child("__pquest")
        val callsign = detail?.child("contact")?.attr("callsign") ?: ""
        when {
            type == "b-t-f" -> {
                val chat = detail?.child("__chat") ?: return null
                val from = detail.child("link")?.attr("uid") ?: uid.split(".").getOrElse(1) { "" }
                val roomId = chat.attr("id") ?: ""
                val label = chat.attr("chatroom") ?: roomId
                val kind = hint?.attr("room")?.let { runCatching { RoomKind.valueOf(it) }.getOrNull() } ?: when {
                    chat.attr("parent") == "TeamGroups" -> RoomKind.TEAM
                    label == "All Chat Rooms" -> RoomKind.ALL
                    detail.child("marti")?.child("dest")?.hasAttribute("mission") == true -> RoomKind.MISSION
                    roomId != label || roomId.startsWith("ANDROID-") -> RoomKind.DIRECT
                    else -> RoomKind.ALL
                }
                val hasLoc = hint?.attr("loc")?.toBoolean() ?: (pos.lat != 0.0 || pos.lon != 0.0)
                CotEvent.Chat(ChatMsg(
                    chat.attr("messageId") ?: uid.substringAfterLast('.'), kind, if (kind == RoomKind.DIRECT) roomId else label, from,
                    chat.attr("senderCallsign") ?: from, detail.child("remarks")?.textContent ?: "", t, if (hasLoc) pos else null, label,
                ))
            }
            type == "b-t-f-d" || type == "b-t-f-r" -> CotEvent.Receipt(
                detail?.child("__chatreceipt")?.attr("messageId") ?: uid, if (type == "b-t-f-r") MsgState.READ else MsgState.DELIVERED,
                detail?.child("link")?.attr("uid") ?: "",
            )
            type.startsWith("b-a-o") -> CotEvent.Alert(
                detail?.child("link")?.attr("uid") ?: uid.removeSuffix("-9-1-1"),
                detail?.child("emergency")?.textContent?.ifBlank { null } ?: callsign.removeSuffix("-Alert"), pos, t,
                type == "b-a-o-can" || detail?.child("emergency")?.attr("cancel") == "true",
            )
            type == "t-x-d-d" -> {
                val target = detail?.child("link")?.attr("uid") ?: uid
                CotEvent.ItemChange(Item(target, hint?.attr("mission") ?: "", Kind.MARKER, "", "", "", emptyList(), "#000000",
                    hint?.attr("updated")?.toLongOrNull() ?: t, hint?.attr("author") ?: "", deleted = true))
            }
            type.startsWith("a-") && (ev.getAttribute("how").startsWith("m-") || detail?.child("__group") != null || detail?.child("takv") != null) -> {
                val g = detail?.child("__group"); val tr = detail?.child("track")
                CotEvent.Pli(Contact(uid, callsign.ifBlank { uid }, g?.attr("name") ?: "", g?.attr("role") ?: "", pos, hae,
                    tr?.attr("course")?.toDoubleOrNull(), tr?.attr("speed")?.toDoubleOrNull(), t))
            }
            type.startsWith("a-") || type.startsWith("b-m-p") || type.startsWith("u-d-") -> {
                val links = detail?.getElementsByTagName("link")
                val pts = ArrayList<LatLon>()
                if (links != null) for (i in 0 until links.length) (links.item(i) as Element).attr("point")?.split(",")?.let {
                    if (it.size >= 2) pts.add(LatLon(it[0].toDouble(), it[1].toDouble()))
                }
                val closed = pts.size > 3 && pts.first() == pts.last()
                val kind = hint?.attr("kind")?.let { runCatching { Kind.valueOf(it) }.getOrNull() } ?: when {
                    pts.isEmpty() -> Kind.MARKER
                    closed || detail?.child("fillColor") != null -> Kind.AREA
                    else -> Kind.LINE
                }
                val sidc = detail?.child("__milsym")?.attr("id") ?: if (type.startsWith("a-")) {
                    val aff = AFF.entries.firstOrNull { it.value == type.getOrNull(2) }?.key ?: '1'
                    val fn = type.substringAfter("-G-", "")
                    Sidc.make(aff, "10", FUNCTION.entries.firstOrNull { it.value == fn }?.key ?: "000000")
                } else if (kind == Kind.MARKER) Sidc.POINT_OF_INTEREST else ""
                val color = hex(detail?.child("strokeColor")?.attr("value")) ?: hex(detail?.child("color")?.attr("argb")) ?: "#000000"
                CotEvent.ItemChange(Item(
                    uid, hint?.attr("mission") ?: "", kind, sidc, callsign, detail?.child("remarks")?.textContent ?: "",
                    if (kind == Kind.MARKER) listOf(pos) else if (closed) pts.dropLast(1) else pts, color,
                    hint?.attr("updated")?.toLongOrNull() ?: t, hint?.attr("author") ?: "",
                ))
            }
            else -> null
        }
    }.getOrNull()

    /** Name of the mission an incoming event was addressed to by TAK Server, if any. */
    fun missionDest(xml: String): String? = Regex("<dest[^>]*mission=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)
        ?.replace("&amp;", "&")

    /** Splits a TCP stream buffer into complete <event>…</event> documents; returns the unconsumed tail. */
    fun split(buffer: StringBuilder, out: (String) -> Unit) {
        while (true) {
            val end = buffer.indexOf("</event>")
            if (end < 0) break
            val start = buffer.indexOf("<event")
            if (start in 0 until end) out(buffer.substring(start, end + 8))
            buffer.delete(0, end + 8)
        }
        if (buffer.length > 1_000_000) buffer.setLength(0)
    }
}
