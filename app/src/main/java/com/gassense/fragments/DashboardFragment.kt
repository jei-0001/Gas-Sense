package com.gassense.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gassense.R
import com.gassense.databinding.FragmentDashboardBinding
import com.gassense.models.SensorData
import com.google.android.material.bottomsheet.BottomSheetDialog

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Simulate live sensor updates
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSensorDisplay(getSimulatedData())
            handler.postDelayed(this, 3000) // Update every 3s
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDeviceSwitcher()
        setupDemoButtons()
        startLiveUpdates()
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

        // Style the bottom sheet
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
        }
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
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
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

    private fun startLiveUpdates() {
        handler.post(updateRunnable)
    }

    private fun getSimulatedData(): SensorData {
        // Simulate realistic fluctuating sensor data
        return SensorData(
            gasLel = (8..15).random().toFloat(),
            pressurePsi = (82..95).random().toFloat(),
            tempC = 25f + (0..50).random() / 10f,
            humidityPct = (58..75).random().toFloat(),
            tankLevel = 68
        )
    }

    private fun updateSensorDisplay(data: SensorData) {
        binding.tvGasValue.text = data.gasLel.toInt().toString()
        binding.tvPressureValue.text = data.pressurePsi.toInt().toString()
        binding.tvTempValue.text = String.format("%.1f", data.tempC)
        binding.tvHumidityValue.text = data.humidityPct.toInt().toString()
        binding.progressTankLevel.progress = data.tankLevel
        binding.tvTankPercentage.text = "${data.tankLevel}%"

        // Update status badge based on thresholds
        updateStatusBadge(data.gasLel)
    }

    private fun updateStatusBadge(gasLel: Float) {
        val context = requireContext()
        when {
            gasLel >= 25f -> {
                // CRITICAL
                binding.tvStatusLabel.text = getString(R.string.status_critical)
                binding.tvStatusLabel.setTextColor(context.getColor(R.color.critical_red))
                binding.tvStatusSubtitle.text = "Immediate action required!"
                binding.cvStatusBadge.setCardBackgroundColor(context.getColor(R.color.critical_red_dim))
            }
            gasLel >= 10f -> {
                // WARNING
                binding.tvStatusLabel.text = getString(R.string.status_warning)
                binding.tvStatusLabel.setTextColor(context.getColor(R.color.warning_yellow))
                binding.tvStatusSubtitle.text = "Monitor gas levels closely."
                binding.cvStatusBadge.setCardBackgroundColor(context.getColor(R.color.warning_yellow_dim))
            }
            else -> {
                // SAFE
                binding.tvStatusLabel.text = getString(R.string.status_safe)
                binding.tvStatusLabel.setTextColor(context.getColor(R.color.safe_green))
                binding.tvStatusSubtitle.text = getString(R.string.safe_subtitle)
                binding.cvStatusBadge.setCardBackgroundColor(context.getColor(R.color.safe_green_dim))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
        _binding = null
    }
}
