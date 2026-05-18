package com.example.grama_wastetracker.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.FragmentHomeBinding
import com.example.grama_wastetracker.utils.LocaleHelper
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Live user location for accurate distance calculation
    private var userLocation: Location? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Language Toggle
        val isKannada = LocaleHelper.getLanguage(requireContext()) == "kn"
        binding.btnLanguageToggle.text = if (isKannada) getString(R.string.lang_toggle_en) else getString(R.string.lang_toggle_kn)

        binding.btnLanguageToggle.setOnClickListener {
            val current = LocaleHelper.getLanguage(requireContext())
            val newLang = if (current == "kn") "en" else "kn"
            LocaleHelper.setLocale(requireContext(), newLang)
            requireActivity().recreate()
        }

        // Navigation
        binding.btnOpenMap.setOnClickListener  {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_live_map
        }
        binding.cardLiveMap.setOnClickListener {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_live_map
        }
        binding.cardReport.setOnClickListener  {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_report
        }
        binding.cardGuide.setOnClickListener   {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_guide
        }

        fetchUserLocation()
        observeTractorStatus()
    }

    private fun fetchUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(requireActivity())
                .lastLocation.addOnSuccessListener { loc -> userLocation = loc }
        }
    }

    private fun observeTractorStatus() {
        FirebaseDatabase.getInstance().getReference("tractors/tractor_1")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null || !snapshot.exists()) return

                    val lat = snapshot.child("lat").getValue(Double::class.java) ?: return
                    val lng = snapshot.child("lng").getValue(Double::class.java) ?: return
                    val status = snapshot.child("status").getValue(String::class.java) ?: ""

                    // Distance calculation using real user location if available
                    val result = FloatArray(1)
                    val uLat = userLocation?.latitude ?: 12.9716 // Fallback to demo
                    val uLng = userLocation?.longitude ?: 77.5946
                    
                    Location.distanceBetween(lat, lng, uLat, uLng, result)
                    val dist = result[0].toInt()
                    val eta = (dist / 1.2 / 60).toInt().coerceAtLeast(1)

                    val statusLabel = when {
                        status.contains("Collect") -> getString(R.string.status_collecting)
                        status.contains("Idle")    -> getString(R.string.status_idle)
                        dist < 200                  -> getString(R.string.status_arriving_soon)
                        dist < 600                  -> getString(R.string.status_nearby)
                        else                        -> getString(R.string.status_en_route)
                    }

                    binding.tvHomeTractorStatus.text = statusLabel
                    val distanceText = if (dist >= 1000) {
                        String.format("%.1f km away • ETA %d min", dist / 1000f, eta)
                    } else {
                        "${dist}m away • ETA ${eta} min"
                    }
                    binding.tvHomeTractorDist.text = distanceText
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}