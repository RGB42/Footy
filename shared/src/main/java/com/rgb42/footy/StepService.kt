package com.rgb42.footy

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import com.rgb42.footy.shared.R
import java.time.LocalDate

/**
 * Zählt Schritte über den Hardware-Schrittzähler (TYPE_STEP_COUNTER).
 *
 * Stromsparend:
 *  - Der Sensor-Hub zählt selbst; die CPU wird dafür nicht geweckt (non-wakeup Sensor).
 *  - Im Hintergrund werden Ereignisse bis zu 10 Minuten gebündelt (Batching).
 *  - Nur bei geöffneter App wird live (ohne Verzögerung) aktualisiert.
 *  - Kein Wakelock, kein GPS, kein Beschleunigungssensor-Polling.
 */
class StepService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "steps"
        private const val NOTIFICATION_ID = 1
        private const val BATCH_LATENCY_US = 10 * 60 * 1_000_000 // 10 Minuten

        /** Ruft die Activity bei neuen Schritten auf (Main-Thread). */
        @JvmStatic var listener: (() -> Unit)? = null

        /** Wird nach jedem Speichern aufgerufen (Uhr: Sync zum Handy). */
        @JvmStatic var afterRecord: ((Context) -> Unit)? = null

        private var instance: StepService? = null

        val isRunning: Boolean get() = instance != null

        /** Bei sichtbarer App live zählen, sonst bündeln. */
        var uiVisible = false
            set(v) {
                field = v
                instance?.registerSensor()
            }

        fun hasPermission(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        fun hasSensor(ctx: Context): Boolean =
            (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
                .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        fun start(ctx: Context) {
            if (!hasPermission(ctx) || !hasSensor(ctx)) return
            ctx.startForegroundService(Intent(ctx, StepService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, StepService::class.java))
        }
    }

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null
    private lateinit var prefs: Prefs
    private lateinit var store: StepStore
    private var registeredLive: Boolean? = null
    private var lastNotifiedSteps = -1L
    private var lastNotifiedAt = 0L
    private var lastNotifiedDay: LocalDate? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        store = StepStore.get(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Schrittzähler", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasPermission(this) || sensor == null || !prefs.tracking) {
            stopSelf()
            return START_NOT_STICKY
        }
        val n = buildNotification(todaySteps())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        registerSensor()
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        registeredLive = null
        instance = null
        super.onDestroy()
    }

    private fun registerSensor() {
        val s = sensor ?: return
        val live = uiVisible
        if (registeredLive == live) return
        if (registeredLive != null) sensorManager.unregisterListener(this)
        sensorManager.registerListener(
            this, s, SensorManager.SENSOR_DELAY_NORMAL, if (live) 0 else BATCH_LATENCY_US
        )
        registeredLive = live
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values[0].toLong()
        val wallMs = System.currentTimeMillis() -
            (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
        if (record(value, wallMs)) {
            listener?.invoke()
            afterRecord?.invoke(this)
            updateNotification()
        }
    }

    /** Wandelt den kumulativen Zählerstand in Schritte pro Stunde um. */
    private fun record(value: Long, wallMs: Long): Boolean {
        val boot = bootCount()
        val last = prefs.lastCounter
        if (last < 0) {
            prefs.saveCounter(value, boot, wallMs)
            return false
        }
        val rebooted = boot != prefs.lastBoot || value < last
        val delta = if (rebooted) value else value - last
        val lastWall = prefs.lastEventMs
        prefs.saveCounter(value, boot, wallMs)
        if (delta <= 0 || delta > 100_000) return false

        // Gehzeit: Zeit zwischen zwei Ereignissen, höchstens 0,75 s pro Schritt
        val dt = wallMs - lastWall
        val active = if (rebooted || dt <= 0) delta * 550 else minOf(dt, delta * 750)
        store.add(Keys.hour(wallMs), delta, active)
        return true
    }

    private fun bootCount(): Int =
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun todaySteps(): Long {
        val today = LocalDate.now()
        return store.range(Keys.dayStart(today), Keys.dayStart(today.plusDays(1))).sumOf { it.steps }
    }

    private fun updateNotification() {
        val now = SystemClock.elapsedRealtime()
        val today = LocalDate.now()
        // höchstens einmal pro Minute aktualisieren
        if (now - lastNotifiedAt < 60_000 && today == lastNotifiedDay) return
        val steps = todaySteps()
        if (steps == lastNotifiedSteps) return
        lastNotifiedAt = now
        lastNotifiedSteps = steps
        lastNotifiedDay = today
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(steps))
    }

    private fun buildNotification(steps: Long): Notification {
        val launch = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent())
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${Fmt.int(steps)} Schritte heute")
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(pi)
            .build()
    }
}
