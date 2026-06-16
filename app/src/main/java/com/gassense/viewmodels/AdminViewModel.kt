package com.gassense.viewmodels

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdminDeviceStatus(
    val id: String = "",
    val name: String = "Unknown Device",
    val gasPpm: Float = 0f,
    val pressurePsi: Float = 0f,
    val hazardLevel: String = "SAFE",
    val autoShutoff: Boolean = false,
    val lastSeen: Long = 0L
)

class AdminViewModel : ViewModel() {

    private val database = FirebaseDatabase.getInstance("https://gas-sense-95d60-default-rtdb.asia-southeast1.firebasedatabase.app/")
    private val _fleetStatus = MutableStateFlow<List<AdminDeviceStatus>>(emptyList())
    val fleetStatus: StateFlow<List<AdminDeviceStatus>> = _fleetStatus.asStateFlow()

    private var fleetListener: ValueEventListener? = null

    init {
        startListening()
    }

    private fun startListening() {
        val ref = database.getReference("devices")
        fleetListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val devices = mutableListOf<AdminDeviceStatus>()
                for (deviceSnapshot in snapshot.children) {
                    val id = deviceSnapshot.key ?: continue
                    val latest = deviceSnapshot.child("latest")
                    
                    val status = AdminDeviceStatus(
                        id = id,
                        name = latest.child("name").getValue(String::class.java) ?: "Device $id",
                        gasPpm = latest.child("gasPpm").getValue(Float::class.java) ?: 0f,
                        pressurePsi = latest.child("pressurePsi").getValue(Float::class.java) ?: 0f,
                        hazardLevel = latest.child("hazardLevel").getValue(String::class.java) ?: "UNKNOWN",
                        autoShutoff = latest.child("autoShutoff").getValue(Boolean::class.java) ?: false,
                        lastSeen = latest.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                    devices.add(status)
                }
                _fleetStatus.value = devices
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(fleetListener!!)
    }

    fun toggleShutoff(deviceId: String, currentStatus: Boolean) {
        database.getReference("devices/$deviceId/latest/autoShutoff").setValue(!currentStatus)
    }

    override fun onCleared() {
        super.onCleared()
        fleetListener?.let { database.reference.removeEventListener(it) }
    }
}
