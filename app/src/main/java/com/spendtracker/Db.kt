package com.spendtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/** Bumped when a new SMS debit is saved so the UI refreshes. */
object Bus { var tick by mutableIntStateOf(0) }

data class Txn(val id: Long, val type: String, val amount: Double, val label: String, val ts: Long)

class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "spend.db", null, 1) {
    private val prefs = ctx.applicationContext.getSharedPreferences("p", Context.MODE_PRIVATE)

    var capital: Double?
        get() = prefs.getString("capital", null)?.toDoubleOrNull()
        set(v) { prefs.edit().putString("capital", v?.toString()).apply() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE txn(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, amount REAL, label TEXT, ts INTEGER, hash TEXT UNIQUE)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun insert(type: String, amount: Double, label: String, ts: Long, hash: String? = null): Boolean {
        val cv = ContentValues().apply {
            put("type", type); put("amount", amount); put("label", label); put("ts", ts); put("hash", hash)
        }
        return writableDatabase.insertWithOnConflict("txn", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun delete(id: Long) { writableDatabase.delete("txn", "id=?", arrayOf(id.toString())) }

    fun all(): List<Txn> {
        val out = ArrayList<Txn>()
        readableDatabase.rawQuery("SELECT id,type,amount,label,ts FROM txn ORDER BY ts DESC", null).use { c ->
            while (c.moveToNext()) out.add(Txn(c.getLong(0), c.getString(1), c.getDouble(2), c.getString(3) ?: "", c.getLong(4)))
        }
        return out
    }
}
