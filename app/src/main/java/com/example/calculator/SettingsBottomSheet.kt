package com.example.calculator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsBottomSheet : BottomSheetDialogFragment() {

    var onTextSizeChanged: ((Float) -> Unit)? = null
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 Manager
        settingsManager = SettingsManager(requireContext())

        setupLanguage(view)
        setupUXPreferences(view)
    }

    private fun setupLanguage(view: View) {
        val langGroup = view.findViewById<RadioGroup>(R.id.settings_lang_group)
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (!currentLocales.isEmpty) currentLocales.get(0)?.toLanguageTag() ?: "en" else java.util.Locale.getDefault().toLanguageTag()

        if (currentTag.contains("zh") && currentTag.contains("TW")) {
            langGroup.check(R.id.rb_lang_zh_tw)
        } else {
            langGroup.check(R.id.rb_lang_en)
        }

        langGroup.setOnCheckedChangeListener { _, checkedId ->
            val newLocale = when (checkedId) {
                R.id.rb_lang_zh_tw -> LocaleListCompat.forLanguageTags("zh-TW")
                else -> LocaleListCompat.forLanguageTags("en")
            }
            AppCompatDelegate.setApplicationLocales(newLocale)
            dismiss()
        }
    }

    private fun setupUXPreferences(view: View) {
        val switchVib = view.findViewById<SwitchMaterial>(R.id.switch_vibration)
        val switchSound = view.findViewById<SwitchMaterial>(R.id.switch_sound)
        val sliderSize = view.findViewById<Slider>(R.id.slider_text_size)
        val tvSizeValue = view.findViewById<TextView>(R.id.tv_text_size_value)

        // 1. 讀取並顯示目前的設定值
        switchVib.isChecked = settingsManager.isVibrationEnabled
        switchSound.isChecked = settingsManager.isSoundEnabled
        val currentScale = settingsManager.textSizeScale
        sliderSize.value = currentScale
        tvSizeValue.text = "${String.format("%.1f", currentScale)}x"

        // 2. 設定監聽器 - 震動
        switchVib.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isVibrationEnabled = isChecked
        }

        // 3. 設定監聽器 - 音效
        switchSound.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isSoundEnabled = isChecked
        }

        // 4. 設定監聽器 - 字體大小 (即時預覽)
        sliderSize.addOnChangeListener { _, value, _ ->
            // 儲存設定
            settingsManager.textSizeScale = value
            // 更新 UI 數字
            tvSizeValue.text = "${String.format("%.1f", value)}x"
            // ★ 通知 MainActivity 改變字體
            onTextSizeChanged?.invoke(value)
        }
    }
}