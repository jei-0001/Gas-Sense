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
import com.gassense.R
import com.gassense.databinding.FragmentHistoryBinding
import com.gassense.viewmodels.HistoryRecord
import com.gassense.viewmodels.SensorViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val sensorViewModel: SensorViewModel by activityViewModels()

    private enum class Period(val label: String, val windowMillis: Long, val dateFormat: String) {
        DAILY("Daily", 24L * 60 * 60 * 1000, "HH:mm"),
        WEEKLY("Weekly", 7L * 24 * 60 * 60 * 1000, "MMM d"),
        MONTHLY("Monthly", 30L * 24 * 60 * 60 * 1000, "MMM d"),
        QUARTERLY("Quarterly", 90L * 24 * 60 * 60 * 1000, "MMM d"),
        YEARLY("Yearly", 365L * 24 * 60 * 60 * 1000, "MMM yyyy")
    }

    // Chip default-checked in the layout is Weekly — keep this in sync with that.
    private var selectedPeriod = Period.WEEKLY

    // A period only "earns" its own chart once the actual data spans meaningfully more
    // than the tier below it — otherwise Monthly/Quarterly/Yearly would each just redraw
    // whatever short window Weekly already has (same points, coarser label format),
    // which looks identical to Weekly instead of showing there's not enough history yet.
    private val Period.minimumSpanMillis: Long
        get() {
            val tiers = Period.entries
            val index = tiers.indexOf(this)
            return if (index <= 0) 0L else tiers[index - 1].windowMillis
        }

    // Full set most recently pushed by Firebase (up to the last 500 raw snapshots the
    // firmware has sent) — re-filtered locally whenever the user changes the period chip,
    // so switching chips doesn't need to wait on a new network push.
    private var allRecords: List<HistoryRecord> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        setupPeriodFilter()
        showPlaceholders()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorViewModel.historyData.collect { records ->
                    allRecords = records
                    refresh()
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

    private fun setupPeriodFilter() {
        binding.chipGroupTimePeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedPeriod = when (checkedId) {
                R.id.chipDaily -> Period.DAILY
                R.id.chipWeekly -> Period.WEEKLY
                R.id.chipMonthly -> Period.MONTHLY
                R.id.chipQuarterly -> Period.QUARTERLY
                R.id.chipYearly -> Period.YEARLY
                else -> selectedPeriod
            }
            refresh()
        }
    }

    // Re-filters the cached record set down to the selected period and redraws
    // everything. Called both when a new Firebase snapshot arrives and when the user
    // taps a different time-period chip, so the two stay in sync with each other.
    private fun refresh() {
        if (allRecords.isEmpty()) {
            binding.lineChart.visibility = View.GONE
            showPlaceholders(hasAnyData = false)
            return
        }

        val cutoff = System.currentTimeMillis() - selectedPeriod.windowMillis
        val filtered = allRecords.filter { it.timestamp >= cutoff }.sortedBy { it.timestamp }

        // The firmware only retains the last ~500 raw snapshots (a few hours), so a
        // Weekly/Monthly/Quarterly/Yearly selection can legitimately have too little
        // data to plot yet — show that honestly instead of drawing a misleading chart.
        // Beyond just needing >=2 points, the actual span the data covers has to clear
        // this period's minimumSpanMillis (the window of the tier below it), otherwise
        // it would just redraw the exact same points as that lower tier, still labeled
        // as if it were a full week/month/quarter/year of history.
        val actualSpanMillis = if (filtered.size >= 2) filtered.last().timestamp - filtered.first().timestamp else 0L
        val hasEnoughSpread = actualSpanMillis >= selectedPeriod.minimumSpanMillis

        if (filtered.size < 2 || !hasEnoughSpread) {
            binding.lineChart.visibility = View.GONE
            showPlaceholders(hasAnyData = true)
            return
        }

        binding.lineChart.visibility = View.VISIBLE
        updateUI(filtered)
    }

    private fun showPlaceholders(hasAnyData: Boolean = false) {
        binding.tvGasHighest.text = "---"
        binding.tvGasLowest.text = "---"
        binding.tvGasAverage.text = "---"

        binding.tvPressurePeak.text = "---"
        binding.tvPressureMin.text = "---"
        binding.tvPressureAvg.text = "---"

        binding.tvPressureSummaryTitle.text = if (hasAnyData) {
            "Not enough data yet for ${selectedPeriod.label} — try a shorter period"
        } else {
            "History (Awaiting Data)"
        }
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
                setLabelCount(5, true)
            }
            // Gas (%LEL) on the left, Pressure (PSI) on its own right-hand axis — these
            // two units have very different natural ranges, so sharing one axis (the
            // previous behavior) squashed whichever series had the smaller range flat
            // against the bottom. Axis label colors match each line's color so it's
            // clear at a glance which axis belongs to which trend.
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1E3A5F")
                textColor = Color.parseColor("#60A5FA")
                textSize = 10f
                axisMinimum = 0f
            }
            axisRight.apply {
                isEnabled = true
                setDrawGridLines(false)
                textColor = Color.parseColor("#A78BFA")
                textSize = 10f
                axisMinimum = 0f
            }
        }
    }

    private fun updateUI(records: List<HistoryRecord>) {
        // %LEL (roughly 0-100), not raw gasPpm (0-20000+) — matches the "Gas %LEL"
        // legend below the chart (the old code plotted gasPpm despite that label) and
        // keeps the gas line on a scale actually comparable to the pressure line.
        val gasEntries = records.map { Entry(it.timestamp.toFloat(), it.lelPercent) }
        val pressureEntries = records.map { Entry(it.timestamp.toFloat(), it.pressurePsi) }

        applyXAxisFormat()
        updateChart(gasEntries, pressureEntries)
        updateStats(records)
    }

    private fun applyXAxisFormat() {
        val formatter = SimpleDateFormat(selectedPeriod.dateFormat, Locale.getDefault()).apply {
            timeZone = com.gassense.models.DEVICE_TIME_ZONE
        }
        binding.lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return formatter.format(Date(value.toLong()))
            }
        }
    }

    private fun updateChart(gasEntries: List<Entry>, pressureEntries: List<Entry>) {
        // LINEAR, not the previous CUBIC_BEZIER — a smoothed curve can overshoot past
        // the actual recorded min/max between points, which is misleading on a safety
        // trend chart. Per-point circles are also dropped (can be hundreds of points)
        // so the line itself reads as a clean trend instead of a dotted smear.
        val gasDataSet = LineDataSet(gasEntries, "Gas %LEL").apply {
            axisDependency = YAxis.AxisDependency.LEFT
            color = Color.parseColor("#60A5FA")
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.LINEAR
        }

        val pressureDataSet = LineDataSet(pressureEntries, "Pressure PSI").apply {
            axisDependency = YAxis.AxisDependency.RIGHT
            color = Color.parseColor("#A78BFA")
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.LINEAR
        }

        binding.lineChart.data = LineData(gasDataSet, pressureDataSet)
        binding.lineChart.invalidate()
    }

    private fun updateStats(records: List<HistoryRecord>) {
        val maxGas = records.maxOf { it.lelPercent }
        val minGas = records.minOf { it.lelPercent }
        val avgGas = records.map { it.lelPercent }.average()

        val maxPress = records.maxOf { it.pressurePsi }
        val minPress = records.minOf { it.pressurePsi }
        val avgPress = records.map { it.pressurePsi }.average()

        binding.tvGasHighest.text = String.format(Locale.getDefault(), "%.1f%%LEL", maxGas)
        binding.tvGasLowest.text = String.format(Locale.getDefault(), "%.1f%%LEL", minGas)
        binding.tvGasAverage.text = String.format(Locale.getDefault(), "%.1f%%LEL", avgGas)

        binding.tvPressurePeak.text = String.format(Locale.getDefault(), "%.1f PSI", maxPress)
        binding.tvPressureMin.text = String.format(Locale.getDefault(), "%.1f PSI", minPress)
        binding.tvPressureAvg.text = String.format(Locale.getDefault(), "%.1f PSI", avgPress)

        binding.tvPressureSummaryTitle.text = "Pressure Summary (${selectedPeriod.label})"
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