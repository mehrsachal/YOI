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

/**
 * Foreground location service. Keeps a GPS fix every 10 s with the screen off, for two purposes:
 * recording a track into the encrypted database, and feeding position reports to the CoT engine.
 */
class TrackService : Service(), LocationListener {
    companion object {
        const val INTERVAL_MS = 10_000L
        val recording = MutableStateFlow<Long?>(null)
        val fix = MutableStateFlow<Location?>(null)
        @Volatile private var wantRecord = false
        @Volatile private var wantPli = false

        fun record(ctx: Context, on: Boolean) { wantRecord = on; apply(ctx) }
        fun reportPosition(ctx: Context, on: Boolean) { if (wantPli != on) { wantPli = on; apply(ctx) } }
        private fun apply(ctx: Context) = runCatching { ctx.startForegroundService(Intent(ctx, TrackService::class.java)) }
    }

    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("track", getString(R.string.track), NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "track").setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(if (wantRecord) R.string.recording else R.string.reporting_position)).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(1, n)
            if (!listening) { getSystemService(LocationManager::class.java).requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, this); listening = true }
        } catch (e: Exception) { // permission missing or GPS absent
            wantRecord = false; wantPli = false
        }
        val session = (application as App).session
        if (wantRecord && recording.value == null && session != null) {
            val now = System.currentTimeMillis()
            recording.value = session.tracks.start(Dtg.format(now, (application as App).settings.zulu), now)
        } else if (!wantRecord) recording.value = null
        if (!wantRecord && !wantPli) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(l: Location) {
        fix.value = l
        val id = recording.value ?: return
        (application as App).session?.tracks?.add(id, l.time, l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else null, if (l.hasAccuracy()) l.accuracy else null)
    }

    @Deprecated("Required below API 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(this) }
        listening = false; recording.value = null
        super.onDestroy()
    }
}
