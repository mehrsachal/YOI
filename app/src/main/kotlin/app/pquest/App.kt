package app.pquest

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import app.pquest.coords.CoordFormat
import app.pquest.map.MapHub
import app.pquest.map.TrackStore
import app.pquest.security.SecureDb
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
    val coordFormat get() = CoordFormat.valueOf(format)
}

class App : Application() {
    lateinit var settings: Settings
    lateinit var hub: MapHub
    val tracks: TrackStore by lazy { TrackStore(SecureDb.open(this, "pquest.db")) }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        hub = MapHub(this)
        applyOnline()
        MapLibre.getInstance(this)
        // All map-engine requests go through the hub's gateway; the engine's own disk cache is not used.
        HttpRequestUtil.setOkHttpClient(hub.mapClient)
        runCatching { OfflineManager.getInstance(this).setMaximumAmbientCacheSize(0, null) }
    }

    fun applyOnline() {
        hub.templates = mapOf("topo" to settings.urlTopo.trim(), "satellite" to settings.urlSatellite.trim(), "dem" to settings.urlDem.trim())
        hub.onlineEnabled = settings.online
    }
}
