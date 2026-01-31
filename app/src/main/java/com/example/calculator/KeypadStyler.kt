package com.example.calculator

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import eightbitlab.com.blurview.BlurView

object KeypadStyler {

    enum class Style { DEFAULT, FROSTED }

    private const val COLOR_ORANGE = "#FF9800" // mode_deg 預設色
    private const val COLOR_BLUE = "#00BCD4"   // mode_rad 預設色
    private const val COLOR_GOLD = "#FDD835"   // mode_inv (Active)

    fun applyStyle(
        context: Context,
        style: Style,
        blurView: BlurView,
        basicPad: ViewGroup,
        advancedPad: ViewGroup?,
        isDegree: Boolean = true,
        isInverse: Boolean = false,
        customBasicColor: Int = -1,
        customAdvancedColor: Int = -1,
        customDegColor: Int = -1,
        customRadColor: Int = -1
    ) {
        val allButtons = mutableListOf<View>()
        collectButtons(basicPad, allButtons)
        if (advancedPad != null && advancedPad.visibility == View.VISIBLE) {
            collectButtons(advancedPad, allButtons)
        }

        if (style == Style.FROSTED) {
            blurView.setBlurEnabled(true)
            blurView.setOverlayColor(Color.TRANSPARENT)
            if (blurView is MaskedBlurView) blurView.setMaskViews(allButtons)
        } else {
            blurView.setBlurEnabled(false)
            if (blurView is MaskedBlurView) blurView.setMaskViews(emptyList())
        }

        recursiveApply(context, basicPad, style, isAdvanced = false, isDegree, isInverse,
            customBasicColor, customAdvancedColor, customDegColor, customRadColor)

        if (advancedPad != null) {
            recursiveApply(context, advancedPad, style, isAdvanced = true, isDegree, isInverse,
                customBasicColor, customAdvancedColor, customDegColor, customRadColor)
        }
    }

    private fun collectButtons(view: View, list: MutableList<View>) {
        if (view is ViewGroup) {
            for (child in view.children) collectButtons(child, list)
        } else if (view is Button || view is ImageButton) {
            list.add(view)
        }
    }

    private fun recursiveApply(
        context: Context, view: View, style: Style,
        isAdvanced: Boolean, isDegree: Boolean, isInverse: Boolean,
        customBasicColor: Int, customAdvancedColor: Int,
        customDegColor: Int, customRadColor: Int
    ) {
        // 1. 判斷深淺模式 & 決定文字顏色
        val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 讀取 colors.xml 裡的 text_default (黑色)
        // 您的 colors.xml 裡有定義 text_default 為 #000000
        val defaultTextColor = ContextCompat.getColor(context, R.color.text_default)

        // 邏輯：深色模式 -> 白字；淺色模式 -> 黑色 (text_default)
        val textColor = if (isNightMode) Color.WHITE else defaultTextColor

        if (view is ViewGroup) {
            for (child in view.children) {
                recursiveApply(context, child, style, isAdvanced, isDegree, isInverse,
                    customBasicColor, customAdvancedColor, customDegColor, customRadColor)
            }
        }
        else if (view is Button) {
            val isModeButton = (view.id == R.id.btn_mode)
            val isInvButton = (view.id == R.id.btn_inv)

            // 決定目標染色 (Tint)
            var targetTint: Int? = null

            if (isModeButton) {
                val degColor = if (customDegColor != -1) customDegColor else Color.parseColor(COLOR_ORANGE)
                val radColor = if (customRadColor != -1) customRadColor else Color.parseColor(COLOR_BLUE)
                targetTint = if (isDegree) degColor else radColor
            } else if (isInvButton && isInverse) {
                targetTint = Color.parseColor(COLOR_GOLD)
            } else {
                val userColor = if (isAdvanced) customAdvancedColor else customBasicColor
                if (userColor != -1) {
                    targetTint = userColor
                }
            }

            fun setIconTint(color: Int) {
                if (view is MaterialButton) {
                    view.iconTint = ColorStateList.valueOf(color)
                }
            }

            // 開始應用樣式
            when (style) {
                Style.FROSTED -> {
                    // === 玻璃模式 ===
                    view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                    view.setBackgroundResource(R.drawable.bg_key_frosted)

                    if (targetTint != null) {
                        view.backgroundTintList = ColorStateList.valueOf(targetTint)
                        // [修改] 不再強制白色，而是跟隨系統 (淺色模式變黑，深色模式變白)
                        view.setTextColor(textColor)
                        setIconTint(textColor)
                    } else {
                        view.backgroundTintList = null
                        view.setTextColor(textColor)
                        setIconTint(textColor)
                    }
                }

                Style.DEFAULT -> {
                    // === 預設模式 ===
                    if (targetTint != null) {
                        view.setBackgroundResource(R.drawable.bg_key_base)
                        view.backgroundTintList = ColorStateList.valueOf(targetTint)
                        // (淺色模式變黑，深色模式變白)
                        view.setTextColor(textColor)
                        setIconTint(textColor)
                    } else {
                        if (isAdvanced) {
                            view.setBackgroundResource(R.drawable.bg_key_advanced_default)
                        } else {
                            view.setBackgroundResource(R.drawable.bg_key_default)
                        }
                        view.backgroundTintList = null
                        view.setTextColor(textColor)
                        setIconTint(textColor)
                    }
                }
            }
        } else if (view is ImageButton) {
            // 圖示按鈕 (Menu/History)
            view.setColorFilter(textColor)
        }
    }
}