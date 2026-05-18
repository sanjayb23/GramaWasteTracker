package com.example.grama_wastetracker.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.grama_wastetracker.R
import com.example.grama_wastetracker.databinding.ItemReportBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportListAdapter(
    private val reports: List<BlackspotReport>,
    private val onAssign:   (id: String) -> Unit,
    private val onComplete: (id: String) -> Unit,
    private val onNavigate: (lat: Double, lng: Double) -> Unit
) : RecyclerView.Adapter<ReportListAdapter.VH>() {

    private val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    inner class VH(val b: ItemReportBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = reports[position]
        with(holder.b) {
            tvReportLocation.text = "📍 ${r.location}"
            tvReportTime.text     = if (r.timestamp == 0L) "--" else sdf.format(Date(r.timestamp))
            tvReportId.text       = "ID: ${r.id.takeLast(6).uppercase()}"

            // Load report photo thumbnail with Glide
            if (r.imageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(r.imageUrl)
                    .placeholder(R.drawable.ic_report)
                    .error(R.drawable.ic_report)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivReportThumb)
                ivReportThumb.visibility = View.VISIBLE
            } else {
                ivReportThumb.visibility = View.GONE
            }

            // Status badge colour
            when (r.status) {
                "assigned"         -> { tvReportStatus.text = "🟡 Assigned";      tvReportStatus.setTextColor(0xFFE65100.toInt()) }
                "driver_confirmed" -> { tvReportStatus.text = "🔵 Driver Done";   tvReportStatus.setTextColor(0xFF1565C0.toInt()) }
                "completed"        -> { tvReportStatus.text = "🟢 Completed";     tvReportStatus.setTextColor(0xFF2E7D32.toInt()) }
                else               -> { tvReportStatus.text = "⏳ Pending";       tvReportStatus.setTextColor(0xFFBF360C.toInt()) }
            }

            btnAssign.visibility   = if (r.status == "pending")          View.VISIBLE else View.GONE
            btnComplete.visibility = if (r.status == "driver_confirmed") View.VISIBLE else View.GONE

            btnAssign.setOnClickListener   { onAssign(r.id) }
            btnComplete.setOnClickListener { onComplete(r.id) }
            btnNavigate.setOnClickListener { onNavigate(r.lat, r.lng) }
        }
    }

    override fun getItemCount() = reports.size
}
