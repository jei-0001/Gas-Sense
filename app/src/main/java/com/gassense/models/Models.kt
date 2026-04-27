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
