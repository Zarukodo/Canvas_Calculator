package com.example.calculator

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // === 首次開啟判定 ===
    var isFirstRun: Boolean
        get() = prefs.getBoolean("pref_is_first_run", true)
        set(value) = prefs.edit().putBoolean("pref_is_first_run", value).apply()

    // === 震動設定 ===
    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean("pref_vibration", true)
        set(value) = prefs.edit().putBoolean("pref_vibration", value).apply()

    // === 音效設定 ===
    var isSoundEnabled: Boolean
        get() = prefs.getBoolean("pref_sound", true)
        set(value) = prefs.edit().putBoolean("pref_sound", value).apply()

    // === 字體大小比例 (0.8 ~ 1.5) ===
    var textSizeScale: Float
        get() = prefs.getFloat("pref_text_size_scale", 1.0f)
        set(value) = prefs.edit().putFloat("pref_text_size_scale", value).apply()

    // === 主題模式設定 ===
    // 預設值為 -1 (MODE_NIGHT_FOLLOW_SYSTEM)
    var themeMode: Int
        get() = prefs.getInt("pref_theme_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt("pref_theme_mode", value).apply()

    // === 背景遮罩開關 ===
    var isScrimEnabled: Boolean
        get() = prefs.getBoolean("pref_scrim_enabled", true) // 預設開啟 (true)
        set(value) = prefs.edit().putBoolean("pref_scrim_enabled", value).apply()

    // === 背景遮罩濃度 (0.0 ~ 1.0) ===
    var scrimAlpha: Float
        get() = prefs.getFloat("pref_scrim_alpha", 0.3f) // 預設 0.8 (配合您原本的色碼濃度)
        set(value) = prefs.edit().putFloat("pref_scrim_alpha", value).apply()

    // 鍵盤材質設定 (存取 KeypadStyler.Style)
    var keypadMaterial: KeypadStyler.Style
        get() {
            val name = prefs.getString("pref_keypad_material", KeypadStyler.Style.DEFAULT.name)
            return try {
                KeypadStyler.Style.valueOf(name!!)
            } catch (e: Exception) {
                KeypadStyler.Style.DEFAULT
            }
        }
        set(value) = prefs.edit().putString("pref_keypad_material", value.name).apply()
    // 自訂按鍵顏色 (-1 代表未設定，使用預設)
    var customKeypadColor: Int
        get() = prefs.getInt("pref_custom_keypad_color", -1)
        set(value) = prefs.edit().putInt("pref_custom_keypad_color", value).apply()
    // 進階鍵盤自訂顏色
    var customAdvancedKeypadColor: Int
        get() = prefs.getInt("pref_custom_advanced_color", -1)
        set(value) = prefs.edit().putInt("pref_custom_advanced_color", value).apply()
    // [新增] DEG 模式自訂顏色
    var customDegColor: Int
        get() = prefs.getInt("pref_custom_deg_color", -1)
        set(value) = prefs.edit().putInt("pref_custom_deg_color", value).apply()

    // [新增] RAD 模式自訂顏色
    var customRadColor: Int
        get() = prefs.getInt("pref_custom_rad_color", -1)
        set(value) = prefs.edit().putInt("pref_custom_rad_color", value).apply()
    // [新增] 輸入框遮罩開關
    var isInputScrimEnabled: Boolean
        get() = prefs.getBoolean("pref_input_scrim_enabled", false)
        set(value) = prefs.edit().putBoolean("pref_input_scrim_enabled", value).apply()

    // [新增] 輸入框遮罩透明度 (0.0 ~ 1.0)
    var inputScrimAlpha: Float
        get() = prefs.getFloat("pref_input_scrim_alpha", 0.5f)
        set(value) = prefs.edit().putFloat("pref_input_scrim_alpha", value).apply()

    // [新增] 輸入框遮罩圓角 (dp)
    var inputScrimCorner: Int
        get() = prefs.getInt("pref_input_scrim_corner", 100)
        set(value) = prefs.edit().putInt("pref_input_scrim_corner", value).apply()
    // 儲存「已儲存的顏色列表」
    // 我們把顏色轉成整數，用逗號隔開存成字串，例如 "123456,-654321"
    fun getSavedColors(): MutableList<Int> {
        val savedString = prefs.getString("pref_saved_color_list", "") ?: ""
        if (savedString.isEmpty()) return mutableListOf()

        return savedString.split(",").mapNotNull {
            try { it.toInt() } catch (e: Exception) { null }
        }.toMutableList()
    }

    fun addSavedColor(color: Int) {
        val list = getSavedColors()
        // 避免重複加入
        if (!list.contains(color)) {
            list.add(color) // 加到最後面
            saveColorList(list)
        }
    }

    fun removeSavedColor(color: Int) {
        val list = getSavedColors()
        list.remove(color)
        saveColorList(list)
    }

    private fun saveColorList(list: List<Int>) {
        val combined = list.joinToString(",")
        prefs.edit().putString("pref_saved_color_list", combined).apply()
    }
}