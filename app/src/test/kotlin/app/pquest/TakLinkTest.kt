package app.pquest

import app.pquest.coords.LatLon
import app.pquest.cot.Contact
import app.pquest.cot.Cot
import app.pquest.cot.CotEvent
import app.pquest.cot.Self
import app.pquest.missions.DataPackage
import app.pquest.net.TakLink
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * End to end over real TLS: a connection package made by server/issue_client.sh is imported, the app's TAK link
 * connects to a local TLS server that demands a client certificate, and CoT flows both ways.
 * Skipped where openssl or zip are not installed.
 */
class TakLinkTest {
    private fun run(dir: File, vararg cmd: String, env: Map<String, String> = emptyMap()): Int =
        ProcessBuilder(*cmd).directory(dir).redirectErrorStream(true).apply { environment().putAll(env) }.start().let { p -> p.inputStream.readBytes(); p.waitFor() }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun waitFor(what: () -> Boolean): Boolean { repeat(100) { if (what()) return true; Thread.sleep(100) }; return false }

    @Test fun packageFromServerKitConnectsWithMutualTlsAndExchangesCot() {
        val kit = File("../server").canonicalFile
        val work = File("build/takkit").apply { deleteRecursively(); mkdirs() }
        for (f in listOf("issue_client.sh")) kit.resolve(f).copyTo(work.resolve(f)).setExecutable(true)
        assumeTrue("openssl and zip needed", runCatching { run(work, "openssl", "version") == 0 && run(work, "zip", "-v") == 0 }.getOrDefault(false))
        val port = freePort()
        assertEquals(0, run(work, "./issue_client.sh", "ALPHA-1", "127.0.0.1", port.toString()))
        val cfg = DataPackage.read(work.resolve("out/ALPHA-1.zip").readBytes()).server!!
        assertEquals("127.0.0.1", cfg.host); assertEquals(port, cfg.port)

        val server = ProcessBuilder("openssl", "s_server", "-accept", port.toString(), "-cert", "certs/server.pem", "-key", "certs/server.key",
            "-CAfile", "ca/ca.pem", "-Verify", "1", "-verify_return_error", "-quiet").directory(work).redirectErrorStream(true).start()
        val fromClient = StringBuilder()
        Thread { runCatching { server.inputStream.bufferedReader().use { r -> val b = CharArray(4096); while (true) { val n = r.read(b); if (n < 0) break; synchronized(fromClient) { fromClient.append(b, 0, n) } } } } }.apply { isDaemon = true }.start()
        Thread.sleep(700)
        val received = CopyOnWriteArrayList<String>(); val up = java.util.concurrent.atomic.AtomicBoolean()
        val link = TakLink(cfg, { received.add(it) }) { up.set(it) }
        try {
            link.start()
            assertTrue("TLS handshake with client certificate: ${link.lastError} / server said: $fromClient", waitFor { up.get() })
            val self = Self("PQUEST-test", "ALPHA-1")
            val pli = CotEvent.Pli(Contact(self.uid, self.callsign, "Cyan", "Team Member", LatLon(20.05, 64.06), 300.0, 0.0, 0.0, System.currentTimeMillis()))
            assertTrue(link.send(Cot.encode(pli, self)))
            assertTrue("server received our position report", waitFor { synchronized(fromClient) { fromClient.contains("PQUEST-test") && fromClient.contains("</event>") } })
            // The server pushes someone else's position; it must arrive as a decodable event.
            val other = Cot.encode(CotEvent.Pli(pli.contact.copy(uid = "ANDROID-other", callsign = "BRAVO")), self)
            server.outputStream.write(other.toByteArray()); server.outputStream.flush()
            assertTrue(waitFor { received.isNotEmpty() })
            assertEquals("BRAVO", (Cot.decode(received[0]) as CotEvent.Pli).contact.callsign)
        } finally { link.stop(); server.destroyForcibly() }
    }

    @Test fun serverNotSignedByTheUnitCaIsRefused() {
        val work = File("build/takkit2").apply { deleteRecursively(); mkdirs() }
        File("../server/issue_client.sh").canonicalFile.copyTo(work.resolve("issue_client.sh")).setExecutable(true)
        assumeTrue(runCatching { run(work, "openssl", "version") == 0 && run(work, "zip", "-v") == 0 }.getOrDefault(false))
        val port = freePort()
        assertEquals(0, run(work, "./issue_client.sh", "ALPHA-1", "127.0.0.1", port.toString()))
        val cfg = DataPackage.read(work.resolve("out/ALPHA-1.zip").readBytes()).server!!
        // An impostor with its own self-signed certificate.
        assertEquals(0, run(work, "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", "rogue.key", "-out", "rogue.pem", "-days", "2", "-subj", "/CN=127.0.0.1"))
        val server = ProcessBuilder("openssl", "s_server", "-accept", port.toString(), "-cert", "rogue.pem", "-key", "rogue.key", "-quiet").directory(work).redirectErrorStream(true).start()
        Thread.sleep(700)
        val up = java.util.concurrent.atomic.AtomicBoolean()
        val link = TakLink(cfg, {}) { if (it) up.set(true) }
        try { link.start(); Thread.sleep(2500); assertFalse("must not trust a server outside the unit CA", up.get()); assertNotNull(link.lastError); assertFalse(link.send("<event/>")) }
        finally { link.stop(); server.destroyForcibly() }
    }
}
