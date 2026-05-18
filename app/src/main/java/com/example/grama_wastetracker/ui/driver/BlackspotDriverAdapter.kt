package com.example.grama_wastetracker.ui.driver

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.ItemBlackspotDriverBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlackspotDriverAdapter(
    private val items: List<Pair<String, Map<String, Any?>>>,
    private val onCollected: (reportId: String) -> Unit
) : RecyclerView.Adapter<BlackspotDriverAdapter.VH>() {

    inner class VH(val binding: ItemBlackspotDriverBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemBlackspotDriverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (id, data) = items[position]
        val loc = data["location"] as? String ?: "Unknown location"
        val ts  = (data["timestamp"] as? Long) ?: 0L
        val lat = data["lat"] as? Double
        val lng = data["lng"] as? Double

        val context = holder.itemView.context

        holder.binding.tvBsLocation.text = "📍 $loc"
        holder.binding.tvBsTime.text     = context.getString(R.string.reported_at, formatTime(ts))

        holder.binding.btnMarkCollected.setOnClickListener { onCollected(id) }

        // Navigate button → opens Google Maps turn-by-turn to blackspot
        holder.binding.btnNavigateBs.setOnClickListener {
            if (lat != null && lng != null) {
                val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
                }
            }
        }
    }

    override fun getItemCount() = items.size

    private fun formatTime(ts: Long): String =
        if (ts == 0L) "--" else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ts))
}
