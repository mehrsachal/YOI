package app.pquest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import app.pquest.coords.Dtg
import kotlinx.coroutines.flow.MutableStateFlow

/** Foreground service that records a GPS fix every 10 s into the encrypted database, screen on or off. */
class TrackService : Service(), LocationListener {
    companion object {
        const val INTERVAL_MS = 10_000L
        val recording = MutableStateFlow<Long?>(null)
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, TrackService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, TrackService::class.java))
    }

    private val prefs by lazy { getSharedPreferences("track", MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (recording.value != null) return START_STICKY
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("track", getString(R.string.track), NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "track").setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.recording)).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(1, n)
            getSystemService(LocationManager::class.java).requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, this)
        } catch (e: Exception) { // permission missing or GPS absent
            stopSelf()
            return START_NOT_STICKY
        }
        // Resume the same track if the system restarted the service.
        val app = application as App
        var id = prefs.getLong("id", -1)
        if (id < 0) {
            val now = System.currentTimeMillis()
            id = app.tracks.start(Dtg.format(now), now)
            prefs.edit().putLong("id", id).apply()
        }
        recording.value = id
        return START_STICKY
    }

    override fun onLocationChanged(l: Location) {
        val id = recording.value ?: return
        (application as App).tracks.add(id, l.time, l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else null, if (l.hasAccuracy()) l.accuracy else null)
    }

    @Deprecated("Required below API 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(this) }
        prefs.edit().remove("id").apply()
        recording.value = null
        super.onDestroy()
    }
}
