package com.rgb42.footy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Ein Stunden-Eintrag: Schritte und Gehzeit. */
data class Bucket(val key: Long, val steps: Long, val activeMs: Long)

/**
 * Speichert Schritte stundenweise. Schlüssel = yyyyMMddHH (lokale Zeit),
 * damit sortiert der Schlüssel automatisch chronologisch.
 *
 * Tabelle `hourly`: eigene Zählung dieses Geräts.
 * Tabelle `hourly_watch`: von der Uhr empfangene Werte (nur auf dem Handy).
 * [range] führt beide zusammen: pro Stunde zählt das Gerät mit mehr Schritten,
 * damit beim gleichzeitigen Tragen nichts doppelt gezählt wird.
 */
class StepStore private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "steps.db", null, 2) {

    companion object {
        @Volatile private var instance: StepStore? = null
        fun get(ctx: Context): StepStore =
            instance ?: synchronized(this) { instance ?: StepStore(ctx).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE hourly (hour INTEGER PRIMARY KEY, steps INTEGER NOT NULL, active_ms INTEGER NOT NULL)"
        )
        createWatchTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createWatchTable(db)
    }

    private fun createWatchTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS hourly_watch (hour INTEGER PRIMARY KEY, steps INTEGER NOT NULL, active_ms INTEGER NOT NULL)"
        )
    }

    fun add(hourKey: Long, steps: Long, activeMs: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val upd = db.compileStatement("UPDATE hourly SET steps = steps + ?, active_ms = active_ms + ? WHERE hour = ?")
            upd.bindLong(1, steps); upd.bindLong(2, activeMs); upd.bindLong(3, hourKey)
            if (upd.executeUpdateDelete() == 0) {
                val ins = db.compileStatement("INSERT INTO hourly (hour, steps, active_ms) VALUES (?, ?, ?)")
                ins.bindLong(1, hourKey); ins.bindLong(2, steps); ins.bindLong(3, activeMs)
                ins.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Zusammengeführte Einträge (eigene + Uhr) mit from <= key < toExclusive. */
    fun range(from: Long, toExclusive: Long): List<Bucket> {
        val own = query("hourly", from, toExclusive)
        val watch = query("hourly_watch", from, toExclusive)
        if (watch.isEmpty()) return own
        val merged = java.util.TreeMap<Long, Bucket>()
        for (b in own) merged[b.key] = b
        for (b in watch) {
            val o = merged[b.key]
            if (o == null || b.steps > o.steps) merged[b.key] = b
        }
        return merged.values.toList()
    }

    /** Nur die eigene Zählung dieses Geräts. */
    fun rangeOwn(from: Long, toExclusive: Long): List<Bucket> = query("hourly", from, toExclusive)

    private fun query(table: String, from: Long, toExclusive: Long): List<Bucket> {
        val out = ArrayList<Bucket>()
        readableDatabase.rawQuery(
            "SELECT hour, steps, active_ms FROM $table WHERE hour >= ? AND hour < ? ORDER BY hour",
            arrayOf(from.toString(), toExclusive.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Bucket(c.getLong(0), c.getLong(1), c.getLong(2)))
        }
        return out
    }

    /** Ersetzt die Uhr-Werte eines Tages (24 Stunden). */
    fun replaceWatchDay(dayStartKey: Long, steps: LongArray, activeMs: LongArray) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "DELETE FROM hourly_watch WHERE hour >= ? AND hour < ?",
                arrayOf(dayStartKey, dayStartKey + 100)
            )
            val ins = db.compileStatement("INSERT INTO hourly_watch (hour, steps, active_ms) VALUES (?, ?, ?)")
            for (h in 0 until minOf(24, steps.size)) {
                if (steps[h] <= 0) continue
                ins.bindLong(1, dayStartKey + h)
                ins.bindLong(2, steps[h])
                ins.bindLong(3, activeMs.getOrElse(h) { 0L })
                ins.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Tage (Schlüssel yyyyMMdd00), an denen dieses Gerät selbst gezählt hat. */
    fun ownDays(): List<Long> {
        val out = ArrayList<Long>()
        readableDatabase.rawQuery("SELECT DISTINCT hour / 100 FROM hourly ORDER BY 1", null).use { c ->
            while (c.moveToNext()) out.add(c.getLong(0) * 100)
        }
        return out
    }

    fun firstKey(): Long? =
        readableDatabase.rawQuery(
            "SELECT MIN(h) FROM (SELECT MIN(hour) AS h FROM hourly UNION ALL SELECT MIN(hour) FROM hourly_watch)", null
        ).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }

    fun deleteAll() {
        writableDatabase.execSQL("DELETE FROM hourly")
        writableDatabase.execSQL("DELETE FROM hourly_watch")
    }
}
