package com.example.grama_wastetracker

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.grama_wastetracker.databinding.ActivityMainBinding
import com.example.grama_wastetracker.utils.LocaleHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val simHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var simLat = 0.0
    private var simLng = 0.0
    private var isSimInitialized = false

    private val simRunnable = object : Runnable {
        override fun run() {
            if (!isSimInitialized) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this@MainActivity)
                        .lastLocation.addOnSuccessListener { loc ->
                            if (loc != null && !isSimInitialized) {
                                simLat = loc.latitude + 0.0018
                                simLng = loc.longitude + 0.0018
                                isSimInitialized = true
                            }
                        }
                }
            }

            if (isSimInitialized) {
                simLat -= 0.00005
                simLng -= 0.00005
                com.google.firebase.database.FirebaseDatabase.getInstance().getReference("tractors/tractor_1")
                    .setValue(mapOf(
                        "lat" to simLat,
                        "lng" to simLng,
                        "status" to "Moving",
                        "speed" to 20,
                        "timestamp" to System.currentTimeMillis()
                    ))
            }
            simHandler.postDelayed(this, 2500)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController


        binding.bottomNavigation.setupWithNavController(navController)


        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_live_map, R.id.nav_report, R.id.nav_guide)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)


        if (intent.getBooleanExtra("open_map", false)) {
            navController.navigate(R.id.nav_live_map)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        simHandler.post(simRunnable)
    }

    override fun onStop() {
        super.onStop()
        simHandler.removeCallbacks(simRunnable)
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("tractors/tractor_1/status").setValue("Offline")
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_signout, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_sign_out) {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            startActivity(android.content.Intent(this, com.example.grama_wastetracker.ui.auth.LoginActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}