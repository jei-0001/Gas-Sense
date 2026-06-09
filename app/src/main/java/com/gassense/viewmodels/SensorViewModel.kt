package com.gassense.viewmodels

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.gassense.models.AlertItem
//...
data class LiveSensorData(
    val gasPpm: Float = 0f,
    val pressurePsi: Float = 0f,
    val tempC: Float = 0f,
    val humidityPct: Float = 0f,
    val hazardLevel: String = "SAFE",
    val timestamp: Long = 0L
)

data class HistoryRecord(
    val gasPpm: Float = 0f,
    val pressurePsi: Float = 0f,
    val timestamp: Long = 0L
)

class SensorViewModel : ViewModel() {

    private val database = FirebaseDatabase.getInstance("https://gas-sense-95d60-default-rtdb.asia-southeast1.firebasedatabase.app/")
    private var latestListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null
    private var alertsListener: ValueEventListener? = null

    private val _sensorData = MutableStateFlow(LiveSensorData())
    val sensorData: StateFlow<LiveSensorData> = _sensorData.asStateFlow()

    private val _historyData = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val historyData: StateFlow<List<HistoryRecord>> = _historyData.asStateFlow()

    private val _alertsData = MutableStateFlow<List<AlertItem>>(emptyList())
    val alertsData: StateFlow<List<AlertItem>> = _alertsData.asStateFlow()

    fun startListening(deviceId: String) {
        stopListening()

        // 1. Listen for latest data
        val latestRef = database.getReference("devices/$deviceId/latest")
        latestListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(LiveSensorData::class.java)?.let {
                    _sensorData.value = it
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        latestRef.addValueEventListener(latestListener!!)

        // 2. Listen for history (last 50 records)
        val historyRef = database.getReference("devices/$deviceId/history").limitToLast(50)
        historyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val records = mutableListOf<HistoryRecord>()
                for (child in snapshot.children) {
                    child.getValue(HistoryRecord::class.java)?.let { records.add(it) }
                }
                _historyData.value = records
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        historyRef.addValueEventListener(historyListener!!)

        // 3. Listen for alerts (last 20 alerts)
        val alertsRef = database.getReference("devices/$deviceId/alerts").limitToLast(20)
        alertsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = mutableListOf<AlertItem>()
                for (child in snapshot.children) {
                    child.getValue(AlertItem::class.java)?.let { alerts.add(it) }
                }
                // Reverse to show newest first
                _alertsData.value = alerts.reversed()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        alertsRef.addValueEventListener(alertsListener!!)
    }

    fun stopListening() {
        latestListener?.let { database.reference.removeEventListener(it) }
        historyListener?.let { database.reference.removeEventListener(it) }
        alertsListener?.let { database.reference.removeEventListener(it) }
        latestListener = null
        historyListener = null
        alertsListener = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
