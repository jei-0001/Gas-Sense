package com.gassense.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gassense.databinding.FragmentHistoryBinding
import com.gassense.viewmodels.HistoryRecord
import com.gassense.viewmodels.SensorViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val sensorViewModel: SensorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        showPlaceholders()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorViewModel.historyData.collect { records ->
                    if (records.isNotEmpty()) {
                        binding.lineChart.visibility = View.VISIBLE
                        updateUI(records)
                    } else {
                        binding.lineChart.visibility = View.GONE
                        showPlaceholders()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorViewModel.usageTips.collect { tips ->
                    updateTipsUI(tips)
                }
            }
        }
    }

    private fun showPlaceholders() {
        binding.tvGasHighest.text = "---"
        binding.tvGasLowest.text = "---"
        binding.tvGasAverage.text = "---"

        binding.tvPressurePeak.text = "---"
        binding.tvPressureMin.text = "---"
        binding.tvPressureAvg.text = "---"
        
        binding.tvPressureSummaryTitle.text = "History (Awaiting Data)"
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            legend.textColor = Color.WHITE
            setNoDataText("Awaiting data from ESP32...")
            setNoDataTextColor(Color.parseColor("#94A3B8"))
            setBackgroundColor(Color.TRANSPARENT)
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            extraBottomOffset = 12f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#94A3B8")
                textSize = 9f
                valueFormatter = object : ValueFormatter() {
                    private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        return mFormat.format(Date(value.toLong()))
                    }
                }
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1E3A5F")
                textColor = Color.parseColor("#94A3B8")
                textSize = 10f
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
        }
    }

    private fun updateUI(records: List<HistoryRecord>) {
        val gasEntries = records.map { Entry(it.timestamp.toFloat(), it.gasPpm) }
        val pressureEntries = records.map { Entry(it.timestamp.toFloat(), it.pressurePsi) }

        updateChart(gasEntries, pressureEntries)
        updateStats(records)
    }

    private fun updateChart(gasEntries: List<Entry>, pressureEntries: List<Entry>) {
        val gasDataSet = LineDataSet(gasEntries, "Gas PPM").apply {
            color = Color.parseColor("#60A5FA")
            setCircleColor(Color.parseColor("#60A5FA"))
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val pressureDataSet = LineDataSet(pressureEntries, "Pressure PSI").apply {
            color = Color.parseColor("#A78BFA")
            setCircleColor(Color.parseColor("#A78BFA"))
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.lineChart.data = LineData(gasDataSet, pressureDataSet)
        binding.lineChart.invalidate()
    }

    private fun updateStats(records: List<HistoryRecord>) {
        val maxGas = records.maxOf { it.gasPpm }
        val minGas = records.minOf { it.gasPpm }
        val avgGas = records.map { it.gasPpm }.average()

        val maxPress = records.maxOf { it.pressurePsi }
        val minPress = records.minOf { it.pressurePsi }
        val avgPress = records.map { it.pressurePsi }.average()

        binding.tvGasHighest.text = String.format("%.0f PPM", maxGas)
        binding.tvGasLowest.text = String.format("%.0f PPM", minGas)
        binding.tvGasAverage.text = String.format("%.0f PPM", avgGas)

        binding.tvPressurePeak.text = String.format("%.1f PSI", maxPress)
        binding.tvPressureMin.text = String.format("%.1f PSI", minPress)
        binding.tvPressureAvg.text = String.format("%.1f PSI", avgPress)
        
        binding.tvPressureSummaryTitle.text = "Real-time Pressure History"
    }

    private fun updateTipsUI(tips: List<String>) {
        if (tips.isEmpty()) {
            binding.cardUsageInsights.visibility = View.GONE
        } else {
            binding.cardUsageInsights.visibility = View.VISIBLE
            binding.tvInsightText1.text = tips.getOrNull(0) ?: ""
            
            binding.tvInsightText2.apply {
                text = tips.getOrNull(1) ?: ""
                visibility = if (tips.size > 1) View.VISIBLE else View.GONE
            }
            
            binding.tvInsightText3.apply {
                text = tips.getOrNull(2) ?: ""
                visibility = if (tips.size > 2) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
