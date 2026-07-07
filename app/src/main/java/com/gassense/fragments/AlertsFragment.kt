package com.gassense.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.gassense.R
import com.gassense.adapters.AlertsAdapter
import com.gassense.databinding.FragmentAlertsBinding
import com.gassense.models.AlertItem
import com.gassense.viewmodels.SensorViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.Locale

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
            binding.rvAlerts.adapter = AlertsAdapter(alerts) { alert -> showAlertDetail(alert) }
        }
    }

    private fun showAlertDetail(alert: AlertItem) {
        val dialog = BottomSheetDialog(requireContext())
        val isCritical = alert.severityEnum() == AlertItem.Severity.CRITICAL
        val layoutRes = if (isCritical) R.layout.dialog_critical_alert else R.layout.dialog_warning_alert
        val sheetView = layoutInflater.inflate(layoutRes, null)
        dialog.setContentView(sheetView)
        dialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        sheetView.findViewById<View>(R.id.tvSimulationBadge)?.visibility = View.GONE

        sheetView.findViewById<TextView>(R.id.tvAlertDescription)?.text =
            "Recorded ${alert.formattedTimestamp()} · ${alert.previousState} → ${alert.hazardState}"
        sheetView.findViewById<TextView>(R.id.tvGasReading)?.text =
            String.format(Locale.getDefault(), "%.1f %%LEL", alert.lelPercentAtEvent())
        sheetView.findViewById<TextView>(R.id.tvPressureReading)?.text =
            String.format(Locale.getDefault(), "%.0f PSI", alert.pressureAtEvent)
        sheetView.findViewById<TextView>(R.id.tvTempReading)?.text =
            String.format(Locale.getDefault(), "%.1f °C", alert.tempAtEvent)
        sheetView.findViewById<TextView>(R.id.tvHumidityReading)?.text =
            String.format(Locale.getDefault(), "%.0f %%", alert.humidityAtEvent)

        listOf(R.id.btnDismiss, R.id.btnAcknowledge, R.id.btnViewDetails).forEach { id ->
            sheetView.findViewById<View>(id)?.setOnClickListener { dialog.dismiss() }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
