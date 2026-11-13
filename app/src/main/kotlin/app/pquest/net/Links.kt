package app.pquest.net

import android.content.Context
import android.net.wifi.WifiManager
import app.pquest.cot.Cot
import app.pquest.missions.Csr
import app.pquest.missions.Links
import app.pquest.missions.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** TLS material for one TAK Server: our client certificate, and trust pinned to the unit's own CA only. */
class TakTls(cfg: ServerConfig) {
    val trust: X509TrustManager
    val context: SSLContext

    init {
        val ca = KeyStore.getInstance("PKCS12").apply { load(ByteArrayInputStream(cfg.caP12), cfg.caPass.toCharArray()) }
        val client = KeyStore.getInstance("PKCS12").apply { load(ByteArrayInputStream(cfg.clientP12), cfg.clientPass.toCharArray()) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ca) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(client, cfg.clientPass.toCharArray()) }
        trust = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        context = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(trust), null) }
    }

    /**
     * HTTPS client for the server's Mission API. Server certificates are accepted only if they chain to the unit CA;
     * the host name is not compared because TAK server certificates are routinely issued without matching names.
     */
    fun http(): OkHttpClient = OkHttpClient.Builder().sslSocketFactory(context.socketFactory, trust).hostnameVerifier { _, _ -> true }
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
}

/** Streaming CoT connection to TAK Server, FreeTAKServer or Taky over TLS (default port 8089). Reconnects by itself. */
class TakLink(private val cfg: ServerConfig, private val onXml: (String) -> Unit, private val onUp: (Boolean) -> Unit) {
    val tls = TakTls(cfg)
    @Volatile private var out: OutputStream? = null
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var running = true

    private val thread = Thread({
        var wait = 2000L
        while (running) {
            try {
                val s = tls.context.socketFactory.createSocket() as SSLSocket
                socket = s
                s.enabledProtocols = s.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
                s.connect(InetSocketAddress(cfg.host, cfg.port), 10_000)
                s.startHandshake()
                out = s.outputStream; onUp(true); wait = 2000
                val buf = StringBuilder(); val chunk = ByteArray(8192); val input = s.inputStream
                while (running) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.append(String(chunk, 0, n, Charsets.UTF_8))
                    Cot.split(buf, onXml)
                }
            } catch (e: Exception) { /* fall through to reconnect */ }
            out = null; runCatching { socket?.close() }
            if (running) { onUp(false); runCatching { Thread.sleep(wait) }; wait = minOf(wait * 2, 60_000) }
        }
        onUp(false)
    }, "tak-link").apply { isDaemon = true }

    fun start() = thread.start()
    fun stop() { running = false; out = null; runCatching { socket?.close() }; thread.interrupt() }

    @Synchronized
    fun send(xml: String): Boolean = try {
        out?.let { it.write(xml.toByteArray()); it.flush(); true } ?: false
    } catch (e: Exception) { out = null; runCatching { socket?.close() }; false }
}

/** TAK Server Mission (Data Sync) API. */
class Marti(private val host: String, private val http: OkHttpClient, private val port: Int = 8443) {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun url(path: String) = "https://$host:$port/Marti/api/$path"
    private fun call(method: String, path: String): String? = runCatching {
        val body = if (method == "GET") null else ByteArray(0).toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url(url(path)).method(method, body).build()).execute().use { if (it.isSuccessful) it.body?.string() ?: "" else null }
    }.getOrNull()

    fun missions(): List<String>? = call("GET", "missions")?.let { runCatching {
        JSONObject(it).getJSONArray("data").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).getString("name") } }
    }.getOrNull() }

    fun create(name: String, uid: String) = call("PUT", "missions/${enc(name)}?creatorUid=${enc(uid)}") != null
    fun subscribe(name: String, uid: String) = call("PUT", "missions/${enc(name)}/subscription?uid=${enc(uid)}") != null

    /** All CoT events currently filed under the mission. */
    fun events(name: String): List<String>? = call("GET", "missions/${enc(name)}/cot")?.let { xml ->
        ArrayList<String>().also { out -> Cot.split(StringBuilder(xml)) { e -> out.add(e) } }
    }
}

/** Peer-to-peer CoT on the local Wi-Fi network: the standard TAK multicast groups, no server needed. */
class WifiLink(private val ctx: Context, private val onXml: (String) -> Unit) {
    val up = MutableStateFlow(false)
    @Volatile private var running = false
    @Volatile private var sockets: List<MulticastSocket> = emptyList()
    private val sa = InetSocketAddress(InetAddress.getByName("239.2.3.1"), 6969)
    private val chat = InetSocketAddress(InetAddress.getByName("224.10.10.1"), 17012)

