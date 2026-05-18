package com.example.grama_wastetracker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.grama_wastetracker.MainActivity
import com.example.grama_wastetracker.databinding.ActivitySplashBinding
import com.example.grama_wastetracker.ui.admin.AdminActivity
import com.example.grama_wastetracker.ui.auth.LoginActivity
import com.example.grama_wastetracker.ui.driver.DriverActivity
import com.example.grama_wastetracker.utils.LocaleHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({ checkAuthAndRoute() }, 1500)
    }

    private fun checkAuthAndRoute() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        FirebaseDatabase.getInstance().getReference("users/${user.uid}/role")
            .get()
            .addOnSuccessListener { snapshot ->
                val role = snapshot.getValue(String::class.java) ?: "resident"
                val destination = when (role) {
                    "admin" -> AdminActivity::class.java
                    "driver" -> DriverActivity::class.java
                    else -> MainActivity::class.java
                }
                startActivity(Intent(this, destination))
                finish()
            }
            .addOnFailureListener {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
    }
}