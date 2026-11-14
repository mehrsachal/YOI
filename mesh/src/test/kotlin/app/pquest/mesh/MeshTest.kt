package app.pquest.mesh

import kotlin.test.*

class MeshTest {
    @Test fun varintAndFieldsRoundTrip() {
        val b = ProtoWriter().varint(1, 300).fixed32(2, -2).bytes(3, "hi".toByteArray()).varint(15, 0xFFFFFFFFL).bytes()
        val f = protoFields(b)
        assertEquals(listOf(1, 2, 3, 15), f.map { it.num })
        assertEquals(300, f[0].value); assertEquals(0xFFFFFFFEL, f[1].value); assertEquals("hi", String(f[2].data!!)); assertEquals(0xFFFFFFFFL, f[3].value)
        // Reference encoding from the protobuf documentation: field 1 = 150 is 08 96 01.
        assertContentEquals(byteArrayOf(0x08, 0x96.toByte(), 0x01), ProtoWriter().varint(1, 150).bytes())
    }

    @Test fun outgoingPacketShape() {
        val payload = ByteArray(Meshtastic.MAX_PAYLOAD) { it.toByte() }
        val packet = protoFields(protoFields(Meshtastic.send(payload, 77)).single { it.num == 1 }.data!!)
        assertEquals(Meshtastic.BROADCAST, packet.single { it.num == 2 }.value)
        assertEquals(77, packet.single { it.num == 6 }.value); assertEquals(3, packet.single { it.num == 9 }.value)
        val data = protoFields(packet.single { it.num == 4 }.data!!)
        assertEquals(256, data.single { it.num == 1 }.value); assertContentEquals(payload, data.single { it.num == 2 }.data)
        assertFailsWith<IllegalArgumentException> { Meshtastic.send(ByteArray(201), 1) }
        assertEquals(3, protoFields(Meshtastic.wantConfig(9)).single().num)
    }

    @Test fun parsesRadioFrames() {
        val data = ProtoWriter().varint(1, 256).bytes(2, byteArrayOf(9, 8)).bytes()
        val pkt = ProtoWriter().fixed32(1, 0x11223344).fixed32(2, -1).bytes(4, data).varint(9, 1).varint(15, 3).bytes()
        val p = Meshtastic.parse(ProtoWriter().varint(1, 5).bytes(2, pkt).bytes()) as FromRadio.Packet
        assertEquals(0x11223344L, p.from); assertEquals(256, p.port); assertContentEquals(byteArrayOf(9, 8), p.payload); assertEquals(2, p.hops)
        val user = ProtoWriter().bytes(1, "!a1b2".toByteArray()).bytes(2, "Section radio 2".toByteArray()).bytes()
        val node = ProtoWriter().varint(1, 42).bytes(2, user).fixed32(4, java.lang.Float.floatToIntBits(6.5f)).fixed32(5, 1_700_000_000).varint(9, 1).bytes()
        val n = (Meshtastic.parse(ProtoWriter().bytes(4, node).bytes()) as FromRadio.Node).node
        assertEquals(MeshNode(42, "Section radio 2", 1_700_000_000_000, 1, 6.5f), n)
        assertEquals(FromRadio.MyInfo(42), Meshtastic.parse(ProtoWriter().bytes(3, ProtoWriter().varint(1, 42).bytes()).bytes()))
        assertEquals(FromRadio.ConfigComplete(9), Meshtastic.parse(ProtoWriter().varint(7, 9).bytes()))
        assertEquals(FromRadio.Other, Meshtastic.parse(byteArrayOf(0x0A, 0x7F, 1)))
        assertEquals(FromRadio.Other, Meshtastic.parse(ProtoWriter().bytes(2, ProtoWriter().bytes(5, byteArrayOf(1)).bytes()).bytes()))
    }

    @Test fun encryptionPerMissionKey() {
        val k1 = MeshCrypto.newKey(); var k2 = MeshCrypto.newKey()
        while (MeshCrypto.keyId(k2) == MeshCrypto.keyId(k1)) k2 = MeshCrypto.newKey()
        val msg = ByteArray(170) { 7 }
        val sealed = MeshCrypto.seal(k2, msg)
        assertEquals(170 + MeshCrypto.OVERHEAD, sealed.size); assertTrue(sealed.size < Meshtastic.MAX_PAYLOAD)
        val (idx, plain) = MeshCrypto.open(listOf(k1, k2), sealed)!!
        assertEquals(1, idx); assertContentEquals(msg, plain)
        assertNull(MeshCrypto.open(listOf(k1), sealed))
        sealed[40] = (sealed[40] + 1).toByte()
        assertNull(MeshCrypto.open(listOf(k1, k2), sealed), "tampered packet must be rejected")
        assertFalse(MeshCrypto.seal(k2, msg).contentEquals(MeshCrypto.seal(k2, msg)), "nonce must differ per packet")
        assertNull(MeshCrypto.open(listOf(k1), ByteArray(5)))
    }

    /** Frames produced by the official Meshtastic protobuf definitions (python package "meshtastic"). */
    private val official = javaClass.getResource("/official_frames.txt")!!.readText().trim().lines().associate { l ->
        l.substringBefore('=') to l.substringAfter('=').chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    @Test fun ourOutgoingFramesAreByteIdenticalToTheOfficialEncoding() {
        assertContentEquals(official.getValue("to_packet"), Meshtastic.send(ByteArray(10) { it.toByte() }, 77))
        assertContentEquals(official.getValue("to_wantconfig"), Meshtastic.wantConfig(9))
    }

    @Test fun weReadFramesFromTheOfficialEncoding() {
        val p = Meshtastic.parse(official.getValue("from_packet")) as FromRadio.Packet
        assertEquals(0x11223344L, p.from); assertEquals(Meshtastic.BROADCAST, p.to); assertEquals(Meshtastic.PORT_PRIVATE, p.port)
        assertContentEquals(byteArrayOf(9, 8), p.payload); assertEquals(2, p.hops)
        assertEquals(MeshNode(42, "Section radio 2", 1_700_000_000_000, 1, 6.5f), (Meshtastic.parse(official.getValue("from_node")) as FromRadio.Node).node)
        assertEquals(FromRadio.MyInfo(42), Meshtastic.parse(official.getValue("from_myinfo")))
        assertEquals(FromRadio.ConfigComplete(9), Meshtastic.parse(official.getValue("from_complete")))
        assertEquals(FromRadio.Other, Meshtastic.parse(official.getValue("from_encrypted")))
    }
}
