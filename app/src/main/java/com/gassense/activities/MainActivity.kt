package com.gassense.activities

import android.os.Bundle
import android.view.View
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

        // This wires bottom nav to nav controller
        // The menu item IDs must match the fragment IDs in nav_main.xml exactly
        binding.bottomNav.setupWithNavController(navController)

        // Hide bottom nav on inner screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideNav = setOf(
                R.id.deviceManagementFragment,
                R.id.addDeviceFragment,
                R.id.qrScannerMainFragment
            )
            binding.bottomNav.visibility =
                if (destination.id in hideNav) View.GONE else View.VISIBLE
        }
    }
}