package com.example.calculator

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val expression: String, // 算式
    val result: String,     // 結果
    val timestamp: Long = System.currentTimeMillis() // 建立當下的時間戳記 (毫秒)
) {
    // 專門給 UI 呼叫的時間格式化函式
    // 需求格式：年/月/日/24時制 (例如：2025/10/20 14:30)
    fun getFormattedTime(): String {
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}