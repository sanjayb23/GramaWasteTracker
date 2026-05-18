package com.example.grama_wastetracker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.grama_wastetracker.MainActivity
import com.example.grama_wastetracker.databinding.ActivityModeSelectionBinding
import com.example.grama_wastetracker.ui.admin.AdminActivity
import com.example.grama_wastetracker.ui.driver.DriverActivity
import com.example.grama_wastetracker.utils.LocaleHelper

class ModeSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModeSelectionBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("reselect_mode", false)) {
            getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit { remove("user_mode") }
        }
        binding = ActivityModeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUserMode.setOnClickListener   { saveModeAndNavigate("user") }
        binding.btnDriverMode.setOnClickListener { saveModeAndNavigate("driver") }
        binding.btnAdminMode.setOnClickListener  {
            startActivity(Intent(this, AdminActivity::class.java))
        }
    }

    private fun saveModeAndNavigate(mode: String) {
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit { putString("user_mode", mode) }
        val intent = if (mode == "driver") Intent(this, DriverActivity::class.java)
                     else                   Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
