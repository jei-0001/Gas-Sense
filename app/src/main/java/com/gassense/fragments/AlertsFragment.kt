package com.gassense.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gassense.adapters.AlertsAdapter
import com.gassense.databinding.FragmentAlertsBinding
import com.gassense.models.AlertItem

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val alerts = getSampleAlerts()

        if (alerts.isEmpty()) {
            binding.rvAlerts.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.chipAlertsCount.visibility = View.GONE
        } else {
            binding.rvAlerts.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
            binding.chipAlertsCount.text = "${alerts.size} alerts"
            binding.rvAlerts.layoutManager = LinearLayoutManager(context)
            binding.rvAlerts.adapter = AlertsAdapter(alerts)
        }
    }

    private fun getSampleAlerts(): List<AlertItem> = listOf(
        AlertItem(
            title = "Gas Leak Detected",
            description = "Gas concentration exceeded 25% LEL — critical threshold",
            severity = AlertItem.Severity.CRITICAL,
            timestamp = "Today, 2:34 PM",
            autoShutoffTriggered = true
        ),
        AlertItem(
            title = "High Pressure Warning",
            description = "Cylinder pressure reached 120 PSI, approaching warning level",
            severity = AlertItem.Severity.WARNING,
            timestamp = "Today, 11:15 AM",
            autoShutoffTriggered = false
        ),
        AlertItem(
            title = "High Temperature",
            description = "Ambient temperature exceeded 40°C near the cylinder",
            severity = AlertItem.Severity.WARNING,
            timestamp = "Yesterday, 4:50 PM",
            autoShutoffTriggered = false
        ),
        AlertItem(
            title = "Gas Leak Detected",
            description = "Gas concentration exceeded 25% LEL — critical threshold",
            severity = AlertItem.Severity.CRITICAL,
            timestamp = "Mar 1, 8:22 AM",
            autoShutoffTriggered = true
        )
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}