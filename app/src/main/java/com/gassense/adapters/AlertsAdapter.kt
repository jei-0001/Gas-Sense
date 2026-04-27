package com.gassense.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gassense.R
import com.gassense.databinding.ItemAlertBinding
import com.gassense.models.AlertItem

class AlertsAdapter(private val alerts: List<AlertItem>) :
    RecyclerView.Adapter<AlertsAdapter.AlertViewHolder>() {

    inner class AlertViewHolder(val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = alerts[position]
        val ctx = holder.binding.root.context

        with(holder.binding) {
            tvAlertTitle.text = alert.title
            tvAlertDesc.text = alert.description
            tvTimestamp.text = alert.timestamp

            when (alert.severity) {
                AlertItem.Severity.CRITICAL -> {
                    ivAlertIcon.setImageResource(R.drawable.ic_warning_triangle)
                    ivAlertIcon.imageTintList = ContextCompat.getColorStateList(ctx, R.color.critical_red)
                    ivAlertIcon.parent.let { parent ->
                        if (parent is android.widget.FrameLayout) {
                            parent.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.critical_red_dim)
                        }
                    }
                    chipSeverity.text = "CRITICAL"
                    chipSeverity.setTextColor(ContextCompat.getColor(ctx, R.color.critical_red))
                    chipSeverity.chipBackgroundColor = ContextCompat.getColorStateList(ctx, R.color.critical_red_dim)
                    chipSeverity.chipStrokeColor = ContextCompat.getColorStateList(ctx, R.color.critical_red)
                }
                AlertItem.Severity.WARNING -> {
                    ivAlertIcon.setImageResource(R.drawable.ic_warning_triangle)
                    ivAlertIcon.imageTintList = ContextCompat.getColorStateList(ctx, R.color.warning_yellow)
                    chipSeverity.text = "WARNING"
                    chipSeverity.setTextColor(ContextCompat.getColor(ctx, R.color.warning_yellow))
                    chipSeverity.chipBackgroundColor = ContextCompat.getColorStateList(ctx, R.color.warning_yellow_dim)
                    chipSeverity.chipStrokeColor = ContextCompat.getColorStateList(ctx, R.color.warning_yellow)
                }
            }

            chipAutoShutoff.visibility = if (alert.autoShutoffTriggered) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = alerts.size
}
