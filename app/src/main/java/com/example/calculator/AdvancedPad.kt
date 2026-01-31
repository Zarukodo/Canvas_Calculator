package com.example.calculator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat

class AdvancedPad : Fragment() {

    // 狀態變數
    private var isInverse = false
    private var isDegree = true // 預設為角度制 (DEG)

    // 為了方便，先把按鈕宣告在外面
    private lateinit var btnMode: Button
    private lateinit var btnInv: Button
    private lateinit var btnSin: Button
    private lateinit var btnCos: Button
    private lateinit var btnTan: Button
    private lateinit var btnLn: Button
    private lateinit var btnLog: Button
    private lateinit var btnSqrt: Button

    // 其他不變的按鈕
    private lateinit var btnFact: Button
    private lateinit var btnPi: Button
    private lateinit var btnE: Button
    private lateinit var btnPow: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.advanced_pad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 綁定按鈕
        bindViews(view)
        // 設定一般按鈕的點擊事件 (不會變身的)
        setupStaticButtons()
        // 設定會變身的按鈕 (sin, cos...)
        updateVariableButtons()
        // 設定 Inv 切換功能
        btnInv.setOnClickListener {
            isInverse = !isInverse // 切換 True/False
            updateVariableButtons() // 更新所有按鈕的文字與功能
            (activity as? MainActivity)?.setInverseMode(isInverse)
        }

        // 設定 Mode (Deg/Rad) 切換功能
        btnMode.setOnClickListener {
            isDegree = !isDegree
            btnMode.text = if (isDegree) "DEG" else "RAD"
            // 告訴 MainActivity 我們現在用什麼單位
            // 我們可以寫一個簡單的方法在 MainActivity 來接收這個設定
            (activity as? MainActivity)?.setDegreeMode(isDegree)
        }
    }

    private fun bindViews(view: View) {
        btnMode = view.findViewById(R.id.btn_mode)
        btnInv = view.findViewById(R.id.btn_inv)
        btnSin = view.findViewById(R.id.btn_sin)
        btnCos = view.findViewById(R.id.btn_cos)
        btnTan = view.findViewById(R.id.btn_tan)
        btnLn = view.findViewById(R.id.btn_ln)
        btnLog = view.findViewById(R.id.btn_log)
        btnSqrt = view.findViewById(R.id.btn_sqrt)
        btnFact = view.findViewById(R.id.btn_fact)
        btnPi = view.findViewById(R.id.btn_pi)
        btnE = view.findViewById(R.id.btn_e)
        btnPow = view.findViewById(R.id.btn_pow)
    }

    private fun setupStaticButtons() {
        val staticMap = mapOf(
            btnFact to "!",
            btnPi to "π",
            btnE to "e",
            btnPow to "^"
        )
        for ((btn, value) in staticMap) {
            btn.setOnClickListener { (activity as? MainActivity)?.addInput(value) }
        }
    }

    // 核心：根據 isInverse 狀態，重新定義按鈕的「文字」和「輸入值」
    private fun updateVariableButtons() {
        if (isInverse) {
            // === 反函數模式 (黃金狀態) ===
            setBtn(btnSin, "sin⁻¹", "sin⁻¹(")
            setBtn(btnCos, "cos⁻¹", "cos⁻¹(")
            setBtn(btnTan, "tan⁻¹", "tan⁻¹(")
            setBtn(btnLn,  "eˣ",    "e^")     // ln 的反函數是 e 的 x 次方
            setBtn(btnLog, "10ˣ",   "10^")    // log 的反函數是 10 的 x 次方
            setBtn(btnSqrt, "x²",   "^2")     // 根號的反函數是平方
        } else {
            // === 正常模式 ===
            setBtn(btnSin, "sin", "sin(")
            setBtn(btnCos, "cos", "cos(")
            setBtn(btnTan, "tan", "tan(")
            setBtn(btnLn,  "ln",  "ln(")
            setBtn(btnLog, "log", "log10(")
            setBtn(btnSqrt, "√",  "√(")
        }
    }

    // 輔助函式：快速設定按鈕文字與點擊輸入
    private fun setBtn(btn: Button, display: String, input: String) {
        btn.text = display
        // 先移除舊的監聽器 (避免重複綁定)
        btn.setOnClickListener(null)
        // 綁定新的輸入
        btn.setOnClickListener {
            (activity as? MainActivity)?.addInput(input)
        }
    }

    // (選做) 改變 Inv 按鈕外觀，讓使用者知道現在是鎖定狀態
    private fun updateInvButtonColor() {
        // 取得 Context，如果沒有就直接 return (避免 crash)
        val context = context ?: return

        if (isInverse) {
            // === 變身：設定成醒目顏色 (金黃色) ===
            val activeColor = ContextCompat.getColor(context, R.color.btn_inv_active_bg)
            val activeTextColor = ContextCompat.getColor(context, R.color.btn_inv_active_text)

            // 使用 backgroundTintList 來保持按鈕形狀
            btnInv.backgroundTintList = ColorStateList.valueOf(activeColor)
            btnInv.setTextColor(activeTextColor)
        } else {
            // === 復原：設定回預設顏色 (淺灰色) ===
            // 這時候就派上用場了！我們直接讀取 colors.xml 裡的預設值
            val defaultColor = ContextCompat.getColor(context, R.color.btn_advanced_default_bg)
            val defaultTextColor = ContextCompat.getColor(context, R.color.btn_advanced_default_text)

            btnInv.backgroundTintList = ColorStateList.valueOf(defaultColor)
            btnInv.setTextColor(defaultTextColor)
        }
    }
    // 根據模式改變 DEG/RAD 按鈕的顏色

}