package com.example.grama_wastetracker.ui.user

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.animation.LinearInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.ActivityUserBinding
import com.example.grama_wastetracker.ui.ModeSelectionActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

class UserActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityUserBinding
    private lateinit var mMap: GoogleMap
    private var tractorMarker: Marker? = null

    private var isFirstCameraMove = true
    private var hasShownNearbyNotification = false

    private val userLocation = LatLng(12.9730, 77.5960)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) enableMyLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_container) as SupportMapFragment
        mapFragment.getMapAsync(this)


        createNotificationChannel()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.user_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_switch_mode) {
            switchMode()
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun switchMode() {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().remove("user_mode").apply()

        val intent = Intent(this, ModeSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        checkLocationPermission()

        mMap.addMarker(
            MarkerOptions()
                .position(userLocation)
                .title(getString(R.string.selected_location))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        simulateTractorMovement()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
    }

    private fun simulateTractorMovement() {
        val path = listOf(
            LatLng(12.9780, 77.6010),
            LatLng(12.9750, 77.5980),
            LatLng(12.9735, 77.5965),
            LatLng(12.9731, 77.5961)
        )

        var index = 0
        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                if (index < path.size) {
                    val pos = path[index]

                    if (tractorMarker == null) {
                        tractorMarker = mMap.addMarker(
                            MarkerOptions()
                                .position(pos)
                                .title(getString(R.string.garbage_truck))
                                .icon(bitmapDescriptorFromVector(R.drawable.ic_tractor))
                        )
                    } else {
                        animateMarker(tractorMarker!!, pos)
                    }

                    if (isFirstCameraMove) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                        isFirstCameraMove = false
                    }

                    val distance = FloatArray(1)
                    Location.distanceBetween(
                        pos.latitude, pos.longitude,
                        userLocation.latitude, userLocation.longitude,
                        distance
                    )

                    updateUI(distance[0])

                    index++
                    handler.postDelayed(this, 5000)
                }
            }
        }

        handler.post(runnable)
    }

    private fun animateMarker(marker: Marker, toPosition: LatLng) {
        val handler = Handler(Looper.getMainLooper())
        val start = marker.position
        val startTime = SystemClock.uptimeMillis()
        val duration = 2000L

        handler.post(object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - startTime).toFloat() / duration)
                val lat = t * toPosition.latitude + (1 - t) * start.latitude
                val lng = t * toPosition.longitude + (1 - t) * start.longitude
                marker.position = LatLng(lat, lng)

                if (t < 1.0) handler.postDelayed(this, 16)
            }
        })
    }

    private fun updateUI(distance: Float) {
        val statusText: String
        val color: Int

        when {
            distance < 150 -> {
                statusText = getString(R.string.status_arriving)
                color = ContextCompat.getColor(this, android.R.color.holo_green_dark)
                if (!hasShownNearbyNotification) {
                    showArrivalNotification()
                    hasShownNearbyNotification = true
                }
            }
            distance < 500 -> {
                statusText = getString(R.string.status_nearby)
                color = ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            }
            else -> {
                statusText = getString(R.string.status_far)
                color = ContextCompat.getColor(this, android.R.color.holo_red_dark)
                hasShownNearbyNotification = false
            }
        }

        binding.tvDistanceStatus.text = statusText
        binding.tvDistanceStatus.setTextColor(color)
        binding.statusIndicator.backgroundTintList =
            android.content.res.ColorStateList.valueOf(color)

        binding.tvUserArrivalAlert.text =
            getString(R.string.distance_away, distance.toInt())
    }

    private fun showArrivalNotification() {
        val notification = NotificationCompat.Builder(this, "ARRIVAL_CHANNEL")
            .setSmallIcon(R.drawable.ic_tractor)
            .setContentTitle("Tractor Arriving!")
            .setContentText(getString(R.string.arrival_alert))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ARRIVAL_CHANNEL",
                "Arrival Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun bitmapDescriptorFromVector(resId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(this, resId)!!
        val bitmap = createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}