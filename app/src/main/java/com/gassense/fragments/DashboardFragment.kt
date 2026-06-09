package com.gassense.fragments

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gassense.R
import com.gassense.databinding.FragmentDashboardBinding
import com.gassense.models.AppThresholds
import com.gassense.utils.ThresholdManager
import com.gassense.viewmodels.LiveSensorData
import com.gassense.viewmodels.SensorViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch



class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var thresholds: AppThresholds
    private val sensorViewModel: SensorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        thresholds = ThresholdManager.load(requireContext())
        setupDeviceSwitcher()
        setupDemoButtons()

        // Start listening to the default device (this would ideally come from the user's profile)
        sensorViewModel.startListening("DEV-TEST-0001")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorViewModel.sensorData.collect { data ->
                    updateSensorDisplay(data)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        thresholds = ThresholdManager.load(requireContext())
    }

    private fun setupDeviceSwitcher() {
        binding.chipDeviceSwitcher.setOnClickListener {
            showDeviceBottomSheet()
        }
    }

    private fun showDeviceBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_device_switcher, null)
        dialog.setContentView(sheetView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun setupDemoButtons() {
        binding.btnWarningDemo.setOnClickListener {
            showAlertDialog(isWarning = true)
        }
        binding.btnCriticalDemo.setOnClickListener {
            showAlertDialog(isWarning = false)
        }
    }

    private fun showAlertDialog(isWarning: Boolean) {
        val dialog = BottomSheetDialog(requireContext())
        val layoutRes = if (isWarning) R.layout.dialog_warning_alert else R.layout.dialog_critical_alert
        val sheetView = layoutInflater.inflate(layoutRes, null)
        dialog.setContentView(sheetView)
        dialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        if (isWarning) {
            sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDismiss)
                ?.setOnClickListener { dialog.dismiss() }
            sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewDetails)
                ?.setOnClickListener { dialog.dismiss() }
        } else {
            sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAcknowledge)
                ?.setOnClickListener { dialog.dismiss() }
            sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewDetails)
                ?.setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
    }

    private fun updateSensorDisplay(data: LiveSensorData) {
        // Map data to UI
        binding.tvGasValue.text = String.format("%.0f", data.gasPpm)
        binding.tvPressureValue.text = String.format("%.1f", data.pressurePsi)
        binding.tvTempValue.text = String.format("%.1f", data.tempC)
        binding.tvHumidityValue.text = String.format("%.1f", data.humidityPct)
        
        // Estimated tank level calculation (simplified)
        val tankLevel = calculateTankLevel(data.pressurePsi)
        binding.progressTankLevel.progress = tankLevel
        binding.tvTankPercentage.text = "$tankLevel%"
        
        updateStatusBadge(data.hazardLevel)
    }

    private fun calculateTankLevel(pressure: Float): Int {
        // Simple linear estimation based on standard propane pressure range (e.g. 0-200 PSI)
        val maxPressure = 150f
        return (pressure / maxPressure * 100).toInt().coerceIn(0, 100)
    }

    private fun updateStatusBadge(hazardLevel: String) {
        val ctx = requireContext()
        when (hazardLevel) {
            "CRITICAL" -> {
                binding.tvStatusLabel.text = getString(R.string.status_critical)
                binding.tvStatusLabel.setTextColor(ctx.getColor(R.color.critical_red))
                binding.tvStatusSubtitle.text = "Immediate action required!"
                binding.cvStatusBadge.setCardBackgroundColor(ctx.getColor(R.color.critical_red_dim))
            }
            "WARNING" -> {
                binding.tvStatusLabel.text = getString(R.string.status_warning)
                binding.tvStatusLabel.setTextColor(ctx.getColor(R.color.warning_yellow))
                binding.tvStatusSubtitle.text = "Monitor sensor levels closely."
                binding.cvStatusBadge.setCardBackgroundColor(ctx.getColor(R.color.warning_yellow_dim))
            }
            else -> {
                binding.tvStatusLabel.text = getString(R.string.status_safe)
                binding.tvStatusLabel.setTextColor(ctx.getColor(R.color.safe_green))
                binding.tvStatusSubtitle.text = getString(R.string.safe_subtitle)
                binding.cvStatusBadge.setCardBackgroundColor(ctx.getColor(R.color.safe_green_dim))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
