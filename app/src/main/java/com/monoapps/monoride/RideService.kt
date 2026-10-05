package com.monoapps.monoride

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.monoapps.monoride.data.Gpx
import java.io.File

/**
 * Foreground service that records GPS fixes to a GPX file while riding.
 * Runs independently of the UI so the screen can sleep.
 */
class RideService : Service(), LocationListener {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val CHANNEL = "ride"
        private const val NOTIFICATION_ID = 1

        // Ignore fixes worse than this; a bad fix would paint streets you never rode.
        private const val MAX_ACCURACY_M = 30f

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, RideService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RideService::class.java).setAction(ACTION_STOP))
        }
    }

    private var writer: Gpx.TrackWriter? = null
    private var rideFile: File? = null
    private var last: Location? = null
    private var distanceM = 0.0
    private var points = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (writer == null) startRecording()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startRecording() {
        val startedAt = System.currentTimeMillis()
        val file = Gpx.newRideFile(filesDir, startedAt)
        rideFile = file
        writer = Gpx.TrackWriter(file, file.name.removeSuffix(".gpx"))

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, this)
        } catch (e: SecurityException) {
            stopSelf()
            return
        }
        RideRecorder.state.value = RideRecorder.State(recording = true, startedAt = startedAt)
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return
        writer?.point(
            location.latitude, location.longitude,
            if (location.hasAltitude()) location.altitude else null,
            location.time,
        )
        last?.let { distanceM += it.distanceTo(location).toDouble() }
        last = location
        points++
        RideRecorder.state.value = RideRecorder.state.value.copy(
            distanceM = distanceM,
            points = points,
            lastLat = location.latitude,
            lastLon = location.longitude,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        writer?.close()
        writer = null
        // A ride with no fixes is noise; drop it.
        if (points == 0) rideFile?.delete()
        rideFile = null
        RideRecorder.state.value = RideRecorder.State()
        RideRecorder.finishedRides.value++
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ride recording", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Recording ride")
            .setContentText("MonoRide is logging your route")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }
}
