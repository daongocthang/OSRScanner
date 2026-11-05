package com.runit.ocrscanner

import android.content.Context
import com.runit.ocrscanner.model.ScanResult
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

class ScanStore(context: Context) {
    private val prefs = context.getSharedPreferences("ScanStore", Context.MODE_PRIVATE)
    private val rows = mutableListOf<ScanResult>()

    init {
        load()
    }

    private fun load() {
        val json = prefs.getString("rows", "[]") ?: "[]"
        rows.clear()
        runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                rows.add(
                    ScanResult(
                        obj.getString("code"),
                        obj.getLong("timeMillis")
                    )
                )
            }
        }
    }

    private fun save() {
        val array = JSONArray()
        rows.forEach {
            val obj = JSONObject()
            obj.put("code", it.code)
                .put("timeMillis", it.timeMillis)
            array.put(obj)
        }
        prefs.edit {
            putString("rows", array.toString())
        }
    }

    fun add(row: ScanResult): Boolean {
        if (rows.any { it.code == row.code }) return false

        rows.add(row)
        save()
        return true
    }

    fun clear() {
        rows.clear()
        prefs.edit {
            remove("rows")
        }
    }

    fun list(): List<ScanResult> {
        return rows.toList()
    }

    fun count(): Int = rows.count()

}