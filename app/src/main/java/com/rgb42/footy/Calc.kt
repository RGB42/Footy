package com.rgb42.footy

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

data class Profile(val strideM: Double, val weightKg: Double)

/** Summen über beliebig viele Stunden-Einträge. */
class Totals {
    var steps = 0L
    var activeMs = 0L
    var distanceM = 0.0
    var kcal = 0.0

    val speedKmh: Double
        get() = if (activeMs > 0) (distanceM / 1000.0) / (activeMs / 3_600_000.0) else 0.0

    fun add(b: Bucket, p: Profile) {
        steps += b.steps
        activeMs += b.activeMs
        val dist = b.steps * p.strideM
        distanceM += dist
        val hours = b.activeMs / 3_600_000.0
        if (hours > 0) {
            val speed = (dist / 1000.0) / hours
            kcal += met(speed) * p.weightKg * hours
        }
    }
}

/** MET-Wert nach Gehgeschwindigkeit (Compendium of Physical Activities). */
private val MET_TABLE = arrayOf(
    1.6 to 2.0, 3.2 to 2.8, 4.0 to 3.0, 4.8 to 3.5, 5.6 to 4.3,
    6.4 to 5.0, 7.2 to 7.0, 8.0 to 8.3, 9.7 to 9.8, 11.3 to 11.0
)

fun met(speedKmh: Double): Double {
    if (speedKmh <= MET_TABLE.first().first) return MET_TABLE.first().second
    for (i in 1 until MET_TABLE.size) {
        val (s1, m1) = MET_TABLE[i]
        if (speedKmh <= s1) {
            val (s0, m0) = MET_TABLE[i - 1]
            return m0 + (m1 - m0) * (speedKmh - s0) / (s1 - s0)
        }
    }
    return MET_TABLE.last().second
}

enum class Metric {
    STEPS, KCAL, DISTANCE, TIME, SPEED;

    fun of(t: Totals): Float = when (this) {
        STEPS -> t.steps.toFloat()
        KCAL -> t.kcal.toFloat()
        DISTANCE -> t.distanceM.toFloat()
        TIME -> t.activeMs / 60_000f
        SPEED -> t.speedKmh.toFloat()
    }

    /** Mindesthöhe der Y-Achse. */
    fun minTop(goal: Int): Float = when (this) {
        STEPS -> goal.toFloat()
        KCAL -> 50f
        DISTANCE -> 500f
        TIME -> 30f
        SPEED -> 5.5f
    }

    fun format(v: Float): String = when (this) {
        STEPS, DISTANCE, TIME -> Math.round(v).toString()
        KCAL, SPEED -> Fmt.one(v.toDouble())
    }
}

object Fmt {
    private val de = DecimalFormatSymbols(Locale.GERMANY)
    private val intF = DecimalFormat("#,##0", de)
    private val oneF = DecimalFormat("#,##0.0", de)
    private val oneNoGroup = DecimalFormat("0.0", de)

    fun int(v: Long): String = intF.format(v)
    fun one(v: Double): String = oneF.format(v)
    fun axis(v: Float, decimals: Boolean): String =
        if (decimals) oneNoGroup.format(v.toDouble()) else Math.round(v).toString()

    fun duration(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.GERMANY, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }
}

object Keys {
    fun hour(dt: LocalDateTime): Long =
        dt.year * 1_000_000L + dt.monthValue * 10_000L + dt.dayOfMonth * 100L + dt.hour

    fun hour(wallMs: Long): Long =
        hour(LocalDateTime.ofInstant(Instant.ofEpochMilli(wallMs), ZoneId.systemDefault()))

    fun dayStart(d: LocalDate): Long =
        d.year * 1_000_000L + d.monthValue * 10_000L + d.dayOfMonth * 100L

    fun date(key: Long): LocalDate =
        LocalDate.of((key / 1_000_000).toInt(), ((key / 10_000) % 100).toInt(), ((key / 100) % 100).toInt())

    fun hourOf(key: Long): Int = (key % 100).toInt()
}
