package app.pquest.cot

import app.pquest.coords.LatLon

enum class Kind { MARKER, LINE, AREA, ROUTE }

/**
 * One object on the map. [sidc] is a 20-digit MIL-STD-2525D / APP-6D code, "custom:<id>" for a unit-defined sign,
 * or empty for a plain route. [mission] is the owning mission id, or "" for the user's own overlay.
 */
data class Item(
    val uid: String, val mission: String, val kind: Kind, val sidc: String, val name: String, val remarks: String,
    val points: List<LatLon>, val color: String, val updated: Long, val author: String, val deleted: Boolean = false,
)

data class Contact(
    val uid: String, val callsign: String, val team: String, val role: String, val pos: LatLon, val alt: Double?,
    val course: Double?, val speed: Double?, val time: Long, val hops: Int? = null,
)

enum class RoomKind { ALL, MISSION, TEAM, DIRECT }
enum class MsgState { QUEUED, SENT, DELIVERED, READ }

/** [room] is "All Chat Rooms", a mission name, a team name, or (DIRECT) the other party's uid. */
data class ChatMsg(
    val id: String, val roomKind: RoomKind, val room: String, val fromUid: String, val fromCallsign: String,
    val text: String, val time: Long, val loc: LatLon?, val roomLabel: String = room,
)

sealed class CotEvent {
    data class Pli(val contact: Contact) : CotEvent()
    data class Chat(val msg: ChatMsg) : CotEvent()
    data class Receipt(val msgId: String, val state: MsgState, val fromUid: String) : CotEvent()
    data class Alert(val uid: String, val callsign: String, val pos: LatLon, val time: Long, val cancel: Boolean) : CotEvent()
    data class ItemChange(val item: Item) : CotEvent()
}

/** Who we are when we transmit. */
data class Self(val uid: String, val callsign: String, val team: String = "Cyan", val role: String = "Team Member")

object Sidc {
    const val POINT_OF_INTEREST = "10032500001313000000"
    fun affiliation(sidc: String) = sidc.getOrNull(3) ?: '3'
    fun withAffiliation(sidc: String, a: Char) = if (sidc.length >= 20) sidc.substring(0, 3) + a + sidc.substring(4) else sidc
    fun symbolSet(sidc: String) = sidc.substring(4, 6)
    fun entity(sidc: String) = sidc.substring(10, 16)
    fun echelon(sidc: String) = sidc.substring(8, 10)
    fun withEchelon(sidc: String, e: String) = if (sidc.length >= 20) sidc.substring(0, 8) + e + sidc.substring(10) else sidc
    fun make(affiliation: Char, set: String, entity: String, echelon: String = "00") = "100$affiliation${set}00$echelon${entity}0000"
    fun isStandard(sidc: String) = sidc.length >= 20 && sidc.all { it.isDigit() }
}