    private fun wlan(): NetworkInterface? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().firstOrNull { it.isUp && !it.isLoopback && it.supportsMulticast() && (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) && it.inetAddresses.toList().any { a -> a.address.size == 4 } }
    }.getOrNull()

    fun start() {
        if (running) return
        running = true
        Thread({
            val lock = runCatching { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("pquest").apply { setReferenceCounted(false) } }.getOrNull()
            while (running) {
                val nif = wlan()
                if (nif == null) { up.value = false; runCatching { Thread.sleep(5000) }; continue }
                try {
                    lock?.acquire()
                    val socks = listOf(sa, chat).map { g -> MulticastSocket(g.port).apply { networkInterface = nif; joinGroup(g, nif); soTimeout = 0; timeToLive = 4 } }
                    sockets = socks; up.value = true
                    val readers = socks.map { s -> Thread({
                        val buf = ByteArray(65535)
                        runCatching { while (running) { val p = DatagramPacket(buf, buf.size); s.receive(p); runCatching { onXml(String(p.data, 0, p.length, Charsets.UTF_8)) } } }
                    }, "wifi-rx").apply { isDaemon = true; start() } }
                    while (running && wlan() != null && readers.all { it.isAlive }) Thread.sleep(5000)
                } catch (e: Exception) { /* retry below */ }
                up.value = false; sockets.forEach { runCatching { it.close() } }; sockets = emptyList()
                runCatching { lock?.release() }
                if (running) runCatching { Thread.sleep(3000) }
            }
        }, "wifi-link").apply { isDaemon = true }.start()
    }

    fun stop() { running = false; up.value = false; sockets.forEach { runCatching { it.close() } } }

    fun send(xml: String, isChat: Boolean): Boolean = runCatching {
        val s = sockets.getOrNull(if (isChat) 1 else 0) ?: return false
        val b = xml.toByteArray()
        if (b.size > 60_000) return false
        s.send(DatagramPacket(b, b.size, if (isChat) chat else sa)); true
    }.getOrDefault(false)
}

/** Certificate enrolment against the official TAK Server (port 8446) from a pquest://enroll QR code. */
object Enrolment {
    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    fun enrol(e: Links.Enrol, uid: String): ServerConfig {
        // Before we hold the CA, trust is pinned to the CA fingerprint carried in the QR code.
        val pin = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) = throw CertificateException()
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) { if (c.none { sha256(it.encoded) == e.caSha256 }) throw CertificateException("server is not signed by the unit CA") }
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pin), null) }
        val http = OkHttpClient.Builder().sslSocketFactory(ctx.socketFactory, pin).hostnameVerifier { _, _ -> true }.connectTimeout(15, TimeUnit.SECONDS).build()
        val auth = "Basic " + Base64.getEncoder().encodeToString("${e.user}:${e.token}".toByteArray())
        val base = "https://${e.host}:${e.enrolPort}/Marti/api/tls"
        val config = http.newCall(Request.Builder().url("$base/config").header("Authorization", auth).build()).execute().use { it.body?.string() ?: "" }
        val subject = ArrayList<Pair<String, String>>().apply { add("CN" to e.user) }
        for (m in Regex("name=\"(OU?)\"\\s+value=\"([^\"]+)\"").findAll(config)) subject += m.groupValues[1] to m.groupValues[2]
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val csr = Base64.getEncoder().encodeToString(Csr.build(keys, subject))
        val signed = http.newCall(
            Request.Builder().url("$base/signClient/v2?clientUid=${URLEncoder.encode(uid, "UTF-8")}&version=1").header("Authorization", auth)
                .post(csr.toRequestBody("text/plain".toMediaType())).build()
        ).execute().use { r -> require(r.isSuccessful) { "enrolment refused (${r.code})" }; JSONObject(r.body!!.string()) }
        val cf = CertificateFactory.getInstance("X.509")
        fun cert(b64: String) = cf.generateCertificate(ByteArrayInputStream(Base64.getMimeDecoder().decode(b64.replace(Regex("-----[A-Z ]+-----"), "")))) as X509Certificate
        val mine = cert(signed.getString("signedCert"))
        val cas = signed.keys().asSequence().filter { it.startsWith("ca") }.sorted().map { cert(signed.getString(it)) }.toList()
        require(cas.any { sha256(it.encoded) == e.caSha256 }) { "CA does not match the QR code" }
        val pass = Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom().generateSeed(18))
        fun p12(fill: KeyStore.() -> Unit) = ByteArrayOutputStream().also { o -> KeyStore.getInstance("PKCS12").apply { load(null, null); fill(); store(o, pass.toCharArray()) } }.toByteArray()
        return ServerConfig(
            e.host, e.port, p12 { cas.forEachIndexed { i, c -> setCertificateEntry("ca$i", c) } }, pass,
            p12 { setKeyEntry("client", keys.private, pass.toCharArray(), (listOf(mine) + cas).toTypedArray()) }, pass,
        )
    }
}
