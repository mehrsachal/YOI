package app.pquest.missions

import app.pquest.cot.Cot
import app.pquest.cot.CotEvent
import app.pquest.cot.Item
import app.pquest.cot.Self
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class Role { OWNER, EDITOR, VIEWER; val canEdit get() = this != VIEWER }

/** A named container of overlays, symbols, routes and a chat room. [key] is the base64 mesh channel key. */
data class Mission(val id: String, val name: String, val key: String, val role: Role, val visible: Boolean = true, val onServer: Boolean = false)

data class Conflict(val uid: String, val time: Long, val keptAuthor: String, val droppedAuthor: String, val name: String)

/** Last-writer-wins merge of one incoming item against the local copy. */
object Merge {
    /** What to store (null = keep the local copy) and a conflict record when a concurrent edit was discarded. */
    data class Result(val store: Item?, val conflict: Conflict?)

    fun resolve(local: Item?, localDirty: Boolean, remote: Item, now: Long = System.currentTimeMillis()): Result {
        if (local == null) return Result(remote, null)
        if (same(local, remote)) return Result(null, null)
        val remoteWins = remote.updated > local.updated || (remote.updated == local.updated && remote.author > local.author)
        // A conflict is two edits made without seeing each other: the local one has not been sent yet.
        val concurrent = localDirty || remote.updated == local.updated
        return if (remoteWins) Result(remote, if (concurrent) Conflict(local.uid, now, remote.author, local.author, local.name.ifEmpty { remote.name }) else null)
        else Result(null, if (concurrent) Conflict(local.uid, now, local.author, remote.author, local.name) else null)
    }

    private fun same(a: Item, b: Item) = a.copy(updated = 0, author = "") == b.copy(updated = 0, author = "")
}

data class ServerConfig(
    val host: String, val port: Int, val caP12: ByteArray, val caPass: String, val clientP12: ByteArray, val clientPass: String,
)

/** TAK data package (.zip): CoT files plus, optionally, a server connection and a P-Quest mission descriptor. */
object DataPackage {
    class Contents(val items: List<Item>, val mission: Mission?, val server: ServerConfig?)

    private const val MISSION_FILE = "pquest-mission.properties"

    fun write(name: String, items: List<Item>, self: Self, mission: Mission? = null): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            val manifest = StringBuilder("<MissionPackageManifest version=\"2\"><Configuration><Parameter name=\"uid\" value=\"${java.util.UUID.randomUUID()}\"/>")
                .append("<Parameter name=\"name\" value=\"${name.replace("\"", "'").replace("&", "+").replace("<", "(")}\"/></Configuration><Contents>")
            for (i in items) {
                val entry = "${i.uid}/${i.uid}.cot"
                z.putNextEntry(ZipEntry(entry)); z.write(Cot.encode(CotEvent.ItemChange(i), self).toByteArray()); z.closeEntry()
                manifest.append("<Content ignore=\"false\" zipEntry=\"$entry\"><Parameter name=\"uid\" value=\"${i.uid}\"/></Content>")
            }
            if (mission != null) {
                z.putNextEntry(ZipEntry(MISSION_FILE))
                z.write("id=${mission.id}\nname=${enc(mission.name)}\nkey=${mission.key}\n".toByteArray()); z.closeEntry()
                manifest.append("<Content ignore=\"true\" zipEntry=\"$MISSION_FILE\"/>")
            }
            z.putNextEntry(ZipEntry("MANIFEST/manifest.xml")); z.write(manifest.append("</Contents></MissionPackageManifest>").toString().toByteArray()); z.closeEntry()
        }
        return bo.toByteArray()
    }

    fun read(zip: ByteArray): Contents {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory && files.size < 5000) files[e.name.substringAfterLast('/')] = z.readBytes().also { require(it.size < 50_000_000) }
            }
        }
        val items = files.filterKeys { it.endsWith(".cot") }.values.mapNotNull { (Cot.decode(String(it)) as? CotEvent.ItemChange)?.item }
        val mission = files[MISSION_FILE]?.let { raw ->
            val p = String(raw).lines().mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            // Anyone importing a package joins as editor; the owner is the device that created the mission.
            Mission(p.getValue("id"), dec(p.getValue("name")), p.getValue("key"), Role.EDITOR)
        }
        // ATAK-style connection package: a .pref file naming the server and two PKCS#12 stores.
        val server = files.entries.firstOrNull { it.key.endsWith(".pref") }?.let { pref ->
            val text = String(pref.value)
            fun entry(key: String) = Regex("key=\"$key\"[^>]*>([^<]*)<").find(text)?.groupValues?.get(1)
            val conn = entry("connectString0")?.split(":") ?: return@let null
            val ca = files[entry("caLocation")?.substringAfterLast('/') ?: ""] ?: return@let null
            val client = files[entry("certificateLocation")?.substringAfterLast('/') ?: ""] ?: return@let null
            ServerConfig(conn[0], conn.getOrNull(1)?.toIntOrNull() ?: 8089, ca, entry("caPassword") ?: "atakatak", client, entry("clientPassword") ?: "atakatak")
        }
        return Contents(items.map { if (mission != null) it.copy(mission = mission.id) else it }, mission, server)
    }

    internal fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    internal fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}

