package app.pquest

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import app.pquest.coords.CoordFormat
import app.pquest.map.MapHub
import app.pquest.map.TrackStore
import app.pquest.security.SecureDb
import app.pquest.security.Vault
import app.pquest.data.Repo
import app.pquest.net.Engine
import app.pquest.symbology.Symbols
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import kotlin.reflect.KProperty

/** A persisted setting that Compose observes. */
class Pref<T>(private val p: SharedPreferences, private val key: String, def: T) {
    @Suppress("UNCHECKED_CAST")
    private val state = mutableStateOf(when (def) {
        is Boolean -> p.getBoolean(key, def) as T
        is Float -> p.getFloat(key, def) as T
        else -> p.getString(key, def as String) as T
    })
    operator fun getValue(o: Any?, k: KProperty<*>): T = state.value
    operator fun setValue(o: Any?, k: KProperty<*>, v: T) {
        state.value = v
        p.edit().apply { when (v) { is Boolean -> putBoolean(key, v); is Float -> putFloat(key, v); else -> putString(key, v as String) } }.apply()
    }
}

class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var lang by Pref(p, "lang", "en")
    var night by Pref(p, "night", false)
    var format by Pref(p, "format", CoordFormat.MGRS.name)
    var base by Pref(p, "base", "topo")
    var hillshade by Pref(p, "hillshade", true)
    var grid by Pref(p, "grid", true)
    var online by Pref(p, "online", false)
    var urlTopo by Pref(p, "url_topo", "")
    var urlSatellite by Pref(p, "url_satellite", "")
    var urlDem by Pref(p, "url_dem", "")
    var camLat by Pref(p, "cam_lat", Float.NaN)
    var camLon by Pref(p, "cam_lon", Float.NaN)
    var camZoom by Pref(p, "cam_zoom", 11.5f)
    var zulu by Pref(p, "zulu", false)
    var mils by Pref(p, "mils", false)
    var north by Pref(p, "north", "GRID")
    var dark by Pref(p, "dark", false)
    var pliSeconds by Pref(p, "pli", "10")
    var secure by Pref(p, "secure", true)
    var tourDone by Pref(p, "tour_done", false)
    var tourStep by Pref(p, "tour_step", "0")
    var tourLater by Pref(p, "tour_later", false)
    var slope by Pref(p, "slope", false)
    var contours by Pref(p, "contours", true)
    var tips by Pref(p, "tips", "")
    val coordFormat get() = CoordFormat.valueOf(format)
}

/** Everything that exists only while the app is unlocked with the PIN. */
class Session(app: App, unlocked: Vault.Unlocked) {
    val duress = unlocked.duress
    val passphrase = unlocked.passphrase
    private val db = SecureDb.open(app, if (duress) "pquest_d.db" else "pquest.db", unlocked.passphrase)
    val repo = Repo(db)
    val tracks = TrackStore(db)
    val engine = Engine(app, repo) { TrackService.fix.value }
    fun close() { engine.stop(); repo.close() }
}

class App : Application() {
    lateinit var settings: Settings
    lateinit var hub: MapHub
    var session by mutableStateOf<Session?>(null)
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashLog()
        settings = Settings(this)
        hub = MapHub(this)
        applyOnline()
        MapLibre.getInstance(this)
        // All map-engine requests go through the hub's gateway; the engine's own disk cache is not used.
        HttpRequestUtil.setOkHttpClient(hub.mapClient)
        runCatching { OfflineManager.getInstance(this).setMaximumAmbientCacheSize(0, null) }
        Symbols.init(this)
    }

    fun open(u: Vault.Unlocked) {
        session?.close()
        session = Session(this, u).also { s ->
            s.engine.pliSeconds = settings.pliSeconds.toIntOrNull() ?: 10
            s.engine.demo = !settings.tourDone
            // In the duress profile nothing is ever transmitted.
            s.engine.start(goDark = settings.dark || u.duress)
        }
    }

    fun lockOut() { session?.close(); session = null }

    /** Online tile sources follow the admin setting, and are always off while dark. */
    fun applyOnline() {
        hub.templates = mapOf("topo" to settings.urlTopo.trim(), "satellite" to settings.urlSatellite.trim(), "dem" to settings.urlDem.trim())
        hub.onlineEnabled = settings.online && !settings.dark
    }

    fun setDark(on: Boolean) {
        settings.dark = on
        applyOnline()
        session?.engine?.setDark(on || session?.duress == true)
        if (on) TrackService.reportPosition(this, false)
    }

    fun wipe() {
        runCatching { TrackService.record(this, false) }
        lockOut()
        Vault.wipe(this)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** Crash reports stay on the device (files/crash) and are exported by hand from Settings. */
    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                java.io.File(filesDir, "crash").apply { mkdirs() }.resolve("crash-${System.currentTimeMillis()}.txt")
                    .writeText("P-Quest ${BuildInfo.VERSION}\nAndroid ${android.os.Build.VERSION.RELEASE} ${android.os.Build.MODEL}\n\n" + android.util.Log.getStackTraceString(e))
            }
            previous?.uncaughtException(t, e)
        }
    }
}
