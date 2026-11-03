package com.rgb42.footy

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Period { DAY, WEEK, MONTH }

/** Alles, was eine Ansicht (Handy oder Uhr) für einen Zeitraum braucht. */
class Summary(
    val start: LocalDate,
    val end: LocalDate, // exklusiv
    val total: Totals,
    val hasPrev: Boolean,
    val hasNext: Boolean,
    val chartStyle: ChartView.Style,
    val values: FloatArray,
    val visible: Int,
    val labels: List<Pair<Int, String>>,
    val minTop: Float,
    val highlight: String?,
    val decimals: Boolean,
) {
    fun applyTo(chart: ChartView) =
        chart.setData(chartStyle, values, visible, labels, minTop, highlight, decimals)

    fun title(period: Period, short: Boolean = false): String = when (period) {
        Period.DAY -> DAY_TITLE.format(start)
        Period.WEEK -> if (short) "${WEEK_SHORT_NUM.format(start)}–${WEEK_SHORT_NUM.format(end.minusDays(1))}"
                       else "${WEEK_TITLE.format(start)} – ${WEEK_TITLE.format(end.minusDays(1))}"
        Period.MONTH -> MONTH_TITLE.format(start).replaceFirstChar { it.uppercase(GERMAN) }
    }

    companion object {
        private val GERMAN = Locale.GERMANY
        private val DAY_TITLE = DateTimeFormatter.ofPattern("EE, d. MMM", GERMAN)
        private val WEEK_TITLE = DateTimeFormatter.ofPattern("d. MMM", GERMAN)
        private val WEEK_SHORT_NUM = DateTimeFormatter.ofPattern("d.M.", GERMAN)
        private val MONTH_TITLE = DateTimeFormatter.ofPattern("LLL yyyy", GERMAN)

        fun periodStart(period: Period, anchor: LocalDate): LocalDate = when (period) {
            Period.DAY -> anchor
            Period.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Period.MONTH -> anchor.withDayOfMonth(1)
        }

        fun move(period: Period, anchor: LocalDate, dir: Long): LocalDate {
            val a = when (period) {
                Period.DAY -> anchor.plusDays(dir)
                Period.WEEK -> anchor.plusWeeks(dir)
                Period.MONTH -> anchor.plusMonths(dir)
            }
            val today = LocalDate.now()
            return if (a.isAfter(today)) today else a
        }

        fun compute(store: StepStore, prefs: Prefs, period: Period, anchor: LocalDate, metric: Metric): Summary {
            val today = LocalDate.now()
            val start = periodStart(period, anchor)
            val days = when (period) {
                Period.DAY -> 1
                Period.WEEK -> 7
                Period.MONTH -> start.lengthOfMonth()
            }
            val end = start.plusDays(days.toLong())
            val profile = prefs.profile
            val buckets = store.range(Keys.dayStart(start), Keys.dayStart(end))
            val firstDate = store.firstKey()?.let { Keys.date(it) } ?: today

            val total = Totals()
            buckets.forEach { total.add(it, profile) }
            val minTop = metric.minTop(prefs.goal)
            val decimals = metric == Metric.SPEED

            if (period == Period.DAY) {
                val hours = Array(24) { Totals() }
                buckets.forEach { hours[Keys.hourOf(it.key)].add(it, profile) }
                val values = FloatArray(25)
                val running = Totals()
                for (h in 0 until 24) {
                    if (metric == Metric.SPEED) {
                        values[h + 1] = hours[h].speedKmh.toFloat()
                    } else {
                        running.steps += hours[h].steps
                        running.activeMs += hours[h].activeMs
                        running.distanceM += hours[h].distanceM
                        running.kcal += hours[h].kcal
                        values[h + 1] = metric.of(running)
                    }
                }
                val visible = when {
                    start.isBefore(today) -> 25
                    start == today -> LocalTime.now().hour + 2
                    else -> 0
                }
                val labels = (2..24 step 2).map { it to it.toString() }
                val hi = if (visible > 0) metric.format(values[visible - 1]) else null
                return Summary(start, end, total, start.isAfter(firstDate), !end.isAfter(today),
                    ChartView.Style.LINE, values, visible, labels, minTop, hi, decimals)
            }

            val perDay = Array(days) { Totals() }
            buckets.forEach {
                val idx = (Keys.date(it.key).toEpochDay() - start.toEpochDay()).toInt()
                if (idx in 0 until days) perDay[idx].add(it, profile)
            }
            val values = FloatArray(days) { metric.of(perDay[it]) }
            val labels = if (period == Period.WEEK) {
                (0 until 7).map { i ->
                    i to start.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, GERMAN).trimEnd('.')
                }
            } else {
                (5..days step 5).mapIndexed { n, d -> (d - 1) to if (n == 0) "${start.monthValue}/$d" else d.toString() }
            }
            return Summary(start, end, total, start.isAfter(firstDate), !end.isAfter(today),
                ChartView.Style.BARS, values, days, labels, minTop, null, decimals)
        }
    }
}
