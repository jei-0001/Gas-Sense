package com.gassense.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.gassense.R
import com.gassense.activities.AuthActivity
import com.gassense.databinding.FragmentSettingsBinding
import com.gassense.models.AppThresholds
import com.gassense.models.SensorThreshold
import com.gassense.utils.ThresholdManager
import com.gassense.viewmodels.AuthViewModel
import com.gassense.viewmodels.SensorViewModel

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by activityViewModels()
    private val sensorViewModel: SensorViewModel by activityViewModels()

    private lateinit var currentThresholds: AppThresholds
    private val dev = AppThresholds.DEV_DEFAULTS

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentThresholds = ThresholdManager.load(requireContext())
        populateFields(currentThresholds)
        binding.etSmsNumber.setText(ThresholdManager.getSmsNumber(requireContext()))

        binding.cardMyDevices.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_devices)
        }

        binding.btnApplyThresholds.setOnClickListener {
            applyThresholds()
        }

        binding.btnResetThresholds.setOnClickListener {
            ThresholdManager.resetToDefaults(requireContext())
            currentThresholds = ThresholdManager.load(requireContext())
            populateFields(currentThresholds)
            Toast.makeText(context, "Reset to developer defaults", Toast.LENGTH_SHORT).show()
        }

        binding.switchAutoShutoff.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(
                context,
                if (isChecked) "AutoShutoff enabled" else "AutoShutoff disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnLogout.setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        authViewModel.logout()
        sensorViewModel.stopListening()
        ThresholdManager.resetToDefaults(requireContext())
        requireActivity().apply {
            startActivity(Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun populateFields(t: AppThresholds) {
        binding.etGasWarning.setText(t.gas.warningLevel.toString())
        binding.etGasCritical.setText(t.gas.criticalLevel.toString())
        binding.etPressureWarning.setText(t.pressure.warningLevel.toString())
        binding.etPressureCritical.setText(t.pressure.criticalLevel.toString())
        binding.etTempWarning.setText(t.temperature.warningLevel.toString())
        binding.etTempCritical.setText(t.temperature.criticalLevel.toString())
        binding.etHumidityWarning.setText(t.humidity.warningLevel.toString())
        binding.etHumidityCritical.setText(t.humidity.criticalLevel.toString())
    }

    private fun applyThresholds() {
        val gasWarn = binding.etGasWarning.text.toString().toIntOrNull()
        val gasCrit = binding.etGasCritical.text.toString().toIntOrNull()
        val pressWarn = binding.etPressureWarning.text.toString().toIntOrNull()
        val pressCrit = binding.etPressureCritical.text.toString().toIntOrNull()
        val tempWarn = binding.etTempWarning.text.toString().toIntOrNull()
        val tempCrit = binding.etTempCritical.text.toString().toIntOrNull()
        val humWarn = binding.etHumidityWarning.text.toString().toIntOrNull()
        val humCrit = binding.etHumidityCritical.text.toString().toIntOrNull()

        if (listOf(gasWarn, gasCrit, pressWarn, pressCrit, tempWarn, tempCrit, humWarn, humCrit).any { it == null }) {
            Toast.makeText(context, "Please fill in all threshold fields", Toast.LENGTH_SHORT).show()
            return
        }

        val errors = mutableListOf<String>()
        if (gasWarn!! >= gasCrit!!) errors.add("Gas: Warning must be lower than Critical")
        if (pressWarn!! >= pressCrit!!) errors.add("Pressure: Warning must be lower than Critical")
        if (tempWarn!! >= tempCrit!!) errors.add("Temperature: Warning must be lower than Critical")
        if (humWarn!! >= humCrit!!) errors.add("Humidity: Warning must be lower than Critical")

        if (gasWarn < dev.gas.devMin || gasCrit > dev.gas.devMax)
            errors.add("Gas must be between ${dev.gas.devMin}–${dev.gas.devMax} %LEL")
        if (pressWarn < dev.pressure.devMin || pressCrit > dev.pressure.devMax)
            errors.add("Pressure must be between ${dev.pressure.devMin}–${dev.pressure.devMax} PSI")
        if (tempWarn < dev.temperature.devMin || tempCrit > dev.temperature.devMax)
            errors.add("Temperature must be between ${dev.temperature.devMin}–${dev.temperature.devMax} °C")
        if (humWarn < dev.humidity.devMin || humCrit > dev.humidity.devMax)
            errors.add("Humidity must be between ${dev.humidity.devMin}–${dev.humidity.devMax} %")

        if (errors.isNotEmpty()) {
            Toast.makeText(context, errors.first(), Toast.LENGTH_LONG).show()
            return
        }

        val newThresholds = AppThresholds(
            gas = SensorThreshold(gasWarn, gasCrit, dev.gas.devMin, dev.gas.devMax),
            pressure = SensorThreshold(pressWarn, pressCrit, dev.pressure.devMin, dev.pressure.devMax),
            temperature = SensorThreshold(tempWarn, tempCrit, dev.temperature.devMin, dev.temperature.devMax),
            humidity = SensorThreshold(humWarn, humCrit, dev.humidity.devMin, dev.humidity.devMax)
        )

        ThresholdManager.save(requireContext(), newThresholds)
        ThresholdManager.saveSmsNumber(requireContext(), binding.etSmsNumber.text.toString())
        currentThresholds = newThresholds
        Toast.makeText(context, "✓ Thresholds saved!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
