package com.gassense.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gassense.R
import com.gassense.databinding.FragmentHistoryBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    enum class TimePeriod { DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY }
    private var currentPeriod = TimePeriod.WEEKLY

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        setupChipGroup()
        loadPeriodData(TimePeriod.WEEKLY)
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setNoDataText("Loading data...")
            setNoDataTextColor(Color.parseColor("#94A3B8"))
            setBackgroundColor(Color.TRANSPARENT)
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            extraBottomOffset = 8f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#94A3B8")
                textSize = 10f
                granularity = 1f
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

    private fun setupChipGroup() {
        binding.chipGroupTimePeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            val period = when {
                checkedIds.contains(R.id.chipDaily) -> TimePeriod.DAILY
                checkedIds.contains(R.id.chipWeekly) -> TimePeriod.WEEKLY
                checkedIds.contains(R.id.chipMonthly) -> TimePeriod.MONTHLY
                checkedIds.contains(R.id.chipQuarterly) -> TimePeriod.QUARTERLY
                checkedIds.contains(R.id.chipYearly) -> TimePeriod.YEARLY
                else -> TimePeriod.WEEKLY
            }
            loadPeriodData(period)
        }
    }

    private fun loadPeriodData(period: TimePeriod) {
        currentPeriod = period
        val config = getDataConfig(period)

        // Update chart
        updateChart(config.gasEntries, config.pressureEntries, config.labels)

        // Update stats
        binding.tvGasHighest.text = config.gasHighest
        binding.tvGasLowest.text = config.gasLowest
        binding.tvGasAverage.text = config.gasAverage
        binding.tvPressurePeak.text = config.pressurePeak
        binding.tvPressureMin.text = config.pressureMin
        binding.tvPressureAvg.text = config.pressureAvg
        binding.tvPressureSummaryTitle.text = "Pressure Summary (${period.name.lowercase().replaceFirstChar { it.uppercase() }})"

        // Show/hide insights card for Quarterly and Yearly
        val showInsights = period == TimePeriod.QUARTERLY || period == TimePeriod.YEARLY
        binding.cardUsageInsights.visibility = if (showInsights) View.VISIBLE else View.GONE

        // Update insights text for each period
        if (showInsights) {
            updateInsights(period)
        }
    }

    private fun updateInsights(period: TimePeriod) {
        if (period == TimePeriod.YEARLY) {
            binding.tvInsightText1.text = "📈 Annual gas usage is up 12% compared to last year."
            binding.tvInsightText2.text = "⚠️ 9 warning-level events recorded this year — up from 4 last year."
            binding.tvInsightText3.text = "✅ No critical shutoff events triggered this year."
        } else {
            binding.tvInsightText1.text = "📈 Gas usage is 18% higher this quarter compared to last."
            binding.tvInsightText2.text = "⚠️ 3 warning-level events recorded — up from 1 last quarter."
            binding.tvInsightText3.text = "✅ Average gas concentration remains safe at 12% LEL."
        }
    }

    private fun updateChart(
        gasEntries: List<Entry>,
        pressureEntries: List<Entry>,
        labels: List<String>
    ) {
        val gasDataSet = LineDataSet(gasEntries, "Gas %LEL").apply {
            color = Color.parseColor("#60A5FA")
            setCircleColor(Color.parseColor("#60A5FA"))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#60A5FA")
            fillAlpha = 30
        }

        val pressureDataSet = LineDataSet(pressureEntries, "Pressure PSI").apply {
            color = Color.parseColor("#A78BFA")
            setCircleColor(Color.parseColor("#A78BFA"))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#A78BFA")
            fillAlpha = 30
        }

        binding.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.lineChart.data = LineData(gasDataSet, pressureDataSet)
        binding.lineChart.invalidate()
    }

    data class PeriodDataConfig(
        val gasEntries: List<Entry>,
        val pressureEntries: List<Entry>,
        val labels: List<String>,
        val gasHighest: String,
        val gasLowest: String,
        val gasAverage: String,
        val pressurePeak: String,
        val pressureMin: String,
        val pressureAvg: String
    )

    private fun getDataConfig(period: TimePeriod): PeriodDataConfig {
        return when (period) {
            TimePeriod.DAILY -> PeriodDataConfig(
                gasEntries = listOf(8f,9f,11f,10f,13f,12f,14f,12f,10f,9f,8f,11f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                pressureEntries = listOf(85f,84f,86f,87f,88f,89f,87f,86f,85f,84f,85f,86f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                labels = listOf("6am","7","8","9","10","11","12pm","1","2","3","4","5"),
                gasHighest = "14%LEL", gasLowest = "8%LEL", gasAverage = "11%LEL",
                pressurePeak = "89 PSI", pressureMin = "84 PSI", pressureAvg = "86 PSI"
            )
            TimePeriod.WEEKLY -> PeriodDataConfig(
                gasEntries = listOf(10f,12f,9f,15f,11f,8f,13f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                pressureEntries = listOf(88f,90f,85f,93f,87f,80f,89f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                labels = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun"),
                gasHighest = "22%LEL", gasLowest = "5%LEL", gasAverage = "12%LEL",
                pressurePeak = "93 PSI", pressureMin = "80 PSI", pressureAvg = "86 PSI"
            )
            TimePeriod.MONTHLY -> PeriodDataConfig(
                gasEntries = listOf(11f,13f,10f,15f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                pressureEntries = listOf(87f,90f,84f,92f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                labels = listOf("Week 1","Week 2","Week 3","Week 4"),
                gasHighest = "25%LEL", gasLowest = "6%LEL", gasAverage = "12%LEL",
                pressurePeak = "95 PSI", pressureMin = "78 PSI", pressureAvg = "88 PSI"
            )
            TimePeriod.QUARTERLY -> PeriodDataConfig(
                gasEntries = listOf(10f,12f,15f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                pressureEntries = listOf(86f,89f,92f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                labels = listOf("Month 1","Month 2","Month 3"),
                gasHighest = "28%LEL", gasLowest = "4%LEL", gasAverage = "13%LEL",
                pressurePeak = "98 PSI", pressureMin = "76 PSI", pressureAvg = "89 PSI"
            )
            TimePeriod.YEARLY -> PeriodDataConfig(
                gasEntries = listOf(8f,9f,10f,12f,11f,13f,14f,12f,10f,9f,11f,12f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                pressureEntries = listOf(84f,85f,88f,90f,89f,92f,94f,91f,88f,86f,87f,89f).mapIndexed { i, v -> Entry(i.toFloat(), v) },
                labels = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"),
                gasHighest = "32%LEL", gasLowest = "3%LEL", gasAverage = "11%LEL",
                pressurePeak = "102 PSI", pressureMin = "72 PSI", pressureAvg = "88 PSI"
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
