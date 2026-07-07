package com.gassense.models

import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SensorData(
    val gasLel: Float,
    val pressurePsi: Float,
    val tempC: Float,
    val humidityPct: Float,
    val tankLevel: Int
)

// Matches the hazard-transition JSON the ESP32 firmware writes to
// devices/{id}/alerts/{epochMs} — field names must stay in sync with the sketch.
@IgnoreExtraProperties
data class AlertItem(
    val hazardState: String = "SAFE",
    val previousState: String = "SAFE",
    val ppmAtEvent: Float = 0f,
    val pressureAtEvent: Float = 0f,
    val tempAtEvent: Float = 0f,
    val humidityAtEvent: Float = 0f,
    val valveOpen: Boolean = false,
    val timestampMs: Long = 0L
) {
    enum class Severity { WARNING, CRITICAL }

    companion object {
        private const val LEL_100_PPM = 21000f
    }

    fun severityEnum(): Severity = when (hazardState.uppercase()) {
        "CRITICAL" -> Severity.CRITICAL
        else -> Severity.WARNING
    }

    fun isAutoShutoff(): Boolean = severityEnum() == Severity.CRITICAL && !valveOpen

    fun lelPercentAtEvent(): Float = (ppmAtEvent / LEL_100_PPM) * 100f

    fun title(): String = when (severityEnum()) {
        Severity.CRITICAL -> "Critical Gas Level Detected"
        Severity.WARNING -> "Gas Level Warning"
    }

    fun description(): String = String.format(
        Locale.getDefault(),
        "%s → %s · %.1f%% LEL, %.0f PSI",
        previousState, hazardState, lelPercentAtEvent(), pressureAtEvent
    )

    fun formattedTimestamp(): String {
        if (timestampMs <= 0L) return ""
        return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestampMs))
    }
}

data class DeviceItem(
    val name: String,
    val deviceId: String,
    val isOnline: Boolean,
    val lastSynced: String
)

data class SensorThreshold(
    val warningLevel: Int,
    val criticalLevel: Int,
    val devMin: Int,
    val devMax: Int
)

data class AppThresholds(
    val gas: SensorThreshold,
    val pressure: SensorThreshold,
    val temperature: SensorThreshold,
    val humidity: SensorThreshold
) {
    companion object {
        val DEV_DEFAULTS = AppThresholds(
            gas = SensorThreshold(
                warningLevel = 10,
                criticalLevel = 25,
                devMin = 5,
                devMax = 50
            ),
            pressure = SensorThreshold(
                warningLevel = 100,
                criticalLevel = 120,
                devMin = 50,
                devMax = 150
            ),
            temperature = SensorThreshold(
                warningLevel = 40,
                criticalLevel = 60,
                devMin = 0,
                devMax = 80
            ),
            humidity = SensorThreshold(
                warningLevel = 80,
                criticalLevel = 90,
                devMin = 0,
                devMax = 100
            )
        )
    }
}