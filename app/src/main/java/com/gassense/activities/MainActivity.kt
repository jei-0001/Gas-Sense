package com.gassense.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.gassense.R
import com.gassense.databinding.ActivityMainBinding
import com.gassense.viewmodels.AuthViewModel
import com.gassense.viewmodels.LiveSensorData
import com.gassense.viewmodels.SensorViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authViewModel: AuthViewModel by viewModels()
    private val sensorViewModel: SensorViewModel by viewModels()

    // Tracks the last hazard level we've already popped a dialog for, so this
    // survives bottom-nav tab switches instead of resetting with each Fragment's
    // view (which is what caused the popup to only ever appear on Dashboard).
    private var lastDialogHazardLevel = "SAFE"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            // Permission denied - explain to user
            Snackbar.make(binding.root, "Notifications are disabled. You won't receive safety alerts.", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.mainNavHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        authViewModel.fetchProfile()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.userProfile.collect { profile ->
                    val isAdmin = profile?.role == "admin"
                    binding.bottomNav.menu.findItem(R.id.adminFragment)?.isVisible = isAdmin

                    val deviceId = profile?.deviceId ?: "DEV-TEST-0001"
                    sensorViewModel.startListening(deviceId)
                }
            }
        }

        // Lives at the Activity level (not inside DashboardFragment) so the
        // critical/warning popup fires no matter which bottom-nav tab is open.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorViewModel.sensorData.collect { data ->
                    checkHazardEscalation(data)
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideNav = setOf(
                R.id.deviceManagementFragment,
                R.id.addDeviceFragment,
                R.id.qrScannerMainFragment
            )
            binding.bottomNav.visibility =
                if (destination.id in hideNav) View.GONE else View.VISIBLE
        }

        askNotificationPermission()

        // Handle deep link from notification
        if (intent.getStringExtra("navigate_to") == "alerts") {
            navController.navigate(R.id.alertsFragment)
        }
    }

    private fun checkHazardEscalation(data: LiveSensorData) {
        val isStale = data.timestamp > 0 && (System.currentTimeMillis() - data.timestamp) > 60_000
        if (data.timestamp == 0L || isStale) {
            lastDialogHazardLevel = "SAFE"
            return
        }

        if (data.hazardLevel != lastDialogHazardLevel &&
            (data.hazardLevel == "WARNING" || data.hazardLevel == "CRITICAL")
        ) {
            showHazardDialog(isWarning = data.hazardLevel == "WARNING", data)
        }
        lastDialogHazardLevel = data.hazardLevel
    }

    private fun showHazardDialog(isWarning: Boolean, data: LiveSensorData) {
        val dialog = BottomSheetDialog(this)
        val layoutRes = if (isWarning) R.layout.dialog_warning_alert else R.layout.dialog_critical_alert
        val sheetView = layoutInflater.inflate(layoutRes, null)
        dialog.setContentView(sheetView)
        dialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        sheetView.findViewById<View>(R.id.tvSimulationBadge)?.visibility = View.GONE
        sheetView.findViewById<android.widget.TextView>(R.id.tvGasReading)?.text =
            String.format(Locale.getDefault(), "%.1f %%LEL", data.lelPercent)
        sheetView.findViewById<android.widget.TextView>(R.id.tvPressureReading)?.text =
            String.format(Locale.getDefault(), "%.0f PSI", data.pressurePsi)
        sheetView.findViewById<android.widget.TextView>(R.id.tvTempReading)?.text =
            String.format(Locale.getDefault(), "%.1f °C", data.tempC)
        sheetView.findViewById<android.widget.TextView>(R.id.tvHumidityReading)?.text =
            String.format(Locale.getDefault(), "%.0f %%", data.humidityPct)

        val dismissIds = if (isWarning) {
            listOf(R.id.btnDismiss, R.id.btnViewDetails)
        } else {
            listOf(R.id.btnAcknowledge, R.id.btnViewDetails)
        }
        dismissIds.forEach { id ->
            sheetView.findViewById<View>(id)?.setOnClickListener { dialog.dismiss() }
        }

        dialog.show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Already granted
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Show rationale and then request
                Snackbar.make(binding.root, "Please enable notifications to receive safety alerts.", Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK") {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }.show()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
