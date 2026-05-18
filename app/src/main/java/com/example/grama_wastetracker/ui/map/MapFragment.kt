package com.example.grama_wastetracker.ui.map

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.grama_wastetracker.MainActivity
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.FragmentMapBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private var truckMarker: Marker? = null
    private val reportMarkers = mutableListOf<Marker>()
    private var proximityNotificationSent = false
    private var previousDistMeters = Int.MAX_VALUE
    private var truckHasPassed = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var userLocation: LatLng? = null

    private val NOTIF_CHANNEL_ID = "tractor_alerts"
    private val NOTIF_ID = 1001

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startLocationUpdates()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        createNotificationChannel()
        (childFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment)
            .getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true
        
        // Move the My Location button to the bottom right, above the zoom controls
        try {
            val mapView = childFragmentManager.findFragmentById(R.id.map_container)?.view
            val locationButton = (mapView?.findViewById<View>(Integer.parseInt("1"))?.parent as? View)?.findViewById<View>(Integer.parseInt("2"))
            locationButton?.let {
                val rlp = it.layoutParams as android.widget.RelativeLayout.LayoutParams
                rlp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP, 0)
                rlp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM, android.widget.RelativeLayout.TRUE)
                rlp.setMargins(0, 0, 30, 280) // 280px from bottom to sit above zoom controls
                it.layoutParams = rlp
            }
        } catch (e: Exception) {
            // Ignore if view hierarchy changes in future map versions
        }

        binding.statusCard.setOnClickListener {
            truckMarker?.let { marker ->
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 15f))
            }
        }

        checkLocationPermission()
        loadReportedBlackspots()
        setupTractorTracking()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        mMap.isMyLocationEnabled = true

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                userLocation = LatLng(loc.latitude, loc.longitude)
                // If it's the first fix, move camera
                if (truckMarker == null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation!!, 15f))
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
    }

    private fun setupTractorTracking() {
        FirebaseDatabase.getInstance().reference
            .child("tractors").child("tractor_1")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null || !snapshot.exists()) return
                    val lat    = snapshot.child("lat").getValue(Double::class.java) ?: return
                    val lng    = snapshot.child("lng").getValue(Double::class.java) ?: return
                    val status = snapshot.child("status").getValue(String::class.java) ?: "Moving"
                    val pos    = LatLng(lat, lng)
                    
                    updateTruckMarker(pos)
                    updateDistanceUI(pos, status)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateTruckMarker(position: LatLng) {
        if (truckMarker == null) {
            truckMarker = mMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(getString(R.string.garbage_truck))
                    .icon(tractorIcon())
            )
        } else {
            animateMarkerSmooth(truckMarker!!, position)
        }
    }

    private fun animateMarkerSmooth(marker: Marker, to: LatLng) {
        val handler = Handler(Looper.getMainLooper())
        val from = marker.position
        val startTime = SystemClock.uptimeMillis()
        val duration = 1500L
        val interp = AccelerateDecelerateInterpolator()
        handler.post(object : Runnable {
            override fun run() {
                val t = interp.getInterpolation(
                    ((SystemClock.uptimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                )
                marker.position = LatLng(
                    t * to.latitude  + (1 - t) * from.latitude,
                    t * to.longitude + (1 - t) * from.longitude
                )
                if (t < 1f) handler.postDelayed(this, 16)
            }
        })
    }

    private fun tractorIcon(): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_tractor)!!
        // Scale up ~5% for a slightly larger, more realistic map vehicle appearance
        val size = (drawable.intrinsicWidth * 1.05f).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun loadReportedBlackspots() {
        FirebaseDatabase.getInstance().getReference("reports")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    reportMarkers.forEach { it.remove() }
                    reportMarkers.clear()
                    for (child in snapshot.children) {
                        val lat = child.child("lat").getValue(Double::class.java) ?: continue
                        val lng = child.child("lng").getValue(Double::class.java) ?: continue
                        mMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(lat, lng))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                        )?.let { reportMarkers.add(it) }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateDistanceUI(truckPos: LatLng, tractorStatus: String) {
        val statusLabel = when {
            tractorStatus.contains("Collect") -> getString(R.string.status_collecting)
            tractorStatus.contains("Idle") -> getString(R.string.status_idle)
            tractorStatus == "Offline" -> "Offline"
            else -> getString(R.string.status_en_route)
        }

        val uLoc = userLocation
        if (uLoc == null) {
            binding.truckStatusText.text = statusLabel
            binding.arrivalTimeText.text = "--"
            if (truckMarker == null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(truckPos, 15f))
            }
            return
        }

        val dist = FloatArray(1)
        Location.distanceBetween(truckPos.latitude, truckPos.longitude, uLoc.latitude, uLoc.longitude, dist)
        val distMeters = dist[0].toInt()
        val etaMin = (distMeters / 1.2 / 60).toInt().coerceAtLeast(1)

        if (distMeters < 100 && !proximityNotificationSent) {
            sendProximityNotification()
            proximityNotificationSent = true
        }
        if (distMeters > 150) proximityNotificationSent = false

        // Detect when truck has passed: it was close (<100m) and now is moving away (increasing distance)
        if (previousDistMeters < 120 && distMeters > previousDistMeters + 20) {
            truckHasPassed = true
        }
        // Reset "passed" flag if truck comes close again
        if (distMeters < 80) truckHasPassed = false
        previousDistMeters = distMeters

        val finalStatus = when {
            tractorStatus.contains("Collect") -> getString(R.string.status_collecting)
            tractorStatus.contains("Idle") -> getString(R.string.status_idle)
            truckHasPassed && distMeters > 120 -> "Collection Done · Moving Away"
            distMeters < 100 -> getString(R.string.status_arriving_now)
            distMeters < 300 -> getString(R.string.status_arriving_soon)
            distMeters < 600 -> getString(R.string.status_nearby)
            else -> getString(R.string.status_en_route)
        }

        // Format distance: show km when >= 1000m, metres otherwise
        val distanceText = if (distMeters >= 1000) {
            String.format("%.1f km away • ETA %d min", distMeters / 1000f, etaMin)
        } else {
            "${distMeters}m away • ETA ${etaMin} min"
        }

        binding.truckStatusText.text = finalStatus
        binding.arrivalTimeText.text = distanceText

        // Update UI dot color
        val colorRes = when {
            tractorStatus.contains("Offline") -> android.R.color.darker_gray
            truckHasPassed && distMeters > 120 -> android.R.color.darker_gray
            tractorStatus.contains("Collect") -> android.R.color.holo_orange_light
            else -> android.R.color.holo_green_light
        }
        binding.statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun sendProximityNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val pi = PendingIntent.getActivity(requireContext(), 0,
            Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(requireContext(), NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)

        with(NotificationManagerCompat.from(requireContext())) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(NOTIF_ID, builder.build())
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notif_channel_name)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIF_CHANNEL_ID, name, importance)
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onPause() {
        super.onPause()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}