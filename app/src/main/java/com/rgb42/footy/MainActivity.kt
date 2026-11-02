package com.rgb42.footy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class MainActivity : Activity() {

    private enum class Period { DAY, WEEK, MONTH }

    private lateinit var prefs: Prefs
    private lateinit var store: StepStore

    private var period = Period.DAY
    private var anchor: LocalDate = LocalDate.now()
    private var metric = Metric.STEPS
    private var followToday = true

    private lateinit var tabTexts: Map<Period, TextView>
    private lateinit var tabLines: Map<Period, View>
    private lateinit var tiles: Map<Metric, View>
    private lateinit var title: TextView
    private lateinit var prev: ImageButton
    private lateinit var next: ImageButton
    private lateinit var stepsValue: TextView
    private lateinit var kcalValue: TextView
    private lateinit var distanceValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var speedValue: TextView
    private lateinit var chart: ChartView
    private lateinit var startStopText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var refreshPending = false
    private val refreshRunnable = Runnable { refreshPending = false; refresh() }

    private val german = Locale.GERMANY
    private val dayTitle = DateTimeFormatter.ofPattern("EE, d. MMM", german)
    private val weekTitleShort = DateTimeFormatter.ofPattern("d. MMM", german)
    private val monthTitle = DateTimeFormatter.ofPattern("LLL yyyy", german)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        store = StepStore.get(this)

        findViewById<View>(R.id.content).background = GradientBackground()
        applyInsets()

        tabTexts = mapOf(
            Period.DAY to findViewById(R.id.tabDayText),
            Period.WEEK to findViewById(R.id.tabWeekText),
            Period.MONTH to findViewById(R.id.tabMonthText)
        )
        tabLines = mapOf(
            Period.DAY to findViewById(R.id.tabDayLine),
            Period.WEEK to findViewById(R.id.tabWeekLine),
            Period.MONTH to findViewById(R.id.tabMonthLine)
        )
        findViewById<View>(R.id.tabDay).setOnClickListener { selectPeriod(Period.DAY) }
        findViewById<View>(R.id.tabWeek).setOnClickListener { selectPeriod(Period.WEEK) }
        findViewById<View>(R.id.tabMonth).setOnClickListener { selectPeriod(Period.MONTH) }
        findViewById<View>(R.id.menuButton).setOnClickListener { showMenu(it) }

        title = findViewById(R.id.periodTitle)
        prev = findViewById(R.id.prevButton)
        next = findViewById(R.id.nextButton)
        prev.setOnClickListener { move(-1) }
        next.setOnClickListener { move(1) }

        stepsValue = findViewById(R.id.stepsValue)
        kcalValue = findViewById(R.id.kcalValue)
        distanceValue = findViewById(R.id.distanceValue)
        timeValue = findViewById(R.id.timeValue)
        speedValue = findViewById(R.id.speedValue)
        chart = findViewById(R.id.chart)
        startStopText = findViewById(R.id.startStopText)

        tiles = mapOf(
            Metric.KCAL to findViewById(R.id.tileKcal),
            Metric.DISTANCE to findViewById(R.id.tileDistance),
            Metric.TIME to findViewById(R.id.tileTime),
            Metric.SPEED to findViewById(R.id.tileSpeed)
        )
        // Kachel antippen → Diagramm zeigt diese Größe; nochmal antippen → zurück zu Schritten
        for ((m, v) in tiles) v.setOnClickListener { selectMetric(if (metric == m) Metric.STEPS else m) }
        findViewById<View>(R.id.stepsRow).setOnClickListener { selectMetric(Metric.STEPS) }
        findViewById<View>(R.id.startStopButton).setOnClickListener { toggleTracking() }

        savedInstanceState?.let {
            period = Period.entries[it.getInt("period", 0)]
            metric = Metric.entries[it.getInt("metric", 0)]
            anchor = LocalDate.ofEpochDay(it.getLong("anchor", LocalDate.now().toEpochDay()))
        }
        selectPeriod(period)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("period", period.ordinal)
        outState.putInt("metric", metric.ordinal)
        outState.putLong("anchor", anchor.toEpochDay())
    }

    override fun onResume() {
        super.onResume()
        StepService.listener = { scheduleRefresh() }
        StepService.uiVisible = true
        if (followToday) anchor = LocalDate.now()
        ensureTracking()
        refresh()
    }

    override fun onPause() {
        followToday = anchor == LocalDate.now()
        StepService.listener = null
        StepService.uiVisible = false
        handler.removeCallbacks(refreshRunnable)
        refreshPending = false
        super.onPause()
    }

    private fun applyInsets() {
        if (Build.VERSION.SDK_INT < 35) return // ältere Android-Versionen: System übernimmt Abstände
        val top = findViewById<View>(R.id.topBar)
        val content = findViewById<View>(R.id.content)
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            top.setPadding(bars.left, bars.top, bars.right, 0)
            content.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
    }

    // ---------- Zählung starten/stoppen ----------

    private fun ensureTracking() {
        updateStartStop()
        if (!prefs.tracking) return
        if (!StepService.hasSensor(this)) {
            startStopText.text = "—"
            Toast.makeText(this, "Dieses Gerät hat keinen Schrittzähler-Sensor.", Toast.LENGTH_LONG).show()
            return
        }
        val needed = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("footy", MODE_PRIVATE).getBoolean("asked_notif", false)
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
            getSharedPreferences("footy", MODE_PRIVATE).edit().putBoolean("asked_notif", true).apply()
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 1)
            return
        }
        if (!StepService.isRunning) StepService.start(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (StepService.hasPermission(this)) {
            StepService.start(this)
        } else {
            Toast.makeText(this, "Ohne die Berechtigung „Körperliche Aktivität“ können keine Schritte gezählt werden.", Toast.LENGTH_LONG).show()
        }
        updateStartStop()
    }

    private fun toggleTracking() {
        if (prefs.tracking && StepService.hasPermission(this)) {
            prefs.tracking = false
            prefs.resetCounter()
            StepService.stop(this)
        } else {
            prefs.tracking = true
            if (!StepService.hasPermission(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) &&
                getSharedPreferences("footy", MODE_PRIVATE).getBoolean("asked_ar", false)
            ) {
                // Dauerhaft abgelehnt → App-Einstellungen öffnen
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            getSharedPreferences("footy", MODE_PRIVATE).edit().putBoolean("asked_ar", true).apply()
            ensureTracking()
        }
        updateStartStop()
    }

    private fun updateStartStop() {
        startStopText.text = if (prefs.tracking && StepService.hasPermission(this)) "STOP" else "START"
    }

    // ---------- Navigation ----------

    private fun selectPeriod(p: Period) {
        period = p
        for ((k, tv) in tabTexts) tv.setTextColor(if (k == p) Color.WHITE else getColor(R.color.tab_inactive))
        for ((k, v) in tabLines) v.visibility = if (k == p) View.VISIBLE else View.INVISIBLE
        refresh()
    }

    private fun selectMetric(m: Metric) {
        metric = m
        refresh()
    }

    private fun move(dir: Long) {
        anchor = when (period) {
            Period.DAY -> anchor.plusDays(dir)
            Period.WEEK -> anchor.plusWeeks(dir)
            Period.MONTH -> anchor.plusMonths(dir)
        }
        val today = LocalDate.now()
        if (anchor.isAfter(today)) anchor = today
        refresh()
    }

    private fun periodStart(): LocalDate = when (period) {
        Period.DAY -> anchor
        Period.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.MONTH -> anchor.withDayOfMonth(1)
    }

    private fun periodDays(start: LocalDate): Int = when (period) {
        Period.DAY -> 1
        Period.WEEK -> 7
        Period.MONTH -> start.lengthOfMonth()
    }

    private fun scheduleRefresh() {
        if (refreshPending) return
        refreshPending = true
        handler.postDelayed(refreshRunnable, 700)
    }

    // ---------- Anzeige ----------

    private fun refresh() {
        val today = LocalDate.now()
        val start = periodStart()
        val days = periodDays(start)
        val end = start.plusDays(days.toLong()) // exklusiv
        val profile = prefs.profile
        val buckets = store.range(Keys.dayStart(start), Keys.dayStart(end))

        // Titel
        title.text = when (period) {
            Period.DAY -> dayTitle.format(start)
            Period.WEEK -> "${weekTitleShort.format(start)} – ${weekTitleShort.format(end.minusDays(1))}"
            Period.MONTH -> monthTitle.format(start).replaceFirstChar { it.uppercase(german) }
        }
        val firstDate = store.firstKey()?.let { Keys.date(it) } ?: today
        prev.visibility = if (start.isAfter(firstDate)) View.VISIBLE else View.INVISIBLE
        next.visibility = if (end.isAfter(today)) View.INVISIBLE else View.VISIBLE

        // Summen
        val total = Totals()
        buckets.forEach { total.add(it, profile) }
        stepsValue.text = Fmt.int(total.steps)
        kcalValue.text = withUnit(Fmt.one(total.kcal), "kcal")
        distanceValue.text = withUnit(Fmt.int(Math.round(total.distanceM)), "m")
        timeValue.text = Fmt.duration(total.activeMs)
        speedValue.text = withUnit(Fmt.one(total.speedKmh), "km/h")
        for ((m, v) in tiles) v.isSelected = m == metric

        // Diagramm
        val minTop = metric.minTop(prefs.goal)
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
            chart.setData(ChartView.Style.LINE, values, visible, labels, minTop, hi, metric == Metric.SPEED)
        } else {
            val perDay = Array(days) { Totals() }
            buckets.forEach {
                val idx = (Keys.date(it.key).toEpochDay() - start.toEpochDay()).toInt()
                if (idx in 0 until days) perDay[idx].add(it, profile)
            }
            val values = FloatArray(days) { metric.of(perDay[it]) }
            val labels = if (period == Period.WEEK) {
                (0 until 7).map { i ->
                    i to start.plusDays(i.toLong()).dayOfWeek
                        .getDisplayName(java.time.format.TextStyle.SHORT, german).trimEnd('.')
                }
            } else {
                (5..days step 5).mapIndexed { n, d ->
                    (d - 1) to if (n == 0) "${start.monthValue}/$d" else d.toString()
                }
            }
            chart.setData(ChartView.Style.BARS, values, days, labels, minTop, null, metric == Metric.SPEED)
        }
    }

    private fun withUnit(value: String, unit: String): CharSequence {
        val s = SpannableString("$value $unit")
        s.setSpan(RelativeSizeSpan(0.78f), value.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    // ---------- Menü ----------

    private fun showMenu(anchorView: View) {
        val pm = PopupMenu(this, anchorView)
        pm.menu.add(0, 1, 0, "Einstellungen")
        pm.menu.add(0, 2, 1, "Akku-Optimierung ausschalten")
        pm.menu.add(0, 3, 2, "Alle Daten löschen")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> showSettings()
                2 -> requestNoBatteryOptimization()
                3 -> confirmDelete()
            }
            true
        }
        pm.show()
    }

    @SuppressLint("BatteryLife")
    private fun requestNoBatteryOptimization() {
        val pmgr = getSystemService(PowerManager::class.java)
        if (pmgr.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Bereits ausgeschaltet.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Alle Daten löschen?")
            .setMessage("Alle gespeicherten Schritte werden unwiderruflich gelöscht.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Löschen") { _, _ -> store.deleteAll(); refresh() }
            .show()
    }

    private fun showSettings() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        fun field(label: String, value: Int): EditText {
            box.addView(TextView(this).apply {
                text = label; textSize = 14f; setPadding(0, pad / 2, 0, 0)
            })
            val e = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(value.toString())
                gravity = Gravity.START
                setSelectAllOnFocus(true)
            }
            box.addView(e)
            return e
        }
        val stride = field("Schrittlänge (cm)", prefs.strideCm)
        val weight = field("Gewicht (kg)", prefs.weightKg)
        val goal = field("Tagesziel (Schritte)", prefs.goal)
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Einstellungen")
            .setView(box)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ ->
                stride.text.toString().toIntOrNull()?.takeIf { it in 30..150 }?.let { prefs.strideCm = it }
                weight.text.toString().toIntOrNull()?.takeIf { it in 20..300 }?.let { prefs.weightKg = it }
                goal.text.toString().toIntOrNull()?.takeIf { it in 100..100_000 }?.let { prefs.goal = it }
                refresh()
            }
            .show()
    }
}
