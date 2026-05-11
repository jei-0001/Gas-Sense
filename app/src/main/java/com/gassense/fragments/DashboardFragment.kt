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
import com.gassense.models.AppThresholds
import com.gassense.models.SensorData
import com.gassense.utils.ThresholdManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var thresholds: AppThresholds

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSensorDisplay(getSimulatedData())
            handler.postDelayed(this, 3000)
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
        // Load thresholds from ThresholdManager so dashboard
        // always reflects whatever the user set in Settings
        thresholds = ThresholdManager.load(requireContext())
        setupDeviceSwitcher()
        setupDemoButtons()
        startLiveUpdates()
    }

    override fun onResume() {
        super.onResume()
        // Reload thresholds every time user comes back to dashboard
        // in case they changed them in Settings
        thresholds = ThresholdManager.load(requireContext())
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
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

    private fun startLiveUpdates() {
        handler.post(updateRunnable)
    }

    private fun getSimulatedData(): SensorData {
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
        updateStatusBadge(data.gasLel, data.pressurePsi, data.tempC, data.humidityPct)
    }

    private fun updateStatusBadge(
        gasLel: Float,
        pressurePsi: Float,
        tempC: Float,
        humidityPct: Float
    ) {
        val ctx = requireContext()

        // Check critical first — any sensor hitting critical = CRITICAL
        val isCritical = gasLel >= thresholds.gas.criticalLevel ||
                pressurePsi >= thresholds.pressure.criticalLevel ||
                tempC >= thresholds.temperature.criticalLevel ||
                humidityPct >= thresholds.humidity.criticalLevel

        // Check warning — any sensor hitting warning = WARNING
        val isWarning = gasLel >= thresholds.gas.warningLevel ||
                pressurePsi >= thresholds.pressure.warningLevel ||
                tempC >= thresholds.temperature.warningLevel ||
                humidityPct >= thresholds.humidity.warningLevel

        when {
            isCritical -> {
                binding.tvStatusLabel.text = getString(R.string.status_critical)
                binding.tvStatusLabel.setTextColor(ctx.getColor(R.color.critical_red))
                binding.tvStatusSubtitle.text = "Immediate action required!"
                binding.cvStatusBadge.setCardBackgroundColor(ctx.getColor(R.color.critical_red_dim))
            }
            isWarning -> {
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
        handler.removeCallbacks(updateRunnable)
        _binding = null
    }
}