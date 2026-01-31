package com.example.calculator

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HistoryManager(context: Context) {

    // 1. 設定檔案名稱與 Key
    private val prefs = context.getSharedPreferences("calc_history_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_HISTORY = "history_list"

    // 2. 儲存紀錄 (List -> JSON String)
    fun saveHistory(historyList: List<HistoryItem>) {
        val jsonString = gson.toJson(historyList)
        prefs.edit()
            .putString(KEY_HISTORY, jsonString)
            .apply() // 非同步存檔，不會卡住介面
    }

    // 3. 讀取紀錄 (JSON String -> List)
    fun getHistory(): List<HistoryItem> {
        val jsonString = prefs.getString(KEY_HISTORY, null)

        return if (jsonString != null) {
            // 如果有存檔，把它轉回 List<HistoryItem>
            // 這行稍微複雜一點，是因為 Gson 需要知道 List 裡面裝的是什麼型別
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            // 如果沒存檔 (第一次開 APP)，回傳空清單
            emptyList()
        }
    }

    // 4. 清空紀錄
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}