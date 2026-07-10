package com.gassense.models

import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// GasSense is a fixed-location installation (Philippines) — alert/history times
// should reflect the installation site's local time regardless of the viewing
// phone's own system timezone (which may be unset/wrong, especially on emulators).
val DEVICE_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Manila")

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
        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        formatter.timeZone = DEVICE_TIME_ZONE
        return formatter.format(Date(timestampMs))
    }
}

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
            // Sensor is a 0-0.8 MPa (~116 psi) rated transducer — devMax is set just
            // under that real ceiling. Bench-tested fuel is Petron Gasul (confirmed
            // 37.2% propane / 62.5% butane per its Product Data Sheet), whose vapor
            // pressure runs ~64-108 psi across a 20-40 degC Philippine ambient range
            // (NIST WebBook Antoine-equation estimate, cross-validated within 4% of
            // Petron's measured RVP — see PRESSURE_PSI_MAX comment in the firmware).
            // warningLevel sits just above that normal hot-day ceiling (~108 psi);
            // criticalLevel leaves a few psi of margin below the sensor's real 116 psi
            // rating. Note pressure alone isn't a strong leak signal for LPG (vapor
            // pressure stays roughly flat regardless of fill level) — these thresholds
            // mainly guard against unexplained overpressure/sensor-ceiling risk, not
            // leak detection, which the firmware's weight-based fusion logic handles.
            pressure = SensorThreshold(
                warningLevel = 108,
                criticalLevel = 113,
                devMin = 50,
                devMax = 115
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