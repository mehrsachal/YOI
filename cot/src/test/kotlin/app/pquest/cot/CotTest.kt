package app.pquest.cot

import app.pquest.coords.LatLon
import kotlin.test.*

class CotTest {
    private val self = Self("ANDROID-abc", "ALPHA 1")
    private val t = 1_794_548_700_000L

    @Test fun pliRoundTrip() {
        val c = Contact("ANDROID-abc", "ALPHA 1", "Cyan", "Team Lead", LatLon(20.0123456, 64.0654321), 812.0, 90.0, 1.5, t)
        val xml = Cot.encode(CotEvent.Pli(c), self)
        assertTrue(xml.contains("type=\"a-f-G-U-C\"") && xml.contains("how=\"m-g\""))
        assertEquals(c, (Cot.decode(xml) as CotEvent.Pli).contact)
    }

    @Test fun decodesAtakPli() {
        val xml = """<event version="2.0" uid="ANDROID-589520ccfcd20f01" type="a-f-G-U-C" how="h-e" time="2026-11-13T05:45:00.000Z" start="2026-11-13T05:45:00.000Z" stale="2026-11-13T05:51:15.000Z"><point lat="20.01" lon="64.02" hae="33.0" ce="3.2" le="9999999.0"/><detail><takv os="29" version="4.10" device="SAMSUNG" platform="ATAK-CIV"/><contact endpoint="*:-1:stcp" callsign="BRAVO &amp; 2"/><uid Droid="BRAVO"/><__group role="Team Member" name="Green"/><status battery="78"/><track course="140.5" speed="0.0"/></detail></event>"""
        val c = (Cot.decode(xml) as CotEvent.Pli).contact
        assertEquals("BRAVO & 2", c.callsign); assertEquals("Green", c.team); assertEquals(LatLon(20.01, 64.02), c.pos); assertEquals(140.5, c.course)
    }

    @Test fun markerRoundTripAndAtakType() {
        val item = Item("u1", "m1", Kind.MARKER, Sidc.make('6', "10", "121100", "15"), "EN <Coy>", "seen \"moving\"", listOf(LatLon(20.1, 64.1)), "#FF0000", t, "ANDROID-abc")
        val xml = Cot.encode(CotEvent.ItemChange(item), self, "OP DEMO")
        assertTrue(xml.contains("type=\"a-h-G-U-C-I\""), xml)
        assertEquals("OP DEMO", Cot.missionDest(xml))
        assertEquals(item, (Cot.decode(xml) as CotEvent.ItemChange).item)
    }

    @Test fun lineAreaAndDeleteRoundTrip() {
        val pts = listOf(LatLon(20.0, 64.0), LatLon(20.1, 64.1), LatLon(20.0, 64.2))
        for (k in listOf(Kind.LINE, Kind.AREA, Kind.ROUTE)) {
            val item = Item("u-$k", "", k, if (k == Kind.LINE) "10032500001403000000" else "", "PL RED", "", pts, "#0044FF", t, "a")
            val xml = Cot.encode(CotEvent.ItemChange(item), self)
            assertTrue(xml.contains("type=\"u-d-f\""))
            assertEquals(item, (Cot.decode(xml) as CotEvent.ItemChange).item)
        }
        val del = Item("u9", "m1", Kind.MARKER, "", "", "", emptyList(), "#000000", t, "a", deleted = true)
        val back = (Cot.decode(Cot.encode(CotEvent.ItemChange(del), self)) as CotEvent.ItemChange).item
        assertTrue(back.deleted); assertEquals("u9", back.uid); assertEquals(t, back.updated)
    }

    @Test fun foreignMarkerWithoutHints() {
        val xml = """<event version="2.0" uid="x1" type="a-h-G-U-C-F" how="h-g-i-g-o" time="2026-11-13T05:45:00Z" start="2026-11-13T05:45:00Z" stale="2026-11-14T05:45:00Z"><point lat="20.2" lon="64.2" hae="9999999.0" ce="9999999.0" le="9999999.0"/><detail><contact callsign="EN ARTY"/></detail></event>"""
        val i = (Cot.decode(xml) as CotEvent.ItemChange).item
        assertEquals(Kind.MARKER, i.kind); assertEquals('6', Sidc.affiliation(i.sidc)); assertEquals("130300", Sidc.entity(i.sidc))
    }

    @Test fun geoChatRoundTripAllRoomKinds() {
        for ((kind, room) in listOf(RoomKind.ALL to "All Chat Rooms", RoomKind.MISSION to "OP DEMO", RoomKind.TEAM to "Cyan", RoomKind.DIRECT to "ANDROID-peer")) {
            val m = ChatMsg(Compact.newId(), kind, room, self.uid, self.callsign, "In position <OK> & ready", t, if (kind == RoomKind.TEAM) null else LatLon(20.0, 64.0))
            val xml = Cot.encode(CotEvent.Chat(m), self)
            assertTrue(xml.contains("type=\"b-t-f\"") && xml.contains("uid=\"GeoChat.${self.uid}.$room.${m.id}\""), xml)
            assertEquals(m, (Cot.decode(xml) as CotEvent.Chat).msg)
        }
    }

