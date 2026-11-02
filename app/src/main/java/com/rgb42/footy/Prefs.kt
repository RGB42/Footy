package com.rgb42.footy

import android.content.Context

/** Einstellungen und Zustand des Sensors (SharedPreferences). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("footy", Context.MODE_PRIVATE)

    var tracking: Boolean
        get() = sp.getBoolean("tracking", true)
        set(v) = sp.edit().putBoolean("tracking", v).apply()

    /** Schrittlänge in cm. */
    var strideCm: Int
        get() = sp.getInt("stride_cm", 70)
        set(v) = sp.edit().putInt("stride_cm", v).apply()

    /** Körpergewicht in kg. */
    var weightKg: Int
        get() = sp.getInt("weight_kg", 70)
        set(v) = sp.edit().putInt("weight_kg", v).apply()

    /** Tagesziel in Schritten. */
    var goal: Int
        get() = sp.getInt("goal", 10000)
        set(v) = sp.edit().putInt("goal", v).apply()

    val profile: Profile get() = Profile(strideCm / 100.0, weightKg.toDouble())

    // --- Zustand des Hardware-Zählers ---
    val lastCounter: Long get() = sp.getLong("last_counter", -1L)
    val lastBoot: Int get() = sp.getInt("last_boot", -1)
    val lastEventMs: Long get() = sp.getLong("last_event_ms", 0L)

    fun saveCounter(value: Long, boot: Int, wallMs: Long) {
        sp.edit()
            .putLong("last_counter", value)
            .putInt("last_boot", boot)
            .putLong("last_event_ms", wallMs)
            .apply()
    }

    /** Nach STOP: beim nächsten START neu ab aktuellem Zählerstand zählen. */
    fun resetCounter() {
        sp.edit().putLong("last_counter", -1L).apply()
    }
}
