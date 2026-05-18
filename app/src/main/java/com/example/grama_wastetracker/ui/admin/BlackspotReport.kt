package com.example.grama_wastetracker.ui.admin

data class BlackspotReport(
    val id: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val location: String = "Unknown",
    val timestamp: Long = 0L,
    val status: String = "pending",
    val imageUrl: String = ""
)
