package com.android.axion.easteregg.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.axion.easteregg.model.GameStat
import org.json.JSONArray
import org.json.JSONObject

class StatsManager(context: Context) {
    private val prefs = context.getSharedPreferences("axion_stats_final_v12", Context.MODE_PRIVATE)
    var history by mutableStateOf(getHistoryFromPrefs())
        private set

    fun saveStat(stat: GameStat) {
        val currentHistory = getHistoryFromPrefs().toMutableList()
        currentHistory.add(0, stat)
        val limitedHistory = currentHistory.take(10)
        val array = JSONArray()
        limitedHistory.forEach {
            val obj = JSONObject()
            obj.put("ts", it.timestamp)
            obj.put("res", it.result)
            obj.put("time", it.time)
            obj.put("acc", it.accuracy)
            array.put(obj)
        }
        prefs.edit().putString("history", array.toString()).apply()
        history = limitedHistory
    }

    private fun getHistoryFromPrefs(): List<GameStat> {
        val json = prefs.getString("history", null) ?: return emptyList()
        val list = mutableListOf<GameStat>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(GameStat(
                obj.getLong("ts"),
                obj.getString("res"),
                obj.getString("time"),
                obj.optInt("acc", 0)
            ))
        }
        return list
    }
}
