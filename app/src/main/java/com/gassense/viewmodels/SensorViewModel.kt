package com.gassense.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.gassense.models.AlertItem
import com.gassense.models.AppThresholds
import com.gassense.utils.NetworkStatus
import com.gassense.utils.SafetyNotificationService
import com.gassense.utils.SmsService
import com.gassense.utils.ThresholdManager
import java.util.Locale

@IgnoreExtraProperties
data class LiveSensorData(
    val gasPpm: Float = 0f,
    val lelPercent: Float = 0f,
    val pressurePsi: Float = 0f,
    val tempC: Float = 0f,
    val humidityPct: Float = 0f,
    val weightKg: Float = 0f,
    val hazardLevel: String = "SAFE",
    val autoShutoff: Boolean = false,
    val valveOpen: Boolean = false,
    val valveOverride: String = "auto",
    val hazardLatched: Boolean = false,
    // Pressure-corroborated near-empty check (weight stays the primary quantity
    // signal — see DashboardFragment for how this is displayed alongside it).
    val expectedPressurePsi: Float = 0f,
    val nearEmptyCorroborated: Boolean = false,
    val timestamp: Long = 0L
)

// Matches the periodic snapshot the ESP32 firmware writes to
// devices/{id}/history/{epochMs} — field names must stay in sync with the sketch.
@IgnoreExtraProperties
data class HistoryRecord(
    val gasPpm: Float = 0f,
    val lelPercent: Float = 0f,
    val pressurePsi: Float = 0f,
    val tempC: Float = 0f,
    val humidityPct: Float = 0f,
    val weightKg: Float = 0f,
    val hazardLevel: String = "SAFE",
    val valveOpen: Boolean = false,
    val timestamp: Long = 0L
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FirebaseDatabase.getInstance("https://gas-sense-95d60-default-rtdb.asia-southeast1.firebasedatabase.app/")
    private val smsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastHazardLevel = "SAFE"
    private var lastSmsSentAt = 0L
    private var hasLoadedInitialAlerts = false
    private var lastNotifiedAlertTimestamp = 0L
    private var currentDeviceId = ""
    private var latestRef: DatabaseReference? = null
    private var historyRef: Query? = null
    private var alertsRef: Query? = null
    private var cloudConnectionRef: DatabaseReference? = null
    private var latestListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null
    private var alertsListener: ValueEventListener? = null
    private var cloudConnectionListener: ValueEventListener? = null

    private val _sensorData = MutableStateFlow(LiveSensorData())
    val sensorData: StateFlow<LiveSensorData> = _sensorData.asStateFlow()

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _historyData = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val historyData: StateFlow<List<HistoryRecord>> = _historyData.asStateFlow()

    private val _usageTips = MutableStateFlow<List<String>>(emptyList())
    val usageTips: StateFlow<List<String>> = _usageTips.asStateFlow()

    private val _alertsData = MutableStateFlow<List<AlertItem>>(emptyList())
    val alertsData: StateFlow<List<AlertItem>> = _alertsData.asStateFlow()

    fun startListening(deviceId: String) {
        if (currentDeviceId == deviceId && latestListener != null) return

        stopListening()
        currentDeviceId = deviceId
        hasLoadedInitialAlerts = false
        lastNotifiedAlertTimestamp = 0L
        startCloudConnectionListener()

        // 1. Listen for latest data
        latestRef = database.getReference("devices/$deviceId/latest")
        latestListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                android.util.Log.d("GasSenseDebug", "raw pressurePsi in snapshot = ${snapshot.child("pressurePsi").value}")
                snapshot.getValue(LiveSensorData::class.java)?.let {
                    android.util.Log.d("GasSenseDebug", "parsed LiveSensorData.pressurePsi = ${it.pressurePsi}, timestamp = ${it.timestamp}")
                    _sensorData.value = it
                    checkAndSendCriticalSms(it.hazardLevel)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        latestRef?.addValueEventListener(latestListener!!)

        // 2. Listen for history — firmware logs ~1 snapshot/min, so 500 records is ~8hrs
        historyRef = database.getReference("devices/$deviceId/history").limitToLast(500)
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
        historyRef?.addValueEventListener(historyListener!!)

        // 3. Listen for alerts (last 20 alerts)
        alertsRef = database.getReference("devices/$deviceId/alerts").limitToLast(20)
        alertsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = mutableListOf<AlertItem>()
                for (child in snapshot.children) {
                    child.getValue(AlertItem::class.java)?.let { alerts.add(it) }
                }
                val orderedAlerts = alerts.sortedBy { it.timestampMs }
                // Reverse to show newest first
                _alertsData.value = orderedAlerts.reversed()
                handleAlertNotifications(orderedAlerts)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        alertsRef?.addValueEventListener(alertsListener!!)
    }

    // Firmware polls this node every ~3s and applies it in classifyHazard() — a
    // CRITICAL gas reading always wins over this regardless of what's written here.
    fun setValveOverride(deviceId: String, closed: Boolean) {
        database.getReference("devices/$deviceId/commands/valveOverride")
            .setValue(if (closed) "closed" else "auto")
    }

    // Firmware clears its hazardLatched flag (and only then re-evaluates whether
    // the valve can reopen) once this lands — it does not open the valve directly.
    fun acknowledgeCritical(deviceId: String) {
        database.getReference("devices/$deviceId/commands/ackCritical").setValue(true)
    }

    private fun handleAlertNotifications(alerts: List<AlertItem>) {
        val newestTimestamp = alerts.maxOfOrNull { it.timestampMs } ?: 0L
        if (!hasLoadedInitialAlerts) {
            lastNotifiedAlertTimestamp = newestTimestamp
            hasLoadedInitialAlerts = true
            return
        }

        alerts
            .filter { it.timestampMs > lastNotifiedAlertTimestamp }
            .filter { it.hazardState.uppercase() == "WARNING" || it.hazardState.uppercase() == "CRITICAL" }
            .forEach { alert ->
                if (canShowAppNotification()) {
                    SafetyNotificationService.showAlertNotification(getApplication(), alert)
                }
            }

        if (newestTimestamp > lastNotifiedAlertTimestamp) {
            lastNotifiedAlertTimestamp = newestTimestamp
        }
    }

    private fun canShowAppNotification(): Boolean {
        val context: Application = getApplication()
        return ThresholdManager.isPushNotificationsEnabled(context) &&
            _isCloudConnected.value &&
            NetworkStatus.isWifiConnected(context)
    }

    private fun startCloudConnectionListener() {
        cloudConnectionRef = database.getReference(".info/connected")
        cloudConnectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _isCloudConnected.value = snapshot.getValue(Boolean::class.java) == true
            }

            override fun onCancelled(error: DatabaseError) {
                _isCloudConnected.value = false
            }
        }
        cloudConnectionRef?.addValueEventListener(cloudConnectionListener!!)
    }

    fun stopListening() {
        latestListener?.let { latestRef?.removeEventListener(it) }
        historyListener?.let { historyRef?.removeEventListener(it) }
        alertsListener?.let { alertsRef?.removeEventListener(it) }
        cloudConnectionListener?.let { cloudConnectionRef?.removeEventListener(it) }
        latestRef = null
        historyRef = null
        alertsRef = null
        cloudConnectionRef = null
        latestListener = null
        historyListener = null
        alertsListener = null
        cloudConnectionListener = null
        _isCloudConnected.value = false
        lastHazardLevel = "SAFE"
    }

    private fun checkAndSendCriticalSms(hazardLevel: String) {
        if (hazardLevel == "CRITICAL" && lastHazardLevel != "CRITICAL") {
            if (!ThresholdManager.isSmsNotificationsEnabled(getApplication())) {
                lastHazardLevel = hazardLevel
                return
            }

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
        if (records.size < 2) {
            _usageTips.value = emptyList()
            return
        }

        val tips = mutableListOf<String>()
        val sorted = records.sortedBy { it.timestamp }
        val dev = AppThresholds.DEV_DEFAULTS

        val avgGas = sorted.map { it.gasPpm }.average()
        val avgPressure = sorted.map { it.pressurePsi }.average()
        val avgHumidity = sorted.map { it.humidityPct }.average()

        // Trend: first half of this window vs. second half, so the number is
        // always this period's actual movement, not a fixed guess.
        val midpoint = sorted.size / 2
        val firstHalfAvgGas = sorted.take(midpoint).map { it.gasPpm }.average()
        val secondHalfAvgGas = sorted.drop(midpoint).map { it.gasPpm }.average()
        if (firstHalfAvgGas > 1.0) {
            val pctChange = ((secondHalfAvgGas - firstHalfAvgGas) / firstHalfAvgGas) * 100.0
            if (pctChange > 15.0) {
                tips.add(
                    "📈 Gas readings are trending %.0f%% higher than earlier this period — worth checking burner efficiency or a slow leak.".format(
                        Locale.getDefault(), pctChange
                    )
                )
            } else if (pctChange < -15.0) {
                tips.add(
                    "📉 Gas readings have dropped %.0f%% compared to earlier this period.".format(
                        Locale.getDefault(), -pctChange
                    )
                )
            }
        }

        // Hazard frequency actually observed in this window.
        val hazardCount = sorted.count { it.hazardLevel != "SAFE" }
        if (hazardCount > 0) {
            val pctHazard = (hazardCount.toFloat() / sorted.size) * 100f
            tips.add(
                "⚠️ %.0f%% of readings this period (%d of %d) were above safe levels. Ensure proper ventilation.".format(
                    Locale.getDefault(), pctHazard, hazardCount, sorted.size
                )
            )
        }

        // Weight-based consumption rate + refill estimate, from actual mass lost
        // over the actual elapsed time in this window (not a canned "3-5 days").
        val weightReadings = sorted.filter { it.weightKg > 0.5f }
        if (weightReadings.size >= 2) {
            val first = weightReadings.first()
            val last = weightReadings.last()
            val elapsedHours = (last.timestamp - first.timestamp) / 3_600_000f
            val consumedKg = first.weightKg - last.weightKg
            if (elapsedHours > 0.5f && consumedKg > 0.05f) {
                val ratePerDay = (consumedKg / elapsedHours) * 24f
                if (ratePerDay > 0.01f) {
                    val daysLeft = last.weightKg / ratePerDay
                    tips.add(
                        "🪫 At the current usage rate (~%.2f kg/day), your tank has roughly %.1f day(s) left.".format(
                            Locale.getDefault(), ratePerDay, daysLeft
                        )
                    )
                }
            }
        }

        // Pressure below the configured minimum expected operating value.
        if (avgPressure < dev.pressure.devMin) {
            tips.add(
                "📉 Average pressure this period (%.1f PSI) is well below the typical operating range — check for a near-empty tank or regulator issue.".format(
                    Locale.getDefault(), avgPressure
                )
            )
        }

        // Humidity sustained above the configured warning threshold.
        if (avgHumidity > dev.humidity.warningLevel) {
            tips.add(
                "💧 Average humidity (%.0f%%) has been high this period — consider improving ventilation to reduce sensor drift and corrosion risk.".format(
                    Locale.getDefault(), avgHumidity
                )
            )
        }

        if (tips.isEmpty()) {
            tips.add(
                "✅ Readings this period stayed stable (avg %.0f PPM gas, %.1f PSI pressure). Keep up the good maintenance!".format(
                    Locale.getDefault(), avgGas, avgPressure
                )
            )
        }

        _usageTips.value = tips.take(4)
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