/** Everything that travels as a QR code. All are pquest:// links. */
object Links {
    data class Enrol(val host: String, val port: Int, val enrolPort: Int, val user: String, val token: String, val caSha256: String)

    const val MAX_QR = 1800

    private fun query(uri: String, scheme: String): Map<String, String>? {
        if (!uri.startsWith("pquest://$scheme?")) return null
        return uri.substringAfter('?').split("&").mapNotNull { kv -> kv.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to DataPackage.dec(it[1]) } }.toMap()
    }

    /** [asRole] is the role the receiving device takes (editor or viewer). */
    fun mission(m: Mission, asRole: Role = Role.EDITOR) = "pquest://mission?id=${m.id}&n=${DataPackage.enc(m.name)}&k=${DataPackage.enc(m.key)}&r=${asRole.name[0]}"
    fun parseMission(uri: String): Mission? = query(uri.trim(), "mission")?.let { q ->
        Mission(q["id"] ?: return null, q["n"] ?: return null, q["k"] ?: return null, if (q["r"] == "V") Role.VIEWER else Role.EDITOR)
    }

    fun enrol(e: Enrol) = "pquest://enroll?h=${DataPackage.enc(e.host)}&p=${e.port}&e=${e.enrolPort}&u=${DataPackage.enc(e.user)}&t=${DataPackage.enc(e.token)}&ca=${e.caSha256}"
    fun parseEnrol(uri: String): Enrol? = query(uri.trim(), "enroll")?.let { q ->
        Enrol(q["h"] ?: return null, q["p"]?.toIntOrNull() ?: 8089, q["e"]?.toIntOrNull() ?: 8446, q["u"] ?: return null, q["t"] ?: return null,
            q["ca"]?.lowercase()?.takeIf { it.length == 64 } ?: return null)
    }

    /** A small overlay as one QR code, or null when it does not fit. */
    fun items(items: List<Item>, self: Self): String? {
        val raw = items.joinToString("\n") { Cot.encode(CotEvent.ItemChange(it), self).substringAfter("?>") }.toByteArray()
        val bo = ByteArrayOutputStream()
        DeflaterOutputStream(bo, Deflater(9)).use { it.write(raw) }
        val uri = "pquest://items?z=" + Base64.getUrlEncoder().withoutPadding().encodeToString(bo.toByteArray())
        return uri.takeIf { it.length <= MAX_QR }
    }

    fun parseItems(uri: String): List<Item>? {
        if (!uri.trim().startsWith("pquest://items?z=")) return null
        return runCatching {
            val raw = InflaterInputStream(ByteArrayInputStream(Base64.getUrlDecoder().decode(uri.trim().substringAfter("z=")))).use { s ->
                val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
                while (true) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n); require(out.size() < 2_000_000) }
                out.toString("UTF-8")
            }
            raw.split("\n").mapNotNull { (Cot.decode(it) as? CotEvent.ItemChange)?.item }
        }.getOrNull()
    }
}
