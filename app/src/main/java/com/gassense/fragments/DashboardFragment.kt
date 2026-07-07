package com.gassense.fragments

import android.os.Bundle
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
import com.gassense.viewmodels.AuthViewModel
import com.gassense.viewmodels.LiveSensorData
import com.gassense.viewmodels.SensorViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale



class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var thresholds: AppThresholds
    private val authViewModel: AuthViewModel by activityViewModels()
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.userProfile.collect { profile ->
                    val deviceId = profile?.deviceId ?: "DEV-TEST-0001"
                    binding.chipDeviceSwitcher.text = if (profile != null) profile.fullName + "'s Tank" else deviceId
                    // Listening itself is started at the Activity level (MainActivity) so
                    // it isn't tied to this Fragment's view lifecycle.
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sensorViewModel.sensorData.collect { data ->
                        updateSensorDisplay(data)
                    }
                }

                // Heartbeat timer: refresh status every 30 seconds to catch stale data
                launch {
                    while (isActive) {
                        delay(30_000)
                        updateSensorDisplay(sensorViewModel.sensorData.value)
                    }
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

    // Demo-only — binds the static placeholder values baked into the dialog layouts.
    // The real hazard-triggered popup (using live readings) lives in MainActivity now,
    // so it fires regardless of which bottom-nav tab is open.
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
        val currentTime = System.currentTimeMillis()
        // Threshold: 60 seconds of inactivity = Offline
        val isStale = data.timestamp > 0 && (currentTime - data.timestamp) > 60_000

        if (data.timestamp == 0L || isStale) {
            showOfflineState(data.timestamp)
            return
        }

        // Update connection dot to green
        binding.connectionDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
            requireContext().getColor(R.color.safe_green)
        )

        // Map data to UI
        binding.tvGasValue.text = String.format(Locale.getDefault(), "%.1f", data.lelPercent)
        binding.tvPressureValue.text = String.format(Locale.getDefault(), "%.1f", data.pressurePsi)
        binding.tvTempValue.text = String.format(Locale.getDefault(), "%.1f", data.tempC)
        binding.tvHumidityValue.text = String.format(Locale.getDefault(), "%.1f", data.humidityPct)
        
        // Estimated tank level calculation (simplified)
        val tankLevel = calculateTankLevel(data.pressurePsi)
        binding.progressTankLevel.progress = tankLevel
        binding.tvTankPercentage.text = String.format(Locale.getDefault(), "%d%%", tankLevel)

        // Shutoff banner
        binding.bannerAutoShutoff.visibility =
            if (data.autoShutoff) View.VISIBLE else View.GONE

        updateStatusBadge(data.hazardLevel)
    }

    private fun showOfflineState(timestamp: Long = 0L) {
        val ctx = requireContext()
        val currentTime = System.currentTimeMillis()
        
        // Update connection dot to gray/hint color
        binding.connectionDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ctx.getColor(R.color.text_hint)
        )

        binding.tvStatusLabel.text = getString(R.string.status_offline).uppercase(Locale.getDefault())
        binding.tvStatusLabel.setTextColor(ctx.getColor(R.color.text_secondary))
        
        if (timestamp > 0) {
            val diffMinutes = (currentTime - timestamp) / 60_000
            binding.tvStatusSubtitle.text = if (diffMinutes < 60) {
                "Inactive for $diffMinutes min"
            } else {
                "Inactive for ${diffMinutes / 60} hours"
            }
        } else {
            binding.tvStatusSubtitle.text = "Waiting for device signal..."
        }

        binding.cvStatusBadge.setCardBackgroundColor(ctx.getColor(R.color.card_medium))

        binding.tvGasValue.text = "--"
        binding.tvPressureValue.text = "--"
        binding.tvTempValue.text = "--"
        binding.tvHumidityValue.text = "--"
        binding.progressTankLevel.progress = 0
        binding.tvTankPercentage.text = "--%"
        binding.bannerAutoShutoff.visibility = View.GONE
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
