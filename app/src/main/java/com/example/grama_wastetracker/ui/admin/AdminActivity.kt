package com.example.grama_wastetracker.ui.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.ActivityAdminBinding
import com.example.grama_wastetracker.ui.auth.LoginActivity
import com.example.grama_wastetracker.utils.LocaleHelper
import com.example.grama_wastetracker.utils.NetworkUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var mMap: GoogleMap
    private val reports = mutableListOf<BlackspotReport>()
    private lateinit var adapter: ReportListAdapter
    private val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        // Offline warning
        if (!NetworkUtils.isOnline(this)) {
            Snackbar.make(binding.root, "⚠️ No internet — data may be outdated", Snackbar.LENGTH_LONG).show()
        }

        adapter = ReportListAdapter(
            reports,
            onAssign   = { id -> assignToDriver(id) },
            onComplete = { id -> markCompleted(id) },
            onNavigate = { lat, lng -> openNavigation(lat, lng) }
        )
        binding.rvReports.layoutManager = LinearLayoutManager(this)
        binding.rvReports.adapter = adapter

        (supportFragmentManager.findFragmentById(R.id.admin_map_container) as SupportMapFragment)
            .getMapAsync(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_signout, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sign_out) {
            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Sign out of Admin Panel?")
                .setPositiveButton("Sign Out") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                .setNegativeButton("Cancel", null).show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(12.9716, 77.5946), 13f))
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setOnInfoWindowClickListener { marker ->
            val tag = marker.tag
            if (tag is Pair<*, *>) {
                val lat = tag.first as? Double ?: return@setOnInfoWindowClickListener
                val lng = tag.second as? Double ?: return@setOnInfoWindowClickListener
                openNavigation(lat, lng)
            }
        }
        loadReports()
    }

    private fun loadReports() {
        FirebaseDatabase.getInstance().getReference("reports")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    reports.clear()
                    mMap.clear()
                    for (child in snapshot.children) {
                        val lat  = child.child("lat").getValue(Double::class.java)    ?: continue
                        val lng  = child.child("lng").getValue(Double::class.java)    ?: continue
                        val loc  = child.child("location").getValue(String::class.java) ?: "Unknown"
                        val ts   = child.child("timestamp").getValue(Long::class.java)  ?: 0L
                        val st   = child.child("status").getValue(String::class.java)   ?: "pending"
                        val imgUrl = child.child("image_url").getValue(String::class.java) ?: ""
                        reports.add(BlackspotReport(child.key ?: "", lat, lng, loc, ts, st, imgUrl))

                        val marker = mMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(lat, lng))
                                .title("📍 $loc")
                                .snippet("${statusLabel(st)} • ${if (ts==0L) "--" else sdf.format(Date(ts))}")
                                .icon(BitmapDescriptorFactory.defaultMarker(pinHue(st)))
                        )
                        marker?.tag = Pair(lat, lng)
                    }
                    adapter.notifyDataSetChanged()
                    val total = reports.size; val pending = reports.count { r -> r.status == "pending" }
                    binding.tvReportCount.text = "$total Reports  •  $pending Pending"
                    binding.tvNoReports.visibility = if (total == 0) View.VISIBLE else View.GONE
                    if (reports.isNotEmpty()) mMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(reports[0].lat, reports[0].lng), 14f))
                }
                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(binding.root, "DB error: ${error.message}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    private fun assignToDriver(reportId: String) {
        FirebaseDatabase.getInstance().getReference("reports/$reportId").updateChildren(
            mapOf("status" to "assigned", "assignedAt" to System.currentTimeMillis())
        ).addOnSuccessListener {
            Snackbar.make(binding.root, "✅ Assigned to driver", Snackbar.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Snackbar.make(binding.root, "Failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun markCompleted(reportId: String) {
        val report = reports.find { r -> r.id == reportId } ?: return
        if (report.status != "driver_confirmed") {
            Snackbar.make(binding.root, "⚠️ Cannot complete — driver hasn't confirmed yet", Snackbar.LENGTH_LONG).show()
            return
        }
        FirebaseDatabase.getInstance().getReference("reports/$reportId").updateChildren(
            mapOf("status" to "completed", "completedAt" to System.currentTimeMillis())
        ).addOnSuccessListener {
            Snackbar.make(binding.root, "🎉 Marked as completed!", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openNavigation(lat: Double, lng: Double) {
        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        startActivity(if (intent.resolveActivity(packageManager) != null) intent
        else Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
    }

    private fun statusLabel(s: String) = when(s) {
        "assigned" -> "🟡 Assigned"; "driver_confirmed" -> "🔵 Driver Done"
        "completed" -> "🟢 Completed"; else -> "🔴 Pending"
    }
    private fun pinHue(s: String) = when(s) {
        "completed" -> BitmapDescriptorFactory.HUE_GREEN
        "driver_confirmed" -> BitmapDescriptorFactory.HUE_AZURE
        "assigned" -> BitmapDescriptorFactory.HUE_YELLOW
        else -> BitmapDescriptorFactory.HUE_ORANGE
    }
}
