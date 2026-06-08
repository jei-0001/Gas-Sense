package com.gassense.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.gassense.R
import com.gassense.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
