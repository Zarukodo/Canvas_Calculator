package com.example.calculator

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class ThemeBottomSheet : BottomSheetDialogFragment() {

    var onChangeBackgroundClick: (() -> Unit)? = null
    var onResetBackgroundClick: (() -> Unit)? = null
    var onScrimStateChanged: (() -> Unit)? = null
    // 宣告 SettingsManager
    var onKeypadColorChanged: ((Int) -> Unit)? = null
    //顯示框遮罩
    var onInputScrimChanged: (() -> Unit)? = null
    private lateinit var settingsManager: SettingsManager
    // 材質改變的回調函式
    // 翻譯：這是一個變數，它裝著一個函式，這個函式接收 Style 參數，不回傳任何東西 (Unit)
    var onMaterialChanged: ((KeypadStyler.Style) -> Unit)? = null
    private lateinit var adapterBasic: ColorPaletteAdapter
    private lateinit var adapterAdvanced: ColorPaletteAdapter
    private lateinit var adapterDeg: ColorPaletteAdapter
    private lateinit var adapterRad: ColorPaletteAdapter
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_theme, container, false)
    }

    @SuppressLint("ResourceType")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsManager = SettingsManager(requireContext())

        // 1. 綁定按鈕 (更換/重置)
        view.findViewById<Button>(R.id.btn_change_bg)?.setOnClickListener {
            dismiss()
            onChangeBackgroundClick?.invoke()
        }

        view.findViewById<Button>(R.id.btn_reset_bg)?.setOnClickListener {
            dismiss()
            onResetBackgroundClick?.invoke()
        }

        // 2. 主題切換 (RadioGroup)
        val rgTheme = view.findViewById<RadioGroup>(R.id.rg_theme)
        if (rgTheme != null) {
            when (settingsManager.themeMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> rgTheme.check(R.id.rb_light)
                AppCompatDelegate.MODE_NIGHT_YES -> rgTheme.check(R.id.rb_dark)
                else -> rgTheme.check(R.id.rb_system)
            }
            rgTheme.setOnCheckedChangeListener { _, checkedId ->
                val mode = when (checkedId) {
                    R.id.rb_light -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.rb_dark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                settingsManager.themeMode = mode
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
        // 3. 遮罩設定 (使用 SwitchMaterial + SeekBar)
        val switchScrim = view.findViewById<SwitchMaterial>(R.id.switch_scrim)
        val seekBarAlpha = view.findViewById<SeekBar>(R.id.slider_alpha)
        val tvAlphaTitle = view.findViewById<TextView>(R.id.tv_alpha_title)
        if (switchScrim != null && seekBarAlpha != null) {
            // A. 初始化狀態
            switchScrim.isChecked = settingsManager.isScrimEnabled

            // 將 0.8f 轉為 80
            val currentProgress = (settingsManager.scrimAlpha * 100).toInt()
            seekBarAlpha.progress = currentProgress

            // 根據開關決定滑桿是否可用
            seekBarAlpha.isEnabled = settingsManager.isScrimEnabled
            tvAlphaTitle?.alpha = if (settingsManager.isScrimEnabled) 1.0f else 0.5f

            // B. 監聽開關
            switchScrim.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.isScrimEnabled = isChecked
                seekBarAlpha.isEnabled = isChecked
                tvAlphaTitle?.alpha = if (isChecked) 1.0f else 0.5f

                // 通知 MainActivity 更新畫面
                onScrimStateChanged?.invoke()
            }

            // C. 監聽滑桿
            seekBarAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        // 將 80 轉回 0.8f
                        val alpha = progress / 100f
                        settingsManager.scrimAlpha = alpha
                        onScrimStateChanged?.invoke()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        // === 輸入框背板設定 ===
        val switchInputScrim = view.findViewById<SwitchMaterial>(R.id.switch_input_scrim)
        val sliderInputAlpha = view.findViewById<SeekBar>(R.id.slider_input_alpha)
        val sliderInputCorner = view.findViewById<SeekBar>(R.id.slider_input_corner)

        if (switchInputScrim != null) {
            // 初始化
            switchInputScrim.isChecked = settingsManager.isInputScrimEnabled
            sliderInputAlpha.progress = (settingsManager.inputScrimAlpha * 100).toInt()
            sliderInputCorner.progress = settingsManager.inputScrimCorner

            val isEnabled = settingsManager.isInputScrimEnabled
            sliderInputAlpha.isEnabled = isEnabled
            sliderInputCorner.isEnabled = isEnabled

            // 開關監聽
            switchInputScrim.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.isInputScrimEnabled = isChecked
                sliderInputAlpha.isEnabled = isChecked
                sliderInputCorner.isEnabled = isChecked
                onInputScrimChanged?.invoke()
            }

            // 透明度滑桿監聽
            sliderInputAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                    settingsManager.inputScrimAlpha = progress / 100f
                    onInputScrimChanged?.invoke()
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })

            // 圓角滑桿監聽
            sliderInputCorner.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                    settingsManager.inputScrimCorner = progress
                    onInputScrimChanged?.invoke()
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        // ================= 鍵盤風格切換邏輯 (改為 RadioGroup) =================
        val rgKeypadStyle = view.findViewById<RadioGroup>(R.id.rg_keypad_style)

        if (rgKeypadStyle != null) {
            // A. 初始化：讀取設定，決定哪個選項要被勾選
            val currentStyle = settingsManager.keypadMaterial
            when (currentStyle) {
                KeypadStyler.Style.FROSTED -> rgKeypadStyle.check(R.id.rb_style_frosted)
                else -> rgKeypadStyle.check(R.id.rb_style_default)
            }

            // B. 監聽點擊：當使用者切換選項時
            rgKeypadStyle.setOnCheckedChangeListener { _, checkedId ->
                val selectedStyle = when (checkedId) {
                    R.id.rb_style_frosted -> KeypadStyler.Style.FROSTED
                    else -> KeypadStyler.Style.DEFAULT
                }

                // 1. 存檔
                settingsManager.keypadMaterial = selectedStyle

                // 2. 觸發回調，通知 MainActivity 換裝 (解決切換無感的問題)
                onMaterialChanged?.invoke(selectedStyle)
            }
        }
        // === 設定 RecyclerView ===
        // === 1. 基礎鍵盤 RecyclerView (邏輯跟之前一樣，只是改名 adapterBasic) ===
        val rvBasic = view.findViewById<RecyclerView>(R.id.rv_color_palette)
        rvBasic.layoutManager = GridLayoutManager(context, 5)
        adapterBasic = ColorPaletteAdapter(
            colors = settingsManager.getSavedColors(), // 共用同一個色票庫? 還是要分開存? 通常共用比較方便
            selectedColor = settingsManager.customKeypadColor,
            onColorClick = { color ->
                settingsManager.customKeypadColor = color
                adapterBasic.updateData(settingsManager.getSavedColors(), color)
                notifyColorChange()
            },
            onAddClick = { showColorPicker(Target.BASIC) },

            onDeleteClick = { color ->
                settingsManager.removeSavedColor(color)
                refreshAllAdapters()
            }
        )
        rvBasic.adapter = adapterBasic

        // === 2. 進階鍵盤 RecyclerView ===
        val rvAdvanced = view.findViewById<RecyclerView>(R.id.rv_color_palette_advanced)
        rvAdvanced.layoutManager = GridLayoutManager(context, 5)
        adapterAdvanced = ColorPaletteAdapter(
            colors = settingsManager.getSavedColors(),
            selectedColor = settingsManager.customAdvancedKeypadColor,
            onColorClick = { color ->
                settingsManager.customAdvancedKeypadColor = color
                adapterAdvanced.updateData(settingsManager.getSavedColors(), color)
                notifyColorChange()
            },
            onAddClick = { showColorPicker(Target.ADVANCED) },

            onDeleteClick = { color ->
                settingsManager.removeSavedColor(color)
                refreshAllAdapters()
            }
        )
        rvAdvanced.adapter = adapterAdvanced
        // === 3. DEG 顏色 ===
        val rvDeg = view.findViewById<RecyclerView>(R.id.rv_color_deg)
        rvDeg.layoutManager = GridLayoutManager(context, 5)
        adapterDeg = ColorPaletteAdapter(
            colors = settingsManager.getSavedColors(),
            selectedColor = settingsManager.customDegColor,
            onColorClick = { color ->
                settingsManager.customDegColor = color
                adapterDeg.updateData(settingsManager.getSavedColors(), color)
                notifyColorChange()
            },
            onAddClick = { showColorPicker(Target.DEG) },
            onDeleteClick = { color ->
                settingsManager.removeSavedColor(color)
                refreshAllAdapters()
            }
        )
        rvDeg.adapter = adapterDeg

        // === 4. RAD 顏色 ===
        val rvRad = view.findViewById<RecyclerView>(R.id.rv_color_rad)
        rvRad.layoutManager = GridLayoutManager(context, 5)
        adapterRad = ColorPaletteAdapter(
            colors = settingsManager.getSavedColors(),
            selectedColor = settingsManager.customRadColor,
            onColorClick = { color ->
                settingsManager.customRadColor = color
                adapterRad.updateData(settingsManager.getSavedColors(), color)
                notifyColorChange()
            },
            onAddClick = { showColorPicker(Target.RAD) },
            onDeleteClick = { color ->
                settingsManager.removeSavedColor(color)
                refreshAllAdapters()
            }
        )
        rvRad.adapter = adapterRad
        // 重置顏色按鈕：必須同時重置兩個顏色與列表
        view.findViewById<Button>(R.id.btn_reset_color)?.setOnClickListener {
            settingsManager.customKeypadColor = -1
            settingsManager.customAdvancedKeypadColor = -1
            settingsManager.customDegColor = -1 // [新增]
            settingsManager.customRadColor = -1 // [新增]

            refreshAllAdapters()
            notifyColorChange()
            android.widget.Toast.makeText(context, "已恢復預設顏色", android.widget.Toast.LENGTH_SHORT).show()
        }
        // 背景遮罩說明 (使用 getString 讀取多語言資源)
        setupHelpButton(
            view,
            R.id.btn_full_scrim_mask_help,
            getString(R.string.help_title_scrim),
            getString(R.string.help_msg_scrim)
        )

        // 輸入框遮罩說明
        setupHelpButton(
            view,
            R.id.btn_input_mask_help,
            getString(R.string.help_title_input_scrim),
            getString(R.string.help_msg_input_scrim)
        )

        // 鍵盤風格說明
        setupHelpButton(
            view,
            R.id.btn_keyboard_style_help,
            getString(R.string.help_title_keyboard_style),
            getString(R.string.help_msg_keyboard_style)
        )
    }
    // 這是通用工具人，給它按鈕ID、標題、內容，它就幫你做好設定
    private fun setupHelpButton(rootView: View, buttonId: Int, title: String, message: String) {
        rootView.findViewById<View>(buttonId)?.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(android.R.string.ok), null) // 使用系統內建的 "OK"
                .show()
        }
    }
    private fun refreshAllAdapters() {
        val colors = settingsManager.getSavedColors()
        adapterBasic.updateData(colors, settingsManager.customKeypadColor)
        adapterAdvanced.updateData(colors, settingsManager.customAdvancedKeypadColor)
        adapterDeg.updateData(colors, settingsManager.customDegColor)
        adapterRad.updateData(colors, settingsManager.customRadColor)
    }
    private fun notifyColorChange() {
        // 這裡只要通知 MainActivity 刷新，它會自己去 SettingsManager 抓兩個顏色
        onKeypadColorChanged?.invoke(0) // 參數隨便傳，反正 MainActivity 現在不看這個參數了
    }
    enum class Target { BASIC, ADVANCED, DEG, RAD }
    // === 顯示專業選色器 (Skydoves) ===
    private fun showColorPicker(target: Target) {
        try {
            ColorPickerDialog.Builder(requireContext())
                .setTitle("自訂顏色")
                .setPositiveButton("新增", ColorEnvelopeListener { envelope, _ ->
                    val color = envelope.color
                    settingsManager.addSavedColor(color)

                    // 根據 Target 決定套用對象
                    when (target) {
                        Target.BASIC -> settingsManager.customKeypadColor = color
                        Target.ADVANCED -> settingsManager.customAdvancedKeypadColor = color
                        Target.DEG -> settingsManager.customDegColor = color
                        Target.RAD -> settingsManager.customRadColor = color
                    }

                    refreshAllAdapters()
                    notifyColorChange()
                })
                .setNegativeButton("取消") { dialogInterface, _ -> dialogInterface.dismiss() }
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(12)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}