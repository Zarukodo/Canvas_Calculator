package com.example.calculator

import android.annotation.SuppressLint
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build

class QuickCalcTileService : TileService() {

    // 當 Tile 變為可見或被請求更新時呼叫
    override fun onStartListening() {
        super.onStartListening()

        // 從 SharedPreferences 讀取 App 是否正在執行的狀態
        val prefs = getSharedPreferences("app_state", Context.MODE_PRIVATE)
        val isAppRunning = prefs.getBoolean("is_running", false)

        qsTile?.let { tile ->
            // 根據讀取到的狀態設定圖示
            tile.state = if (isAppRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            // 套用變更
            tile.updateTile()
        }
    }

    // 當使用者點擊圖示時呼叫
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /**
         * 請求系統更新 Quick Settings Tile
         * @param context Context 物件
         * @param isRunning App 是否正在運行
         */
        fun updateTile(context: Context, isRunning: Boolean) {
            // 將 App 的運行狀態存入 SharedPreferences
            val prefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_running", isRunning).apply()

            // 建立指向我們 TileService 的 ComponentName
            val tileService = ComponentName(context, QuickCalcTileService::class.java)

            // 請求系統去更新 Tile。這會觸發 onStartListening()
            requestListeningState(context, tileService)
        }
    }
}
