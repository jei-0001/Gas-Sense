package com.gassense.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.gassense.models.AlertItem
import com.gassense.utils.SmsService
import com.gassense.utils.ThresholdManager

data class LiveSensorData(
    val gasPpm: Float = 0f,
    val pressurePsi: Float = 0f,
    val tempC: Float = 0f,
    val humidityPct: Float = 0f,
    val hazardLevel: String = "SAFE",
    val autoShutoff: Boolean = false,
    val timestamp: Long = 0L
)

data class HistoryRecord(
    val gasPpm: Float = 0f,
    val pressurePsi: Float = 0f,
    val timestamp: Long = 0L
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FirebaseDatabase.getInstance("https://gas-sense-95d60-default-rtdb.asia-southeast1.firebasedatabase.app/")
    private val smsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastHazardLevel = "SAFE"
    private var lastSmsSentAt = 0L
    private var currentDeviceId = ""
    private var latestListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null
    private var alertsListener: ValueEventListener? = null
    private var rawGasListener: ValueEventListener? = null
    private var rawPressureListener: ValueEventListener? = null

    private val _sensorData = MutableStateFlow(LiveSensorData())
    val sensorData: StateFlow<LiveSensorData> = _sensorData.asStateFlow()

    private val _rawGasValue = MutableStateFlow(0)
    val rawGasValue: StateFlow<Int> = _rawGasValue.asStateFlow()

    private val _rawPressureValue = MutableStateFlow(0)
    val rawPressureValue: StateFlow<Int> = _rawPressureValue.asStateFlow()

    private val _historyData = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val historyData: StateFlow<List<HistoryRecord>> = _historyData.asStateFlow()

    private val _usageTips = MutableStateFlow<List<String>>(emptyList())
    val usageTips: StateFlow<List<String>> = _usageTips.asStateFlow()

    private val _alertsData = MutableStateFlow<List<AlertItem>>(emptyList())
    val alertsData: StateFlow<List<AlertItem>> = _alertsData.asStateFlow()

    fun startListening(deviceId: String) {
        stopListening()
        currentDeviceId = deviceId

        // 1. Listen for latest data
        val latestRef = database.getReference("devices/$deviceId/latest")
        latestListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(LiveSensorData::class.java)?.let {
                    _sensorData.value = it
                    checkAndSendCriticalSms(it.hazardLevel)
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
                generateUsageTips(records)
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

        // 4. Listen for Raw Calibration Data
        val rawGasRef = database.getReference("Sensors/Raw/MQ6")
        rawGasListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _rawGasValue.value = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        rawGasRef.addValueEventListener(rawGasListener!!)

        val rawPressureRef = database.getReference("Sensors/Raw/Pressure")
        rawPressureListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _rawPressureValue.value = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        rawPressureRef.addValueEventListener(rawPressureListener!!)
    }

    fun stopListening() {
        latestListener?.let { database.reference.removeEventListener(it) }
        historyListener?.let { database.reference.removeEventListener(it) }
        alertsListener?.let { database.reference.removeEventListener(it) }
        rawGasListener?.let { database.getReference("Sensors/Raw/MQ6").removeEventListener(it) }
        rawPressureListener?.let { database.getReference("Sensors/Raw/Pressure").removeEventListener(it) }
        latestListener = null
        historyListener = null
        alertsListener = null
        rawGasListener = null
        rawPressureListener = null
        lastHazardLevel = "SAFE"
    }

    private fun checkAndSendCriticalSms(hazardLevel: String) {
        if (hazardLevel == "CRITICAL" && lastHazardLevel != "CRITICAL") {
            val now = System.currentTimeMillis()
            val cooldownMs = 3 * 60 * 1000L
            if (now - lastSmsSentAt > cooldownMs) {
                val smsNumber = ThresholdManager.getSmsNumber(getApplication())
                if (smsNumber.isNotBlank()) {
                    lastSmsSentAt = now
                    smsScope.launch {
                        SmsService.sendCriticalAlert(smsNumber, currentDeviceId)
                    }
                }
            }
        }
        lastHazardLevel = hazardLevel
    }

    private fun generateUsageTips(records: List<HistoryRecord>) {
        val tips = mutableListOf<String>()
        if (records.isEmpty()) {
            _usageTips.value = emptyList()
            return
        }

        val avgGas = records.map { it.gasPpm }.average()
        val maxGas = records.maxOf { it.gasPpm }
        val avgPressure = records.map { it.pressurePsi }.average()

        // 1. High Usage Tip
        if (avgGas > 50) {
            tips.add("📈 Your gas usage is 15% higher this period. Consider checking for small leaks or burner efficiency.")
        }

        // 2. Safety Alert
        if (maxGas > 150) {
            tips.add("⚠️ Recent spikes detected. Ensure proper ventilation while cooking to maintain air quality.")
        }

        // 3. Low Pressure
        if (avgPressure < 30) {
            tips.add("🪫 Tank pressure is low. Based on your usage, you may need a refill within 3-5 days.")
        }

        // 4. Stable/Efficient
        if (tips.isEmpty()) {
            tips.add("✅ Your gas consumption is stable. Keep up the good maintenance!")
        }

        // Always add a general tip if there's room
        if (tips.size < 3) {
            tips.add("💡 Tip: Always ensure the blue flame on your burner; a yellow flame indicates incomplete combustion.")
        }

        _usageTips.value = tips
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
