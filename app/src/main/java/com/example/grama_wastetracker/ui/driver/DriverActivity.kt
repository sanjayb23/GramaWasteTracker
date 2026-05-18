package com.example.grama_wastetracker.ui.driver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.ActivityDriverBinding
import com.example.grama_wastetracker.services.TrackingService
import com.example.grama_wastetracker.ui.auth.LoginActivity
import com.example.grama_wastetracker.utils.LocaleHelper
import com.example.grama_wastetracker.utils.NetworkUtils
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DriverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverBinding
    private var isTracking = false
    private var startTime: Long = 0
    private var stopsCount = 0
    private var distanceMeters = 0.0
    private val handler = Handler(Looper.getMainLooper())

    private val blackspots = mutableListOf<Pair<String, Map<String, Any?>>>()
    private lateinit var blackspotAdapter: BlackspotDriverAdapter

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: "Moving"
            val speed  = intent?.getIntExtra("speed", 0) ?: 0
            binding.tvLastUpdate.text = "just now"
            val emoji = when {
                status.contains("Collect") -> "🟡 Collecting"
                status.contains("Idle")    -> "⏸ Idle"
                else                       -> "🟢 Moving ${if (speed > 0) "${speed}km/h" else ""}"
            }
            binding.chipTractorId.text = "🚜 TRK-001  •  $emoji"
            distanceMeters += (8..25).random()
            if (status.contains("Collect")) stopsCount++
            binding.tvDistanceCovered.text = "%.1f km".format(distanceMeters / 1000.0)
            binding.tvStopsCompleted.text = "$stopsCount"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (!NetworkUtils.isOnline(this)) {
            Snackbar.make(binding.root, "⚠️ No internet — tracking data won't sync", Snackbar.LENGTH_LONG).show()
        }

        blackspotAdapter = BlackspotDriverAdapter(blackspots) { markBlackspotCollected(it) }
        binding.rvBlackspots.layoutManager = LinearLayoutManager(this)
        binding.rvBlackspots.adapter = blackspotAdapter

        binding.btnToggleTracking.setOnClickListener {
            if (isTracking) stopTracking()
            else if (!isGpsEnabled()) {
                Snackbar.make(binding.root, "⚠️ GPS is disabled", Snackbar.LENGTH_LONG)
                    .setAction("Enable") {
                        startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }.show()
            } else startTracking()
        }
        loadAssignedBlackspots()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_signout, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sign_out) {
            if (isTracking) stopTracking()
            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Sign out of Driver mode?")
                .setPositiveButton("Sign Out") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }.setNegativeButton("Cancel", null).show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(locationReceiver,
            IntentFilter("com.example.grama_wastetracker.LOCATION_UPDATE"),
            Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(locationReceiver) } catch (_: Exception) {}
    }

    private fun loadAssignedBlackspots() {
        FirebaseDatabase.getInstance().getReference("reports")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    blackspots.clear()
                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java) ?: "pending"
                        if (status == "assigned") {
                    blackspots.add(Pair(child.key ?: "", mapOf(
                                "location"  to child.child("location").getValue(String::class.java),
                                "timestamp" to child.child("timestamp").getValue(Long::class.java),
                                "lat"       to child.child("lat").getValue(Double::class.java),
                                "lng"       to child.child("lng").getValue(Double::class.java)
                            )))
                        }
                    }
                    blackspotAdapter.notifyDataSetChanged()
                    binding.tvNoBlackspots.visibility = if (blackspots.isEmpty()) View.VISIBLE else View.GONE
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun markBlackspotCollected(reportId: String) {
        FirebaseDatabase.getInstance().getReference("reports/$reportId/status")
            .setValue("driver_confirmed")
        Snackbar.make(binding.root, "✅ Marked collected — awaiting admin confirmation", Snackbar.LENGTH_LONG).show()
    }

    private fun startTracking() {
        isTracking = true; startTime = System.currentTimeMillis()
        distanceMeters = 0.0; stopsCount = 0
        binding.tvTrackingStatus.text = getString(R.string.status_tracking)
        binding.tvTrackingStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        binding.btnToggleTracking.text = getString(R.string.btn_stop_tracking)
        startTimer()
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
    }

    private fun stopTracking() {
        isTracking = false
        binding.tvTrackingStatus.text = getString(R.string.status_not_tracking)
        binding.tvTrackingStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        binding.btnToggleTracking.text = getString(R.string.btn_start_tracking)
        binding.tvRuntime.text = "00:00"; binding.tvLastUpdate.text = "--"
        handler.removeCallbacksAndMessages(null)
        stopService(Intent(this, TrackingService::class.java))
    }

    private fun startTimer() {
        handler.post(object : Runnable {
            override fun run() {
                val e = System.currentTimeMillis() - startTime
                binding.tvRuntime.text = "%02d:%02d".format((e/60000)%60, (e/1000)%60)
                if (isTracking) handler.postDelayed(this, 1000)
            }
        })
    }

    private fun isGpsEnabled() =
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .isProviderEnabled(LocationManager.GPS_PROVIDER)
}