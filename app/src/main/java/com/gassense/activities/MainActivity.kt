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

    // Tracks the last hazard level we've already popped a dialog for, so this
    // survives bottom-nav tab switches instead of resetting with each Fragment's
    // view (which is what caused the popup to only ever appear on Dashboard).
    private var lastDialogHazardLevel = "SAFE"

    // Once the WARNING sheet has been dismissed for the current escalation episode, it
    // won't reopen until the hazard genuinely clears back to SAFE (a new episode) —
    // prevents it from spuriously popping back up on a brief WARNING boundary flicker
    // shortly after the user dismissed it. CRITICAL's own re-fire behavior is untouched.
    private var warningDialogDismissedForEpisode = false

    // CRITICAL is a live emergency, not a one-time notice — it keeps re-showing every
    // CRITICAL_DIALOG_REPEAT_MS for as long as the reading stays CRITICAL, even after
    // the user has acknowledged it once, instead of going silent for the rest of the
    // episode.
    private var lastCriticalDialogShownAtMs = 0L
    private val CRITICAL_DIALOG_REPEAT_MS = 30_000L

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
        android.util.Log.d("GasSenseDebug", "checkHazardEscalation: hazardLevel=${data.hazardLevel} lastDialogHazardLevel=$lastDialogHazardLevel timestamp=${data.timestamp} now=${System.currentTimeMillis()} isStale=$isStale")
        if (data.timestamp == 0L || isStale) {
            android.util.Log.d("GasSenseDebug", "checkHazardEscalation: SKIPPED (stale/no timestamp)")
            lastDialogHazardLevel = "SAFE"
            warningDialogDismissedForEpisode = false
            return
        }

        if (data.hazardLevel == "SAFE") {
            // Hazard has genuinely cleared — re-arm the WARNING sheet for the next episode.
            warningDialogDismissedForEpisode = false
        }

        val shouldShowWarning = data.hazardLevel == "WARNING" &&
            data.hazardLevel != lastDialogHazardLevel &&
            !warningDialogDismissedForEpisode

        val now = System.currentTimeMillis()
        val enteredCritical = data.hazardLevel == "CRITICAL" && data.hazardLevel != lastDialogHazardLevel
        val criticalStillOngoing = data.hazardLevel == "CRITICAL" &&
            (now - lastCriticalDialogShownAtMs) >= CRITICAL_DIALOG_REPEAT_MS
        val shouldShowCritical = enteredCritical || criticalStillOngoing
        android.util.Log.d("GasSenseDebug", "checkHazardEscalation: enteredCritical=$enteredCritical criticalStillOngoing=$criticalStillOngoing shouldShowWarning=$shouldShowWarning shouldShowCritical=$shouldShowCritical")

        if (shouldShowWarning || shouldShowCritical) {
            showHazardDialog(isWarning = data.hazardLevel == "WARNING", data)
            if (data.hazardLevel == "CRITICAL") lastCriticalDialogShownAtMs = now
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

        if (isWarning) {
            listOf(R.id.btnDismiss, R.id.btnViewDetails).forEach { id ->
                sheetView.findViewById<View>(id)?.setOnClickListener {
                    warningDialogDismissedForEpisode = true
                    dialog.dismiss()
                }
            }
        } else {
            sheetView.findViewById<View>(R.id.btnAcknowledge)?.setOnClickListener {
                sensorViewModel.acknowledgeCritical(currentDeviceId)
                dialog.dismiss()
            }
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
