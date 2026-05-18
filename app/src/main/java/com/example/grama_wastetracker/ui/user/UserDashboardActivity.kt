package com.example.grama_wastetracker.ui.user

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.grama_wastetracker.databinding.ActivityUserDashboardBinding
import com.example.grama_wastetracker.MainActivity

class UserDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUserDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Track button → opens map
        binding.btnTrack.setOnClickListener {
            val intent = Intent(this, com.example.grama_wastetracker.ui.user.UserActivity::class.java)
            startActivity(intent)
        }

        binding.btnReport.setOnClickListener {
            val intent = Intent(this, com.example.grama_wastetracker.MainActivity::class.java)
            intent.putExtra("open_report", true)
            startActivity(intent)
        }

        binding.btnGuide.setOnClickListener {
            val intent = Intent(this, com.example.grama_wastetracker.MainActivity::class.java)
            intent.putExtra("open_guide", true)
            startActivity(intent)
        }
        // Language (we'll implement later)
        binding.btnLanguage.setOnClickListener {
            // do nothing for now
        }
    }
}