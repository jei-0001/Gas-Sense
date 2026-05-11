package com.gassense.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gassense.R
import com.gassense.databinding.ItemDeviceBinding
import com.gassense.models.DeviceItem

class DevicesAdapter(private val devices: List<DeviceItem>) :
    RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        val ctx = holder.binding.root.context

        with(holder.binding) {
            tvDeviceName.text = device.name
            tvDeviceId.text = device.deviceId
            tvLastSynced.text = ctx.getString(R.string.last_synced, device.lastSynced)

            if (device.isOnline) {
                chipStatus.text = ctx.getString(R.string.status_online)
                chipStatus.setTextColor(ContextCompat.getColor(ctx, R.color.safe_green))
                chipStatus.chipBackgroundColor = ContextCompat.getColorStateList(ctx, R.color.safe_green_dim)
            } else {
                chipStatus.text = ctx.getString(R.string.status_offline)
                chipStatus.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                chipStatus.chipBackgroundColor = ContextCompat.getColorStateList(ctx, R.color.card_medium)
            }

            ivOverflowMenu.setOnClickListener { view ->
                val popup = PopupMenu(ctx, view)
                popup.menu.apply {
                    add(ctx.getString(R.string.menu_rename))
                    add(ctx.getString(R.string.menu_set_primary))
                    add(ctx.getString(R.string.menu_remove))
                }
                popup.show()
            }
        }
    }

    override fun getItemCount() = devices.size
}
