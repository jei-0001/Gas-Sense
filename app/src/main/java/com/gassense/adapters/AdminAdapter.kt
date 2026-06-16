package com.gassense.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gassense.R
import com.gassense.databinding.ItemFleetDeviceBinding
import com.gassense.viewmodels.AdminDeviceStatus

class AdminAdapter(private val onShutoffClick: (AdminDeviceStatus) -> Unit) :
    ListAdapter<AdminDeviceStatus, AdminAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFleetDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFleetDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AdminDeviceStatus) {
            binding.tvDeviceName.text = item.name
            binding.tvDeviceId.text = "ID: ${item.id}"
            binding.tvGasValue.text = "${item.gasPpm.toInt()} PPM"
            binding.tvPressureValue.text = "${item.pressurePsi.toInt()} PSI"
            binding.tvHazardLevel.text = item.hazardLevel

            val hazardColor = when (item.hazardLevel) {
                "SAFE" -> Color.parseColor("#22C55E")
                "WARNING" -> Color.parseColor("#EAB308")
                "CRITICAL" -> Color.parseColor("#EF4444")
                else -> Color.parseColor("#94A3B8")
            }
            binding.tvHazardLevel.setTextColor(hazardColor)

            binding.btnShutoffOverride.apply {
                text = if (item.autoShutoff) {
                    context.getString(R.string.admin_open_valve)
                } else {
                    context.getString(R.string.admin_shutoff)
                }
                backgroundTintList = if (item.autoShutoff) {
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#1A4A2E"))
                } else {
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#4A1A1A"))
                }
                setTextColor(if (item.autoShutoff) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
                setOnClickListener { onShutoffClick(item) }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AdminDeviceStatus>() {
        override fun areItemsTheSame(oldItem: AdminDeviceStatus, newItem: AdminDeviceStatus) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AdminDeviceStatus, newItem: AdminDeviceStatus) = oldItem == newItem
    }
}
