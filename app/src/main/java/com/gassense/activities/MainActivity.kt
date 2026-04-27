package com.gassense.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.gassense.R
import com.gassense.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.mainNavHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Wire bottom nav to nav controller
        binding.bottomNav.setupWithNavController(navController)

        // Hide bottom nav on inner screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideNavDestinations = setOf(
                R.id.deviceManagementFragment,
                R.id.addDeviceFragment,
                R.id.qrScannerMainFragment
            )
            binding.bottomNav.visibility = if (destination.id in hideNavDestinations) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }
        }
    }
}