    @Test fun decodesAtakGeoChat() {
        val xml = """<event version="2.0" uid="GeoChat.ANDROID-58.All Chat Rooms.9f46716f-c875" type="b-t-f" how="h-g-i-g-o" time="2026-11-13T05:45:00.000Z" start="2026-11-13T05:45:00.000Z" stale="2026-11-14T05:45:00.000Z"><point lat="20.0" lon="64.0" hae="9999999.0" ce="9999999.0" le="9999999.0"/><detail><__chat parent="RootContactGroup" groupOwner="false" messageId="9f46716f-c875" chatroom="All Chat Rooms" id="All Chat Rooms" senderCallsign="BRAVO"><chatgrp uid0="ANDROID-58" uid1="All Chat Rooms" id="All Chat Rooms"/></__chat><link uid="ANDROID-58" type="a-f-G-U-C" relation="p-p"/><remarks source="BAO.F.ATAK.ANDROID-58" to="All Chat Rooms" time="2026-11-13T05:45:00.000Z">Moving</remarks></detail></event>"""
        val m = (Cot.decode(xml) as CotEvent.Chat).msg
        assertEquals("Moving", m.text); assertEquals(RoomKind.ALL, m.roomKind); assertEquals("ANDROID-58", m.fromUid); assertEquals("BRAVO", m.fromCallsign)
    }

    @Test fun receiptAndAlert() {
        val r = CotEvent.Receipt("0011223344556677", MsgState.READ, "ANDROID-abc")
        assertEquals(r, Cot.decode(Cot.encode(r, self)))
        val a = CotEvent.Alert("ANDROID-abc", "ALPHA 1", LatLon(20.0, 64.0), t, false)
        val xml = Cot.encode(a, self)
        assertTrue(xml.contains("b-a-o-tbl") && xml.contains("911 Alert"))
        assertEquals(a, Cot.decode(xml))
        assertEquals(a.copy(cancel = true), Cot.decode(Cot.encode(a.copy(cancel = true), self)))
    }

    @Test fun garbageNeverThrows() {
        assertNull(Cot.decode("not xml")); assertNull(Cot.decode("<event"))
        assertNull(Cot.decode("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><event uid=\"a\" type=\"a-f\">&e;</event>"))
        assertNull(Cot.decode("<event version=\"2.0\" uid=\"p\" type=\"t-x-c-t\" how=\"h\" time=\"x\"/>"))
    }

    @Test fun streamSplitting() {
        val a = Cot.encode(CotEvent.Receipt("0011223344556677", MsgState.DELIVERED, "x"), self)
        val buf = StringBuilder(a + a.take(40)); val got = ArrayList<String>()
        Cot.split(buf) { got.add(it) }
        assertEquals(1, got.size); assertNotNull(Cot.decode(got[0]))
        buf.append(a.drop(40)); Cot.split(buf) { got.add(it) }
        assertEquals(2, got.size); assertNotNull(Cot.decode(got[1])); assertEquals(0, buf.length)
    }

    @Test fun compactPacketsFitLoRa() {
        val long = "م".repeat(200) // multi-byte text is cut on a character boundary
        val worst = listOf(
            CotEvent.Pli(Contact("ANDROID-abc", "A-VERY-LONG-CALLSIGN-INDEED", "Dark Green!!", "x", LatLon(-79.9, -179.9), 8000.0, 359.0, 400.0, t)),
            CotEvent.Chat(ChatMsg(Compact.newId(), RoomKind.MISSION, "OPERATION WITH A LONG NAME 123", "ANDROID-abc", "A-VERY-LONG-CALLSIGN-INDEED", long, t, LatLon(20.0, 64.0))),
            CotEvent.Alert("ANDROID-abc", "A-VERY-LONG-CALLSIGN-INDEED", LatLon(20.0, 64.0), t, false),
            CotEvent.Receipt(Compact.newId(), MsgState.READ, "ANDROID-abc"),
        )
        for (e in worst) assertTrue(Compact.encode(e)!!.size + 28 < 200, "${e::class.simpleName} ${Compact.encode(e)!!.size}")
        val txt = (Compact.decode(Compact.encode(worst[1])!!) as CotEvent.Chat).msg.text
        assertTrue(txt.isNotEmpty() && txt.all { it == 'م' })
    }

    @Test fun compactRoundTrip() {
        val known = mapOf(Compact.hash("ANDROID-abc") to "ANDROID-abc", Compact.hash("ANDROID-peer") to "ANDROID-peer")
        val c = Contact("ANDROID-abc", "ALPHA 1", "Cyan", "Team Member", LatLon(20.0123456, 64.0654321), 812.0, 90.0, 2.0, t)
        assertEquals(c, (Compact.decode(Compact.encode(CotEvent.Pli(c))!!) { known[it] } as CotEvent.Pli).contact)
        val unknown = (Compact.decode(Compact.encode(CotEvent.Pli(c))!!) as CotEvent.Pli).contact
        assertTrue(unknown.uid.startsWith("MESH-"))
        val m = ChatMsg(Compact.newId(), RoomKind.DIRECT, "ANDROID-peer", "ANDROID-abc", "ALPHA 1", "Need resupply", t, LatLon(20.5, 64.5))
        assertEquals(m, (Compact.decode(Compact.encode(CotEvent.Chat(m))!!) { known[it] } as CotEvent.Chat).msg)
        val g = m.copy(roomKind = RoomKind.MISSION, room = "OP DEMO", roomLabel = "OP DEMO", loc = null)
        assertEquals(g, (Compact.decode(Compact.encode(CotEvent.Chat(g))!!) { known[it] } as CotEvent.Chat).msg)
        val r = CotEvent.Receipt(m.id, MsgState.DELIVERED, "ANDROID-peer")
        assertEquals(r, Compact.decode(Compact.encode(r)!!) { known[it] })
        assertNull(Compact.encode(CotEvent.ItemChange(Item("u", "", Kind.MARKER, "", "", "", emptyList(), "#000000", t, "a"))))
        assertNull(Compact.decode(byteArrayOf(9, 1, 2))); assertNull(Compact.decode(byteArrayOf(2, 1)))
    }
}
