package com.gassense.utils

import android.content.Context
import android.content.SharedPreferences
import com.gassense.models.AppThresholds
import com.gassense.models.SensorThreshold

object ThresholdManager {

    private const val PREFS_NAME = "gassense_thresholds"
    private const val KEY_GAS_WARN = "gas_warning"
    private const val KEY_GAS_CRIT = "gas_critical"
    private const val KEY_PRESS_WARN = "pressure_warning"
    private const val KEY_PRESS_CRIT = "pressure_critical"
    private const val KEY_TEMP_WARN = "temp_warning"
    private const val KEY_TEMP_CRIT = "temp_critical"
    private const val KEY_HUM_WARN = "humidity_warning"
    private const val KEY_HUM_CRIT = "humidity_critical"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AppThresholds {
        val p = prefs(context)
        val dev = AppThresholds.DEV_DEFAULTS
        return AppThresholds(
            gas = SensorThreshold(
                warningLevel = p.getInt(KEY_GAS_WARN, dev.gas.warningLevel).coerceIn(dev.gas.devMin, dev.gas.devMax),
                criticalLevel = p.getInt(KEY_GAS_CRIT, dev.gas.criticalLevel).coerceIn(dev.gas.devMin, dev.gas.devMax),
                devMin = dev.gas.devMin, devMax = dev.gas.devMax
            ),
            pressure = SensorThreshold(
                warningLevel = p.getInt(KEY_PRESS_WARN, dev.pressure.warningLevel).coerceIn(dev.pressure.devMin, dev.pressure.devMax),
                criticalLevel = p.getInt(KEY_PRESS_CRIT, dev.pressure.criticalLevel).coerceIn(dev.pressure.devMin, dev.pressure.devMax),
                devMin = dev.pressure.devMin, devMax = dev.pressure.devMax
            ),
            temperature = SensorThreshold(
                warningLevel = p.getInt(KEY_TEMP_WARN, dev.temperature.warningLevel).coerceIn(dev.temperature.devMin, dev.temperature.devMax),
                criticalLevel = p.getInt(KEY_TEMP_CRIT, dev.temperature.criticalLevel).coerceIn(dev.temperature.devMin, dev.temperature.devMax),
                devMin = dev.temperature.devMin, devMax = dev.temperature.devMax
            ),
            humidity = SensorThreshold(
                warningLevel = p.getInt(KEY_HUM_WARN, dev.humidity.warningLevel).coerceIn(dev.humidity.devMin, dev.humidity.devMax),
                criticalLevel = p.getInt(KEY_HUM_CRIT, dev.humidity.criticalLevel).coerceIn(dev.humidity.devMin, dev.humidity.devMax),
                devMin = dev.humidity.devMin, devMax = dev.humidity.devMax
            )
        )
    }

    fun save(context: Context, thresholds: AppThresholds) {
        val dev = AppThresholds.DEV_DEFAULTS
        prefs(context).edit().apply {
            putInt(KEY_GAS_WARN, thresholds.gas.warningLevel.coerceIn(dev.gas.devMin, dev.gas.devMax))
            putInt(KEY_GAS_CRIT, thresholds.gas.criticalLevel.coerceIn(dev.gas.devMin, dev.gas.devMax))
            putInt(KEY_PRESS_WARN, thresholds.pressure.warningLevel.coerceIn(dev.pressure.devMin, dev.pressure.devMax))
            putInt(KEY_PRESS_CRIT, thresholds.pressure.criticalLevel.coerceIn(dev.pressure.devMin, dev.pressure.devMax))
            putInt(KEY_TEMP_WARN, thresholds.temperature.warningLevel.coerceIn(dev.temperature.devMin, dev.temperature.devMax))
            putInt(KEY_TEMP_CRIT, thresholds.temperature.criticalLevel.coerceIn(dev.temperature.devMin, dev.temperature.devMax))
            putInt(KEY_HUM_WARN, thresholds.humidity.warningLevel.coerceIn(dev.humidity.devMin, dev.humidity.devMax))
            putInt(KEY_HUM_CRIT, thresholds.humidity.criticalLevel.coerceIn(dev.humidity.devMin, dev.humidity.devMax))
            apply()
        }
    }

    fun resetToDefaults(context: Context) {
        prefs(context).edit().clear().apply()
    }
}