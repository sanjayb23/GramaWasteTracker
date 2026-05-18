package com.example.grama_wastetracker.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.grama_wastetracker.MainActivity
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.ActivityLoginBinding
import com.example.grama_wastetracker.ui.admin.AdminActivity
import com.example.grama_wastetracker.ui.driver.DriverActivity
import com.example.grama_wastetracker.utils.LocaleHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val seededRoles = mapOf(
        "admin@grama.in"  to "admin",
        "driver@grama.in" to "driver"
    )

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            handleGoogleResult(result.data)
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnLogin.setOnClickListener  { doEmailLogin() }
        binding.btnGoogle.setOnClickListener { doGoogleSignIn() }
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun doEmailLogin() {
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (email.isEmpty())    { binding.tilEmail.error = "Required"; return }
        if (password.isEmpty()) { binding.tilPassword.error = "Required"; return }
        binding.tilEmail.error = null; binding.tilPassword.error = null
        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { r -> ensureAndRoute(r.user!!.uid, email) }
            .addOnFailureListener { e ->
                setLoading(false)
                Snackbar.make(binding.root, "Login failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun doGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail().build()
        googleSignInLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
    }

    private fun handleGoogleResult(data: Intent?) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            setLoading(true)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(account.idToken, null))
                .addOnSuccessListener { r -> ensureAndRoute(r.user!!.uid, r.user?.email ?: "") }
                .addOnFailureListener { e ->
                    setLoading(false)
                    Snackbar.make(binding.root, "Google sign-in failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
        } catch (e: ApiException) {
            Snackbar.make(binding.root, "Google error (${e.statusCode})", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun ensureAndRoute(uid: String, email: String) {
        val ref = FirebaseDatabase.getInstance().getReference("users/$uid")
        ref.get().addOnSuccessListener { snap ->
            val role: String
            if (!snap.exists()) {
                role = seededRoles[email.lowercase()] ?: "resident"
                ref.setValue(mapOf("role" to role, "email" to email))
            } else {
                role = snap.child("role").getValue(String::class.java) ?: "resident"
            }
            setLoading(false)
            navigateByRole(role)
        }.addOnFailureListener {
            setLoading(false)
            navigateByRole("resident")
        }
    }

    private fun navigateByRole(role: String) {
        val dest = when (role) {
            "admin"  -> AdminActivity::class.java
            "driver" -> DriverActivity::class.java
            else     -> MainActivity::class.java
        }
        startActivity(Intent(this, dest).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun setLoading(on: Boolean) {
        binding.progressLogin.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled       = !on
        binding.btnGoogle.isEnabled      = !on
        binding.tvRegister.isEnabled     = !on
    }
}