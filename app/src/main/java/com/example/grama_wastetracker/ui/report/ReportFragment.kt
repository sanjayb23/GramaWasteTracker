package com.example.grama_wastetracker.ui.report

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.FragmentReportBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private var imageUri: Uri? = null
    private var reportLat = 12.9716
    private var reportLng = 77.5946

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    val tempFile = java.io.File(requireContext().cacheDir, "temp_upload.jpg")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    imageUri = Uri.fromFile(tempFile)
                    binding.imagePreview.setImageURI(uri)
                    fetchLiveLocationAndShowMetadata()
                } catch (e: Exception) {
                    com.google.android.material.snackbar.Snackbar.make(binding.root, "Error loading image", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                }
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startSubmit()
            else Snackbar.make(binding.root, R.string.camera_permission_denied, Snackbar.LENGTH_LONG).show()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        binding.btnUploadPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnSubmit.setOnClickListener { checkLocationPermission() }
        return binding.root
    }

    private fun fetchLiveLocationAndShowMetadata() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity())
                .lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        reportLat = loc.latitude
                        reportLng = loc.longitude
                    }
                    showMetadata()
                }.addOnFailureListener { showMetadata() }
        } else {
            showMetadata()
        }
    }

    private fun showMetadata() {
        binding.tvTimestamp.text = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        try {
            val addresses = Geocoder(requireContext(), Locale.getDefault()).getFromLocation(reportLat, reportLng, 1)
            binding.tvLocation.text = addresses?.firstOrNull()?.let { 
                "${it.subLocality ?: ""}, ${it.locality ?: ""}".trim(',',' ')
            } ?: "Ashok Nagar, Bengaluru"
        } catch (e: Exception) {
            binding.tvLocation.text = "Ashok Nagar, Bengaluru"
        }
        binding.layoutMetadata.visibility = View.VISIBLE
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) startSubmit()
        else requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun startSubmit() {
        if (imageUri == null) {
            com.google.android.material.snackbar.Snackbar.make(binding.root, R.string.select_photo_first, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anon"
        
        try {
            val bytes = requireContext().contentResolver.openInputStream(imageUri!!)?.readBytes()
            if (bytes != null) {
                // Compress image to save database space
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val baos = java.io.ByteArrayOutputStream()
                
                // Scale down if it's too large
                val maxDim = 800
                val scale = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val scaledBmp = if (scale < 1) android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
                
                scaledBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
                val base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                
                val dataUri = "data:image/jpeg;base64,$base64"
                saveReportToDb(uid, dataUri)
            } else {
                setLoading(false)
                com.google.android.material.snackbar.Snackbar.make(binding.root, "Error reading image file", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            setLoading(false)
            com.google.android.material.snackbar.Snackbar.make(binding.root, "Upload Failed: ${e.message}", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
        }
    }

    private fun saveReportToDb(uid: String, imageUrl: String) {
        val dbRef = FirebaseDatabase.getInstance().getReference("reports")
        val reportId = dbRef.push().key!!
        dbRef.child(reportId).setValue(mapOf(
            "lat" to reportLat, "lng" to reportLng, "timestamp" to System.currentTimeMillis(),
            "status" to "pending", "image_url" to imageUrl, "reported_by" to uid,
            "location" to binding.tvLocation.text.toString()
        )).addOnSuccessListener {
            setLoading(false)
            binding.cardSuccess.visibility = View.VISIBLE
        }.addOnFailureListener { e ->
            setLoading(false)
            Snackbar.make(binding.root, "Save Failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setLoading(on: Boolean) {
        binding.progressUpload.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !on
        binding.btnSubmit.text = if (on) getString(R.string.uploading) else getString(R.string.btn_submit_report)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}