package com.example.calculator

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ThemeManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
    private val KEY_BG_PATH = "custom_bg_path"
    // 定義固定檔名，確保永遠只佔用一張圖的空間
    private val CUSTOM_BG_FILENAME = "bg_custom.jpg"

    // 取得目前儲存的背景路徑
    fun getBackgroundPath(): String? {
        val path = prefs.getString(KEY_BG_PATH, null)
        return if (path != null && File(path).exists()) path else null
    }

    // 儲存背景圖片 (suspend function 確保在背景執行)
    suspend fun saveBackgroundImage(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 建立目標檔案 (在 app 內部的 theme 資料夾)
                val directory = File(context.filesDir, "theme")
                if (!directory.exists()) directory.mkdirs()

                // 檔名固定為 bg_custom.jpg，這樣每次換圖都會自動覆蓋舊的，節省空間
                val destinationFile = File(directory, CUSTOM_BG_FILENAME)

                // 2. 從 Uri 讀取資料並寫入檔案
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // 3. 儲存路徑到 SharedPreferences
                val path = destinationFile.absolutePath
                prefs.edit().putString(KEY_BG_PATH, path).apply()

                path // 回傳路徑讓 UI 更新
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    }
    // 清除背景 (解決需求 1)
    fun clearBackground() {
        // 1. 清除設定
        prefs.edit().remove(KEY_BG_PATH).apply()

        // 2. 刪除實體檔案 (節省空間)
        try {
            val directory = File(context.filesDir, "theme")
            val file = File(directory, CUSTOM_BG_FILENAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}