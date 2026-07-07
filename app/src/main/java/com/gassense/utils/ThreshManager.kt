package com.gassense.utils

import android.content.Context
import android.content.SharedPreferences
import com.gassense.models.AppThresholds
import com.gassense.models.SensorThreshold

object ThresholdManager {

    private const val PREFS_NAME = "gassense_thresholds"
    // Separate file from thresholds — resetToDefaults() (called on every logout, and
    // from "Reset to Developer Defaults") clears the thresholds file entirely, which
    // was silently wiping the saved SMS number as an unintended side effect.
    private const val SMS_PREFS_NAME = "gassense_sms"
    private const val KEY_GAS_WARN = "gas_warning"
    private const val KEY_GAS_CRIT = "gas_critical"
    private const val KEY_PRESS_WARN = "pressure_warning"
    private const val KEY_PRESS_CRIT = "pressure_critical"
    private const val KEY_TEMP_WARN = "temp_warning"
    private const val KEY_TEMP_CRIT = "temp_critical"
    private const val KEY_HUM_WARN = "humidity_warning"
    private const val KEY_HUM_CRIT = "humidity_critical"
    private const val KEY_SMS_NUMBER = "sms_recipient_number"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun smsPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SMS_PREFS_NAME, Context.MODE_PRIVATE)

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

    fun saveSmsNumber(context: Context, number: String) {
        smsPrefs(context).edit().putString(KEY_SMS_NUMBER, number).apply()
    }

    fun getSmsNumber(context: Context): String {
        return smsPrefs(context).getString(KEY_SMS_NUMBER, "") ?: ""
    }

    // Only clears threshold values — the SMS number lives in its own prefs file
    // now and is untouched by this, since logging out shouldn't silently disable
    // critical-alert texting.
    fun resetToDefaults(context: Context) {
        prefs(context).edit().clear().apply()
    }
}