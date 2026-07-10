package com.gassense.activities

import android.Manifest
import android.content.Intent
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
import androidx.navigation.NavController
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

    // The single hazard dialog currently on screen, if any. Kept at the Activity level
    // so it survives bottom-nav tab switches. Instead of popping a new BottomSheetDialog
    // for every qualifying sensor update, we keep this one instance around and just
    // refresh its readings live while the hazard level stays the same — a new dialog is
    // only created when the level actually transitions (SAFE->WARNING, WARNING<->CRITICAL).
    // If the user dismisses/acknowledges it, activeHazardDialog goes back to null and it
    // stays closed until lastSeenHazardLevel changes again (a fresh episode).
    private var activeHazardDialog: BottomSheetDialog? = null
    private var activeHazardDialogView: View? = null
    private var lastSeenHazardLevel = "SAFE"

    private var currentDeviceId = "DEV-TEST-0001"

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
                    currentDeviceId = deviceId
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

        askNotificationPermission()

        handleNotificationNavigation(intent, navController)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.mainNavHostFragment) as? NavHostFragment ?: return
        handleNotificationNavigation(intent, navHostFragment.navController)
    }

    private fun handleNotificationNavigation(intent: Intent?, navController: NavController) {
        if (
            intent?.getStringExtra("navigate_to") == "alerts" &&
            navController.currentDestination?.id != R.id.alertsFragment
        ) {
            navController.navigate(R.id.alertsFragment)
        }
    }

    private fun checkHazardEscalation(data: LiveSensorData) {
        val isStale = data.timestamp > 0 && (System.currentTimeMillis() - data.timestamp) > 60_000
        if (data.timestamp == 0L || isStale) {
            dismissActiveHazardDialog()
            lastSeenHazardLevel = "SAFE"
            return
        }

        val level = data.hazardLevel
        val levelChanged = level != lastSeenHazardLevel
        lastSeenHazardLevel = level

        when {
            level == "SAFE" -> dismissActiveHazardDialog()
            levelChanged -> {
                // Genuine transition into (or between) WARNING/CRITICAL — always show a
                // fresh sheet for the new level, replacing whatever was there before.
                dismissActiveHazardDialog()
                showHazardDialog(isWarning = level == "WARNING", data)
            }
            activeHazardDialog != null -> {
                // Same level as last time and the sheet is still open — just refresh the
                // numbers instead of spawning another dialog.
                activeHazardDialogView?.let { updateHazardDialogReadings(it, data) }
            }
            // else: same level, but the user already dismissed/acknowledged it — stay closed.
        }
    }

    private fun dismissActiveHazardDialog() {
        activeHazardDialog?.dismiss()
        activeHazardDialog = null
        activeHazardDialogView = null
    }

    private fun updateHazardDialogReadings(sheetView: View, data: LiveSensorData) {
        sheetView.findViewById<android.widget.TextView>(R.id.tvGasReading)?.text =
            String.format(Locale.getDefault(), "%.1f %%LEL", data.lelPercent)
        sheetView.findViewById<android.widget.TextView>(R.id.tvPressureReading)?.text =
            String.format(Locale.getDefault(), "%.0f PSI", data.pressurePsi)
        sheetView.findViewById<android.widget.TextView>(R.id.tvTempReading)?.text =
            String.format(Locale.getDefault(), "%.1f °C", data.tempC)
        sheetView.findViewById<android.widget.TextView>(R.id.tvHumidityReading)?.text =
            String.format(Locale.getDefault(), "%.0f %%", data.humidityPct)
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
        updateHazardDialogReadings(sheetView, data)

        if (isWarning) {
            listOf(R.id.btnDismiss, R.id.btnViewDetails).forEach { id ->
                sheetView.findViewById<View>(id)?.setOnClickListener { dialog.dismiss() }
            }
        } else {
            sheetView.findViewById<View>(R.id.btnAcknowledge)?.setOnClickListener {
                sensorViewModel.acknowledgeCritical(currentDeviceId)
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            if (activeHazardDialog === dialog) {
                activeHazardDialog = null
                activeHazardDialogView = null
            }
        }

        activeHazardDialog = dialog
        activeHazardDialogView = sheetView
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
