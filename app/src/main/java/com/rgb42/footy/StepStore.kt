package com.rgb42.footy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Ein Stunden-Eintrag: Schritte und Gehzeit. */
data class Bucket(val key: Long, val steps: Long, val activeMs: Long)

/**
 * Speichert Schritte stundenweise. Schlüssel = yyyyMMddHH (lokale Zeit),
 * damit sortiert der Schlüssel automatisch chronologisch.
 */
class StepStore private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "steps.db", null, 1) {

    companion object {
        @Volatile private var instance: StepStore? = null
        fun get(ctx: Context): StepStore =
            instance ?: synchronized(this) { instance ?: StepStore(ctx).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE hourly (hour INTEGER PRIMARY KEY, steps INTEGER NOT NULL, active_ms INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

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

    /** Alle Einträge mit from <= key < toExclusive. */
    fun range(from: Long, toExclusive: Long): List<Bucket> {
        val out = ArrayList<Bucket>()
        readableDatabase.rawQuery(
            "SELECT hour, steps, active_ms FROM hourly WHERE hour >= ? AND hour < ? ORDER BY hour",
            arrayOf(from.toString(), toExclusive.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Bucket(c.getLong(0), c.getLong(1), c.getLong(2)))
        }
        return out
    }

    fun firstKey(): Long? =
        readableDatabase.rawQuery("SELECT MIN(hour) FROM hourly", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }

    fun deleteAll() {
        writableDatabase.execSQL("DELETE FROM hourly")
    }
}
