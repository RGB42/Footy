package com.rgb42.footy.wear

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.rgb42.footy.ChartView
import com.rgb42.footy.Fmt
import com.rgb42.footy.Metric
import com.rgb42.footy.Period
import com.rgb42.footy.Prefs
import com.rgb42.footy.StepService
import com.rgb42.footy.StepStore
import com.rgb42.footy.Summary
import java.time.LocalDate

/** Uhr-Ansicht: gleiche Funktionen wie am Handy, schwarzer Hintergrund (spart Strom bei AMOLED). */
class WatchActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var store: StepStore

    private var period = Period.DAY
    private var anchor: LocalDate = LocalDate.now()
    private var metric = Metric.STEPS
    private var followToday = true

    private lateinit var tabs: Map<Period, TextView>
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch)
        prefs = Prefs(this)
        store = StepStore.get(this)

        tabs = mapOf(
            Period.DAY to findViewById(R.id.tabDay),
            Period.WEEK to findViewById(R.id.tabWeek),
            Period.MONTH to findViewById(R.id.tabMonth)
        )
        for ((p, v) in tabs) v.setOnClickListener { selectPeriod(p) }

        title = findViewById(R.id.periodTitle)
        prev = findViewById(R.id.prevButton)
        next = findViewById(R.id.nextButton)
        prev.setOnClickListener { anchor = Summary.move(period, anchor, -1); refresh() }
        next.setOnClickListener { anchor = Summary.move(period, anchor, 1); refresh() }

        stepsValue = findViewById(R.id.stepsValue)
        kcalValue = findViewById(R.id.kcalValue)
        distanceValue = findViewById(R.id.distanceValue)
        timeValue = findViewById(R.id.timeValue)
        speedValue = findViewById(R.id.speedValue)
        chart = findViewById(R.id.chart)
        chart.compact = true
        startStopText = findViewById(R.id.startStopText)

        tiles = mapOf(
            Metric.KCAL to findViewById(R.id.tileKcal),
            Metric.DISTANCE to findViewById(R.id.tileDistance),
            Metric.TIME to findViewById(R.id.tileTime),
            Metric.SPEED to findViewById(R.id.tileSpeed)
        )
        for ((m, v) in tiles) v.setOnClickListener { metric = if (metric == m) Metric.STEPS else m; refresh() }
        findViewById<View>(R.id.stepsRow).setOnClickListener { metric = Metric.STEPS; refresh() }
        findViewById<View>(R.id.startStopButton).setOnClickListener { toggleTracking() }

        // Drehkrone / Drehlünette scrollt
        findViewById<ScrollView>(R.id.scroll).requestFocus()
        selectPeriod(period)
    }

    override fun onResume() {
        super.onResume()
        StepService.listener = { scheduleRefresh() }
        StepService.uiVisible = true
        if (followToday) anchor = LocalDate.now()
        ensureTracking()
        refresh()
        WatchSync.sync(this, force = true)
    }

    override fun onPause() {
        followToday = anchor == LocalDate.now()
        StepService.listener = null
        StepService.uiVisible = false
        handler.removeCallbacks(refreshRunnable)
        refreshPending = false
        super.onPause()
    }

    private fun ensureTracking() {
        updateStartStop()
        if (!prefs.tracking) return
        if (!StepService.hasSensor(this)) {
            Toast.makeText(this, "Kein Schrittsensor", Toast.LENGTH_LONG).show()
            return
        }
        val needed = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
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
        if (StepService.hasPermission(this)) StepService.start(this)
        else Toast.makeText(this, "Berechtigung „Körperliche Aktivität“ nötig", Toast.LENGTH_LONG).show()
        updateStartStop()
    }

    private fun toggleTracking() {
        if (prefs.tracking && StepService.hasPermission(this)) {
            prefs.tracking = false
            prefs.resetCounter()
            StepService.stop(this)
        } else {
            prefs.tracking = true
            ensureTracking()
        }
        updateStartStop()
    }

    private fun updateStartStop() {
        startStopText.text = if (prefs.tracking && StepService.hasPermission(this)) "STOP" else "START"
    }

    private fun selectPeriod(p: Period) {
        period = p
        for ((k, tv) in tabs) {
            val sel = k == p
            tv.setTextColor(if (sel) Color.WHITE else getColor(com.rgb42.footy.shared.R.color.tab_inactive))
            val s = SpannableString(tv.text.toString())
            if (sel) s.setSpan(android.text.style.UnderlineSpan(), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tv.text = s
        }
        refresh()
    }

    private fun scheduleRefresh() {
        if (refreshPending) return
        refreshPending = true
        handler.postDelayed(refreshRunnable, 1000)
    }

    private fun refresh() {
        val sum = Summary.compute(store, prefs, period, anchor, metric)
        title.text = sum.title(period, short = true)
        prev.visibility = if (sum.hasPrev) View.VISIBLE else View.INVISIBLE
        next.visibility = if (sum.hasNext) View.VISIBLE else View.INVISIBLE
        val t = sum.total
        stepsValue.text = Fmt.int(t.steps)
        kcalValue.text = withUnit(Fmt.one(t.kcal), "kcal")
        distanceValue.text = withUnit(Fmt.int(Math.round(t.distanceM)), "m")
        timeValue.text = Fmt.duration(t.activeMs)
        speedValue.text = withUnit(Fmt.one(t.speedKmh), "km/h")
        for ((m, v) in tiles) v.isSelected = m == metric
        sum.applyTo(chart)
    }

    private fun withUnit(value: String, unit: String): CharSequence {
        val s = SpannableString("$value $unit")
        s.setSpan(RelativeSizeSpan(0.75f), value.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }
}
