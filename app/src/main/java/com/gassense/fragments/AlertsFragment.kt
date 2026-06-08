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
import androidx.recyclerview.widget.LinearLayoutManager
import com.gassense.adapters.AlertsAdapter
import com.gassense.databinding.FragmentAlertsBinding
import com.gassense.models.AlertItem
import com.gassense.viewmodels.SensorViewModel
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private val sensorViewModel: SensorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAlerts.layoutManager = LinearLayoutManager(context)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorViewModel.alertsData.collect { alerts ->
                    updateUI(alerts)
                }
            }
        }
    }

    private fun updateUI(alerts: List<AlertItem>) {
        if (alerts.isEmpty()) {
            binding.rvAlerts.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.chipAlertsCount.visibility = View.GONE
        } else {
            binding.rvAlerts.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
            binding.chipAlertsCount.visibility = View.VISIBLE
            binding.chipAlertsCount.text = "${alerts.size} alerts"
            binding.rvAlerts.adapter = AlertsAdapter(alerts)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
