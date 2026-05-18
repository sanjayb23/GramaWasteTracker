package com.example.grama_wastetracker.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.grama_wastetracker.MainActivity
import com.example.grama_wastetracker.databinding.ActivityRegisterBinding
import com.example.grama_wastetracker.utils.LocaleHelper
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth = FirebaseAuth.getInstance()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnRegister.setOnClickListener { doRegister() }
        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun doRegister() {
        val name     = binding.etName.text.toString().trim()
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm  = binding.etConfirm.text.toString()

        if (name.isEmpty())      { binding.tilName.error = "Required"; return }
        if (email.isEmpty())     { binding.tilEmail.error = "Required"; return }
        if (password.length < 6) { binding.tilPassword.error = "Min 6 characters"; return }
        if (password != confirm)  { binding.tilConfirm.error = "Passwords do not match"; return }

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                FirebaseDatabase.getInstance().getReference("users/$uid").setValue(
                    mapOf(
                        "role" to "resident",
                        "name" to name,
                        "email" to email,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).addOnCompleteListener {
                    setLoading(false)
                    // Residents go straight to MainActivity
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Snackbar.make(binding.root, "Registration failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun setLoading(on: Boolean) {
        binding.progressRegister.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !on
    }
}