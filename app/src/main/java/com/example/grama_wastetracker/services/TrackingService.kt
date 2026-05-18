package com.example.grama_wastetracker.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.grama_wastetracker.R
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * TrackingService — runs in foreground, reads real GPS from FusedLocationProviderClient,
 * and writes lat/lng/speed/status to Firebase every 5 seconds.
 */
class TrackingService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val driverUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: "tractor_1"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .setMaxUpdateDelayMillis(8000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val speed = (loc.speed * 3.6).toInt()    // m/s → km/h

                val status = when {
                    speed < 2  -> "Idle"
                    speed < 10 -> "Collecting…"
                    else       -> "Moving"
                }

                // Write real GPS to Firebase under tractors/tractor_1
                db.child("tractors").child("tractor_1").setValue(
                    mapOf(
                        "lat"        to loc.latitude,
                        "lng"        to loc.longitude,
                        "speed"      to speed,
                        "bearing"    to loc.bearing.toInt(),
                        "accuracy"   to loc.accuracy.toInt(),
                        "status"     to status,
                        "timestamp"  to System.currentTimeMillis(),
                        "driver_uid" to driverUid
                    )
                )

                // Broadcast to DriverActivity UI
                val broadcast = Intent("com.example.grama_wastetracker.LOCATION_UPDATE")
                broadcast.setPackage(packageName)
                broadcast.putExtra("status", status)
                broadcast.putExtra("speed", speed)
                sendBroadcast(broadcast)
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) fusedClient.removeLocationUpdates(locationCallback)
        db.child("tractors").child("tractor_1").child("status").setValue("Offline")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, "tracking_channel")
            .setContentTitle(getString(R.string.tracking_service_notification_title))
            .setContentText(getString(R.string.tracking_service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "tracking_channel", "Tractor Tracking", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Live tractor GPS updates" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}