package com.gassense.models

data class SensorData(
    val gasLel: Float,
    val pressurePsi: Float,
    val tempC: Float,
    val humidityPct: Float,
    val tankLevel: Int
)

data class AlertItem(
    val title: String,
    val description: String,
    val severity: Severity,
    val timestamp: String,
    val autoShutoffTriggered: Boolean
) {
    enum class Severity { WARNING, CRITICAL }
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