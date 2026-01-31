package com.example.calculator

import android.animation.TimeInterpolator
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.Fragment
import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.operator.Operator
import net.objecthunter.exp4j.function.Function
import kotlin.math.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.ViewGroup
import androidx.core.view.WindowInsetsControllerCompat
import android.view.HapticFeedbackConstants
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import android.animation.ValueAnimator


class MainActivity : AppCompatActivity() {
    //承諾系統：我現在還沒給它值，但我保證在用它之前會給，請先讓我通過。
    lateinit var inputEditText: EditText
    lateinit var outputTextView: TextView

    // 定義會受到角度模式影響的關鍵字，只要輸入框出現這些字，顏色就要跟著模式變
    private lateinit var blurKeypad: eightbitlab.com.blurview.BlurView
    private lateinit var rootLayout: android.view.ViewGroup
    // 宣告全域手勢偵測器
    private lateinit var swipeDetector: android.view.GestureDetector
    private val trigKeywords = listOf(
        "sin", "cos", "tan",
        "asin", "acos", "atan"
    )
    private lateinit var settingsManager: SettingsManager
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyManager: HistoryManager
    private lateinit var rvHistory: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnHistory: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var btnMenu: ImageButton

    // 用來暫存記憶體中的歷史紀錄
    private val historyData = mutableListOf<HistoryItem>()

    //記錄現在是角度還是弧度，預設為 true (角度 DEG)
    // 把手元件
    private lateinit var viewHandle: View

    // 記錄上一次調整的高度
    private var lastHistoryHeight = 0

    // 記錄螢幕高度 (用來計算磁吸上限)
    private var screenHeight = 0
    var isDegree = true
    var isInverse = false

    // 定義基礎字體大小 (sp)，避免重複縮放導致失真
    private val BASE_TEXT_SIZE_INPUT = 39f
    private val BASE_TEXT_SIZE_OUTPUT = 26f
    private lateinit var themeManager: ThemeManager
    private lateinit var ivGlobalBackground: android.widget.ImageView
    private lateinit var viewScrim: View // 定義遮罩
    private lateinit var viewInputScrim: View //

    // 註冊圖片裁切合約 (這是一個變數，定義了選圖後的行為)
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            if (uriContent != null) {
                lifecycleScope.launch {
                    // 加入提示：開始處理
                    // android.widget.Toast.makeText(this@MainActivity, "正在設定背景...", android.widget.Toast.LENGTH_SHORT).show()

                    val savedPath = themeManager.saveBackgroundImage(uriContent)
                    if (savedPath != null) {
                        applyBackground(savedPath)
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "背景設定成功！",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "背景儲存失敗",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else {
            // 如果失敗，印出原因
            val error = result.error
            error?.printStackTrace()
            android.widget.Toast.makeText(
                this,
                "裁切取消或失敗: ${error?.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Callback: 當使用者選好圖片後會執行這裡
            if (uri != null) {
                // 使用協程在背景存檔
                lifecycleScope.launch {
                    val savedPath = themeManager.saveBackgroundImage(uri)
                    if (savedPath != null) {
                        // 存檔成功，更新 UI
                        applyBackground(savedPath)
                    }
                }
            } else {
                // 使用者取消選取
            }
        }

    //給 Fragment 呼叫的方法，用來切換模式
    fun setDegreeMode(degree: Boolean) {
        isDegree = degree
        checkAndUpdateColor()
        val currentText = inputEditText.text.toString()
        if (currentText.isNotEmpty() && currentText != "Error" && !isOperator(currentText.last())) {
            calculateResult(currentText)
        }
        val basicPad = findViewById<ViewGroup>(R.id.basicPad)
        val advancedPad = findViewById<ViewGroup>(R.id.advancedPad)
        if (advancedPad.visibility == View.VISIBLE) {
            val currentStyle = settingsManager.keypadMaterial

            // [修改] 讀取所有顏色並傳入
            val basicColor = settingsManager.customKeypadColor
            val advColor = settingsManager.customAdvancedKeypadColor
            val degColor = settingsManager.customDegColor
            val radColor = settingsManager.customRadColor

            advancedPad.post {
                KeypadStyler.applyStyle(
                    this, currentStyle, blurKeypad, basicPad, advancedPad,
                    isDegree = degree, isInverse = isInverse,
                    customBasicColor = basicColor, customAdvancedColor = advColor,
                    customDegColor = degColor, customRadColor = radColor
                )
            }
        }
    }

    // 檢查內容並更新顏色的核心大腦
    private fun checkAndUpdateColor() {
        val currentText = inputEditText.text.toString()
        // 1. 檢查算式裡有沒有出現三角函數關鍵字？
        val hasTrig = trigKeywords.any { currentText.contains(it) }

        val color = if (hasTrig) {
            // A. 有用到三角函數 -> 根據模式與設定決定顏色
            if (isDegree) {
                if (settingsManager.customDegColor != -1) settingsManager.customDegColor
                else ContextCompat.getColor(this, R.color.mode_deg)
            } else {
                if (settingsManager.customRadColor != -1) settingsManager.customRadColor
                else ContextCompat.getColor(this, R.color.mode_rad)
            }
        } else {
            // B. 只是普通運算 -> 顯示預設顏色
            ContextCompat.getColor(this, R.color.text_default)
        }

        // 2. 設定文字顏色
        inputEditText.setTextColor(color)
        // [修改] 判斷深色模式，決定是否開啟天使光暈
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isNightMode) {
            // 深色模式：開啟光暈 (半徑 35f)
            inputEditText.setShadowLayer(13f, 0f, 0f, color)
        } else {
            // 淺色模式：關閉光暈 (半徑 0f)
            inputEditText.setShadowLayer(0f, 0f, 0f, 0)
        }
    }

    // 切換顏色模式的方法 (給 AdvancedPad 呼叫)
    fun setInputColor(isDegree: Boolean) {
        // 更新狀態
        this.isDegree = isDegree
        // 決定顏色
        checkAndUpdateColor()
    }

    // 工具一：判斷是不是運算符號 (防止使用者打 "10+" 就叫程式算，會報錯)
    fun isOperator(char: Char?): Boolean {
        return char == '+' || char == '-' || char == '×' || char == '÷' || char == '.'
    }

    // 工具二：計算的核心邏輯 (請數學專家 exp4j 出馬)
    // 工具二：計算的核心邏輯 (請數學專家 exp4j 出馬)
    // 工具二：計算的核心邏輯 (請數學專家 exp4j 出馬)
    // 工具二：計算的核心邏輯
    fun calculateResult(expression: String) {
        try {
            // 防止不合規的小數點格式
            if (expression.matches(".*\\d+\\.\\d*\\..*".toRegex()) || expression.contains("..")) {
                outputTextView.text = getString(R.string.text_error)
                return
            }

            // 1. 基礎符號替換
            var fixedExpression = expression
                .replace("×", "*")
                .replace("÷", "/")
                .replace("√", "sqrt")
                .replace("π", "pi")
                .replace("ln(", "log(")
                .replace("loge(", "log(")
                .replace("sin⁻¹(", "asin(")
                .replace("cos⁻¹(", "acos(")
                .replace("tan⁻¹(", "atan(")

            // 處理 logN(...) 格式
            val regexLog = Regex("log(\\d+(\\.\\d+)?)\\(")
            fixedExpression = regexLog.replace(fixedExpression) { matchResult ->
                val base = matchResult.groupValues[1]
                "logB($base,"
            }

            // 2. [核心] 處理連續階乘 (3!! -> (3!)!, 3!!! -> ((3!)!)!)
            // 使用迴圈不斷將 "!!" 替換為 "(運算元!)!"
            while (fixedExpression.contains("!!")) {
                val index = fixedExpression.indexOf("!!")

                // 向前搜尋運算元
                var ptr = index - 1
                var parenDepth = 0

                while (ptr >= 0) {
                    val c = fixedExpression[ptr]

                    if (c == ')') {
                        parenDepth++
                    } else if (c == '(') {
                        if (parenDepth > 0) {
                            parenDepth--
                            if (parenDepth == 0) {
                                // 括號結束，檢查前面是否還有函數名稱 (例如 sin(...))
                                var funcPtr = ptr - 1
                                while (funcPtr >= 0 && fixedExpression[funcPtr].isLetter()) {
                                    funcPtr--
                                }
                                ptr = funcPtr + 1
                            }
                        } else {
                            break // 括號不平衡
                        }
                    } else if (parenDepth == 0) {
                        // 遇到運算符號 (+, -, *, /, ^, %) 則停止
                        // 注意：【不】包含 '!'，因為 (3!)!! 需要抓取 (3!) 當作運算元
                        if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%') {
                            break
                        }

                        // 隱式乘法邊界檢查 (例如 3sin(x)!!，應只抓取 sin(x))
                        if (ptr + 1 < index) {
                            val right = fixedExpression[ptr+1]
                            // 如果現在是數字，右邊是字母或括號，代表這裡是乘法邊界
                            if (c.isDigit() && (right.isLetter() || right == '(')) {
                                break
                            }
                        }
                    }
                    ptr--
                }

                val start = ptr + 1
                val operand = fixedExpression.substring(start, index)

                // 替換邏輯：把 [運算元]!! 變成 ([運算元]!)!
                fixedExpression = fixedExpression.substring(0, start) +
                        "(" + operand + "!)!" +
                        fixedExpression.substring(index + 2)
            }

            // 3. 處理階乘後的隱式乘法
            // 規則：當 "!" 後面緊接著 "數字"、"左括號"、"字母" 或 "π" 時，補上 "*"
            // 例如 (3!)3 會變成 (3!)*3
            fixedExpression = fixedExpression.replace(Regex("!(?=[0-9\\.\\(a-zA-Z\\π])"), "!*")

            // 4. 智慧百分比處理
            fixedExpression = fixedExpression.replace("%(?![0-9\\.\\(\\π\\e])".toRegex(), "/100")

            // 定義階乘運算子 (!)
            val factorial = object : Operator("!", 1, true, Operator.PRECEDENCE_POWER + 1) {
                override fun apply(vararg args: Double): Double {
                    val arg = args[0].toInt()
                    // 驗證是否為整數
                    if (arg.toDouble() != args[0]) {
                        throw IllegalArgumentException("Operand for factorial has to be an integer")
                    }
                    if (arg < 0) {
                        throw IllegalArgumentException("Operand cannot be < 0")
                    }
                    // [修改] 移除 170 的限制，允許顯示 Infinity，這樣您才能驗證 3!!! 是否運算成功
                    var result = 1.0
                    for (i in 1..arg) {
                        result *= i.toDouble()
                    }
                    return result
                }
            }

            // ... (三角函數定義保持不變，請保留原有的 sin/cos 等函數) ...
            val sinFunc = object : Function("sin", 1) {
                override fun apply(vararg args: Double): Double {
                    return if (isDegree) sin(Math.toRadians(args[0])) else sin(args[0])
                }
            }
            val cosFunc = object : Function("cos", 1) {
                override fun apply(vararg args: Double): Double {
                    return if (isDegree) cos(Math.toRadians(args[0])) else cos(args[0])
                }
            }
            val tanFunc = object : Function("tan", 1) {
                override fun apply(vararg args: Double): Double {
                    return if (isDegree) tan(Math.toRadians(args[0])) else tan(args[0])
                }
            }
            val asinFunc = object : Function("asin", 1) {
                override fun apply(vararg args: Double): Double {
                    val result = asin(args[0])
                    return if (isDegree) Math.toDegrees(result) else result
                }
            }
            val acosFunc = object : Function("acos", 1) {
                override fun apply(vararg args: Double): Double {
                    val result = acos(args[0])
                    return if (isDegree) Math.toDegrees(result) else result
                }
            }
            val atanFunc = object : Function("atan", 1) {
                override fun apply(vararg args: Double): Double {
                    val result = atan(args[0])
                    return if (isDegree) Math.toDegrees(result) else result
                }
            }
            val logB = object : Function("logB", 2) {
                override fun apply(args: DoubleArray): Double {
                    return kotlin.math.ln(args[1]) / kotlin.math.ln(args[0])
                }
            }

            // 構建並計算
            val builder = ExpressionBuilder(fixedExpression)
                .operator(factorial)
                .function(sinFunc)
                .function(cosFunc)
                .function(tanFunc)
                .function(asinFunc)
                .function(acosFunc)
                .function(atanFunc)
                .function(logB)

            val result = builder.build().evaluate()

            val resultString = formatResult(result)

            if (fixedExpression.contains("%")) {
                val isSimpleModulo = fixedExpression.matches(Regex("^\\d+(\\.\\d+)?%\\d+(\\.\\d+)?$"))
                if (isSimpleModulo) {
                    outputTextView.text = "$resultString${getString(R.string.text_remainder)}"
                } else {
                    outputTextView.text = resultString
                }
            } else {
                outputTextView.text = resultString
            }
            // 強制讓輸出框捲動到最頂端
            // 使用 post 是為了確保文字更新渲染完成後才執行捲動，避免因為 UI 還沒畫好導致捲動無效
            outputTextView.post {
                outputTextView.scrollTo(0, 0)
            }
        } catch (e: Exception) {
            outputTextView.text = getString(R.string.text_error)
            e.printStackTrace()
        }
    }

    // [新增] 輔助判斷是否為「停止掃描」的字元
    private fun isStoppingChar(c: Char): Boolean {
        // 如果遇到運算符號，就停止 (代表運算元結束)
        // 注意：不包含 '!' (因為 (3!)!! 是合法的)
        // 也不包含 '(' 或 ')' (由 parenDepth 邏輯處理)
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%'
    }

    // 專門用來修剪浮點數誤差的美容師
    fun formatResult(value: Double): String {
        // 1. 使用 BigDecimal 來處理高精度的四雪五入
        // 設定保留小數點後 10 位 (足以應付日常與工程需求，也能過濾掉誤差)
        val bd = java.math.BigDecimal(value)
            .setScale(10, java.math.RoundingMode.HALF_UP)

        // 2. 去除尾部多餘的 0 (例如 30.0000 -> 3E+1)
        // 3. 轉成一般人類看的字串 (toPlainString 避免出現科學記號 3E+1)
        return bd.stripTrailingZeros().toPlainString()
    }

    //隨游標位置輸入
    fun addInput(textToInsert: String) {
        val currentInput = inputEditText.text.toString()
        if (currentInput == "Error") {
            inputEditText.setText("")
        }
        // 單純插入文字 (顏色由 inputEditText.setTextColor 統一控制)
        val cursorIndex = inputEditText.selectionStart.takeIf { it >= 0 } ?: inputEditText.length()
        inputEditText.text.insert(cursorIndex, textToInsert)
    }

    // 這是一個擴充函式，讓所有的 View (按鈕) 都能獲得「連點」的超能力
    fun View.setSmartClickListener(action: () -> Unit) {
        this.isSoundEffectsEnabled = false
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                action() // 執行動作
                triggerFeedback(this@setSmartClickListener)
                handler.postDelayed(this, 50) // 設定下次執行在 0.2 秒後
            }
        }

        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 1. 手指按下去的瞬間：
                    v.isPressed = true // 讓按鈕變色 (顯示按壓效果)
                    triggerFeedback(v)
                    action() // 馬上執行第一次動作 (讓短按也有反應)

                    // 2. 啟動計時器：但在 0.3 秒後才開始重複
                    // 這樣如果使用者在 0.3 秒內放開，就不會觸發連點
                    handler.postDelayed(runnable, 300)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 3. 手指放開或移開：
                    v.isPressed = false // 恢復按鈕顏色
                    handler.removeCallbacks(runnable) // 停止計時器，不准再重複了
                    v.performClick() // 為了符合無障礙規範 (可選)
                    true
                }

                else -> false
            }
        }
    }

    // 專門處理刪除邏輯的函式
    fun deleteInput() {
        val currentInput = inputEditText.text.toString()

        // 1. 處理 Error：如果是 Error，按下刪除就全清空
        if (currentInput == "Error") {
            inputEditText.setText("")
            return // 結束，不往下執行
        }

        // 2. 處理刪除：根據游標位置刪除前一個字
        // 必須先確認游標位置大於 0 (因為如果游標在最前面，前面沒有字可以刪)
        val cursorIndex = inputEditText.selectionStart
        if (cursorIndex > 0) {
            // 刪除 cursorIndex - 1 (前一個字) 到 cursorIndex (現在位置)
            inputEditText.text.delete(cursorIndex - 1, cursorIndex)
        }
    }

    private fun toggleHistoryVisibility() {
        // 這行魔法代碼會自動幫你做「滑出/淡入」的動畫效果
        android.transition.TransitionManager.beginDelayedTransition(rvHistory.parent as android.view.ViewGroup)

        if (rvHistory.visibility == View.VISIBLE) {
            rvHistory.visibility = View.GONE
            viewHandle.visibility = View.GONE
            btnHistory.visibility = View.VISIBLE
            btnMenu.visibility = View.VISIBLE
            btnClear.visibility = View.GONE
        } else {
            rvHistory.visibility = View.VISIBLE
            viewHandle.visibility = View.VISIBLE
            //強制套用上次記憶的高度
            val params = rvHistory.layoutParams
            params.height = lastHistoryHeight
            rvHistory.layoutParams = params

            btnHistory.visibility = View.VISIBLE
            btnMenu.visibility = View.GONE
            btnClear.visibility = View.VISIBLE

            // 每次打開都捲動到最新的紀錄
            if (historyData.isNotEmpty()) {
                rvHistory.scrollToPosition(historyData.size - 1)
            }
        }
    }

    //歷史列表拖曳
    private fun setupDragHandle() {
        viewHandle.setOnTouchListener(object : View.OnTouchListener {
            var startY = 0f
            var startHeight = 0
            var velocityTracker: android.view.VelocityTracker? = null

            // 用來執行平滑回彈的動畫器
            var heightAnimator: ValueAnimator? = null

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (velocityTracker == null) {
                    velocityTracker = android.view.VelocityTracker.obtain()
                }
                velocityTracker?.addMovement(event)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 如果正在回彈中，使用者又按住了，要立刻停止動畫，接管控制權
                        heightAnimator?.cancel()

                        startY = event.rawY
                        startHeight = rvHistory.height
                        v.alpha = 0.5f
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - startY).toInt()

                        // --- 優化點 1: 增加「阻尼」效果 (Rubber Banding) ---
                        // 這裡不直接限制 min/max，而是算出原本應該的高度
                        var targetHeight = startHeight + deltaY

                        val minHeight = (screenHeight * 0.2).toInt()
                        val maxHeight = (screenHeight * 0.75).toInt()

                        // 如果超出範圍，對超出的部分打折 (除以 3)，產生拉皮筋的阻力感
                        if (targetHeight > maxHeight) {
                            val excess = targetHeight - maxHeight
                            targetHeight = maxHeight + (excess / 3) // 阻尼係數
                        } else if (targetHeight < minHeight) {
                            val deficiency = minHeight - targetHeight
                            targetHeight = minHeight - (deficiency / 3) // 阻尼係數
                        }

                        // 更新高度
                        updateHeight(targetHeight)
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.alpha = 1.0f // 恢復顏色

                        // 計算速度 (單位: 像素/秒)
                        velocityTracker?.computeCurrentVelocity(1000)
                        val yVelocity = velocityTracker?.yVelocity ?: 0f

                        // 取得當前 View 的 Configuration
                        val viewConfiguration = android.view.ViewConfiguration.get(v.context)

                        // [修改點 1] 大幅提高判定門檻
                        // 原本的 min * 3 太靈敏了，這裡改用固定數值 1500 (一般快速滑動約 2000~4000)
                        // 這樣可以過濾掉手指離開時無意的微小回勾
                        val flingThreshold = 1500f

                        // 定義高度參數
                        val currentHeight = rvHistory.height
                        val closeThreshold = (screenHeight * 0.21).toInt()
                        val maxExpandThreshold = (screenHeight * 0.55).toInt()
                        val defaultHeight = (screenHeight * 0.3).toInt()
                        val expandHeight = (screenHeight * 0.7).toInt()

                        // 判斷是否正在向下展開 (目前高度 > 起始高度)
                        val isExpanding = currentHeight > startHeight

                        // === 判斷邏輯 (防呆優化版) ===
                        val targetDestination = when {
                            // 1. 明顯向下快速滑動 -> 展開
                            yVelocity > flingThreshold -> expandHeight

                            // 2. 明顯向上快速滑動 -> 收起
                            // [修改點 2] 加入防呆：如果是正在展開中(isExpanding)，且向上速度沒那麼快，
                            // 則視為手指回勾雜訊，不執行收起，改為走下面的位置判斷邏輯。
                            yVelocity < -flingThreshold -> {
                                // 只有當「沒在展開」或者「向上甩的力道真的很大(超過2倍門檻)」才執行收起
                                if (!isExpanding || yVelocity < -flingThreshold * 2) {
                                    toggleHistoryVisibility()
                                    velocityTracker?.recycle()
                                    velocityTracker = null
                                    return true
                                } else {
                                    // 雖然速度是負的，但判定是回勾雜訊，交給後面的位置判斷
                                    if (currentHeight > maxExpandThreshold) expandHeight else defaultHeight
                                }
                            }

                            // 3. 速度不夠快 -> 依照放手時的位置決定
                            else -> {
                                if (currentHeight <= closeThreshold) {
                                    toggleHistoryVisibility()
                                    velocityTracker?.recycle()
                                    velocityTracker = null
                                    return true
                                } else if (currentHeight > maxExpandThreshold) {
                                    expandHeight
                                } else {
                                    defaultHeight
                                }
                            }
                        }

                        lastHistoryHeight = targetDestination

                        // 執行動畫彈至最終位置
                        animateHeightTo(rvHistory.height, targetDestination)

                        // 回收測速器
                        velocityTracker?.recycle()
                        velocityTracker = null
                        return true
                    }
                }
                return false
            }

            // 封裝更新高度的邏輯
            private fun updateHeight(height: Int) {
                val params = rvHistory.layoutParams
                params.height = height
                rvHistory.layoutParams = params
            }

            // 平滑動畫函式
            private fun animateHeightTo(current: Int, target: Int) {
                // 這裡會自動使用 android.animation.ValueAnimator
                heightAnimator = ValueAnimator.ofInt(current, target).apply {
                    val distance = Math.abs(target - current)
                    duration = (distance / 2L).coerceIn(200L, 400L)

                    // 因為換回了原生 Animator，這裡直接賦值就不會報錯了
                    interpolator = android.view.animation.OvershootInterpolator(0.8f)

                    addUpdateListener { animation ->
                        val value = animation.animatedValue as Int
                        updateHeight(value)
                    }

                    start()
                }
            }
        })
    }

    //Help
    private fun showHelpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help, null)

        // 1. 改抓 WebView
        val webView = dialogView.findViewById<android.webkit.WebView>(R.id.webview_content)
        val btnReport = dialogView.findViewById<Button>(R.id.btn_report_issue)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close_dialog)

        // ... (計算螢幕高度的邏輯可以保留，也可以省略，WebView 通常 match_parent 配合 layout_weight 即可) ...
        // 如果你要保留高度控制，請對 webView 設定 layoutParams
        val displayMetrics = resources.displayMetrics
        val targetHeight = (displayMetrics.heightPixels * 0.75).toInt()
        val layoutParams = webView.layoutParams
        layoutParams.height = targetHeight
        webView.layoutParams = layoutParams

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 決定語言檔案
        val currentLocale = resources.configuration.locales[0]
        val isChinese = currentLocale.language == "zh" || currentLocale.toLanguageTag().contains("zh")
        val fileName = if (isChinese) "README_zh.md" else "README_en.md"

        try {
            // 讀取 Markdown 文字
            val inputStream = assets.open(fileName)
            val buffer = ByteArray(inputStream.available())
            inputStream.read(buffer)
            inputStream.close()
            val markdownText = String(buffer, java.nio.charset.Charset.forName("UTF-8"))

            // ★ 魔法轉換：將 Markdown 轉為美觀的 HTML
            // 這樣可以支援 GIF，還能用 CSS 控制圖片大小
            val htmlContent = """
            <html>
            <head>
                <style>
                    body { 
                        color: #E0E0E0; /* 文字顏色 (淺灰) */
                        font-family: sans-serif; 
                        font-size: 16px; 
                        line-height: 1.6; 
                        padding: 8px;
                    }
                    h1, h2, h3 { color: #80CBC4; /* 標題顏色 (你的主題綠) */ }
                    /* 讓所有圖片(包含GIF)自動適應寬度並置中 */
                    img { 
                        max-width: 100%; 
                        height: auto; 
                        display: block; 
                        margin: 16px auto; 
                        border-radius: 8px; /* 圖片圓角 */
                    }
                    a { color: #80CBC4; }
                </style>
            </head>
            <body style="background-color: transparent;">
                ${markdownToSimpleHtml(markdownText)}
            </body>
            </html>
        """.trimIndent()

            // WebView 設定
            webView.setBackgroundColor(0) // 背景透明
            webView.settings.allowFileAccess = true // 允許讀取 assets

            // 載入內容 (BaseURL 設為 android_asset 讓圖片路徑生效)
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )

        } catch (e: Exception) {
            // 錯誤處理 (可以直接顯示在 WebView 裡)
            webView.loadData("<h3>Load Error: ${e.message}</h3>", "text/html", "UTF-8")
        }

        //設定反饋按鈕邏輯 (Email Intent)
        btnReport.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:") // 只允許 Email app 處理
                putExtra(
                    android.content.Intent.EXTRA_EMAIL,
                    arrayOf("nasukonasuko13@gmail.com") // 您的管理員信箱
                )
                // 讀取 strings.xml 的預設主旨
                putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.email_subject))

                // 讀取 strings.xml 的預設內文 (包含自動抓取手機型號 android.os.Build.MODEL)
                putExtra(
                    android.content.Intent.EXTRA_TEXT,
                    getString(R.string.email_body, android.os.Build.MODEL)
                )
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // 如果使用者的手機沒裝 Email App，顯示提示
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.toast_no_email_app),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // ★ 輔助函式：簡單的 Markdown 轉 HTML 處理器
    // 這樣你就不用手動去改 MD 檔裡的語法了
    private fun markdownToSimpleHtml(markdown: String): String {
        var html = markdown

        // 1. 處理圖片語法 ![alt](src) -> <img src="src">
        // Regex 抓取 ![...](...) 結構
        val imgRegex = Regex("!\\[.*?]\\((.*?)\\)")
        html = imgRegex.replace(html) { matchResult ->
            val src = matchResult.groupValues[1]
            // 確保路徑有加 file:///android_asset/
            val finalSrc = if (src.startsWith("file")) src else "file:///android_asset/$src"
            "<img src=\"$finalSrc\" />"
        }

        // 2. 處理標題 # Title -> <h1>Title</h1>
        html = html.replace(Regex("^# (.*)$", RegexOption.MULTILINE), "<h1>$1</h1>")
        html = html.replace(Regex("^## (.*)$", RegexOption.MULTILINE), "<h2>$1</h2>")

        // 3. 處理粗體 **text** -> <b>text</b>
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")

        // 4. 處理換行 (Markdown 的換行在 HTML 需要 <br>)
        html = html.replace("\n", "<br>")

        return html
    }

    // 統一調整字體大小
    private fun applyTextScale(scale: Float) {
        inputEditText.textSize = BASE_TEXT_SIZE_INPUT * scale
        outputTextView.textSize = BASE_TEXT_SIZE_OUTPUT * scale
    }

    private fun applyBackground(path: String) {
        updateScrimState()
        ivGlobalBackground.visibility = View.VISIBLE

        Glide.with(this)
            .load(path)
            // === 加入這行，用當下時間作為簽名，強制 Glide 認定這是新圖 ===
            .signature(com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
            // ================================================================
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(ivGlobalBackground)
    }

    // 統一管理遮罩顯示
    private fun updateScrimState() {
        // 檢查是否正在顯示背景圖
        val hasBg = ivGlobalBackground.visibility == View.VISIBLE
        val isEnabled = settingsManager.isScrimEnabled

        if (hasBg && isEnabled) {
            viewScrim.visibility = View.VISIBLE
            viewScrim.alpha = settingsManager.scrimAlpha
        } else {
            // 如果沒開遮罩，就隱藏 viewScrim (但背景圖保持顯示)
            viewScrim.visibility = View.GONE
        }
    }
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // 1. 先讓我們的偵測器聞一下這個事件
        if (ev != null) {
            // 如果 swipeDetector 判定這是下滑手勢 (onFling 回傳 true)，我們就不往下傳了？
            // 不，這裡我們只單純偵測。
            // 注意：我們不需要判斷回傳值，因為我們希望觸發 toggleHistoryVisibility 後，
            // 原本的 View (如按鈕) 還是能收到 Up 事件以完成點擊動畫 (雖然選單蓋上來了沒差)
            swipeDetector.onTouchEvent(ev)
        }

        // 2. 絕對要執行這行！
        // 這是讓原本的點擊、滑動、輸入功能正常運作的關鍵
        return super.dispatchTouchEvent(ev)
    }
    // 統一管理輸入框背板
    private fun updateInputScrimState() {
        // 雖然通常在 onCreate 做過了，但為了防止意外，這裡可以加個安全檢查
        if (!::viewInputScrim.isInitialized) {
            viewInputScrim = findViewById(R.id.view_input_scrim)
        }
        val isEnabled = settingsManager.isInputScrimEnabled

        if (isEnabled) {
            viewInputScrim.visibility = View.VISIBLE
            viewInputScrim.alpha = settingsManager.inputScrimAlpha

            // 動態修改圓角
            val background = viewInputScrim.background as? android.graphics.drawable.GradientDrawable
            // === 根據深淺模式決定顏色 ===
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isNightMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val scrimColor = if (isNightMode) {
                android.graphics.Color.BLACK // 深色模式用黑色
            } else {
                android.graphics.Color.WHITE // 淺色模式用白色
            }
            // 設定顏色
            background?.setColor(scrimColor)

            // 將 dp 轉為 px
            val cornerPx = (settingsManager.inputScrimCorner * resources.displayMetrics.density)
            background?.cornerRadii = floatArrayOf(
                cornerPx, cornerPx, // 左上
                cornerPx, cornerPx, // 右上
                cornerPx, cornerPx, // 右下
                cornerPx, cornerPx  // 左下
            )
        } else {
            viewInputScrim.visibility = View.GONE
        }
    }
    //繼承AppCompatActivity()的MainActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        // 這會讓 App 一啟動就載入正確的顏色
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(settingsManager.themeMode)
        //當店面準備開張時 (onCreate)，如果有前一班留下來的交接事項 (savedInstanceState)，一併帶入。
        //第一件事，務必先執行總部的基礎水電開啟流程 (super.onCreate)，確保基礎設施沒問題。 接著，才開始執行我們這家店專屬的裝潢和擺設。
        setContentView(R.layout.mainactivity)

        //磨砂玻璃
        // 綁定變數
        // 這裡我們抓取最外層的 Layout 當作模糊的背景來源
        rootLayout =
            findViewById<android.view.View>(R.id.iv_global_background).parent as android.view.ViewGroup
        blurKeypad = findViewById(R.id.blur_keypad)

        // 初始化 Blur 引擎 (忽略過時警告)
        @Suppress("DEPRECATION")
        blurKeypad.setupWith(rootLayout, eightbitlab.com.blurview.RenderScriptBlur(this))
            .setBlurRadius(20f) // 模糊程度
            .setBlurEnabled(true) // 先開啟

        // 抓取基礎鍵盤容器
        val basicPad = findViewById<android.view.ViewGroup>(R.id.basicPad)
        viewInputScrim = findViewById(R.id.view_input_scrim)
        // APP 啟動當下，立刻幫「基礎鍵盤」穿衣服
        // 這時候 AdvancedPad 可能還沒準備好，所以我們先傳 null 或是目前的容器
        // 這樣至少數字鍵會立刻變成正確的顏色
        val advancedPadContainer = findViewById<ViewGroup>(R.id.advancedPad)
        val initialStyle = settingsManager.keypadMaterial
        val initBasicColor = settingsManager.customKeypadColor
        val initAdvColor = settingsManager.customAdvancedKeypadColor
        val initDegColor = settingsManager.customDegColor
        val initRadColor = settingsManager.customRadColor

        // 使用 post 確保 Layout 已經量測好寬高 (避免模糊失效)
        basicPad.post {
            KeypadStyler.applyStyle(this, initialStyle, blurKeypad, basicPad, advancedPadContainer,
                isDegree = isDegree, isInverse = isInverse,
                customBasicColor = initBasicColor, customAdvancedColor = initAdvColor,
                customDegColor = initDegColor, customRadColor = initRadColor)
        }
        // 註冊 Fragment 生命週期監聽器
        supportFragmentManager.registerFragmentLifecycleCallbacks(object :
            androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(
                fm: androidx.fragment.app.FragmentManager,
                f: androidx.fragment.app.Fragment
            ) {
                if (f is AdvancedPad) {
                    val view = f.view
                    if (view is ViewGroup) {
                        val currentStyle = settingsManager.keypadMaterial
                        val basicColor = settingsManager.customKeypadColor
                        val advColor = settingsManager.customAdvancedKeypadColor
                        val degColor = settingsManager.customDegColor
                        val radColor = settingsManager.customRadColor

                        view.post {
                            KeypadStyler.applyStyle(this@MainActivity, currentStyle, blurKeypad, basicPad, view,
                                isDegree = isDegree, isInverse = isInverse,
                                customBasicColor = basicColor, customAdvancedColor = advColor,
                                customDegColor = degColor, customRadColor = radColor)
                        }
                    }
                }
            }
        }, true)
        themeManager = ThemeManager(this)
        ivGlobalBackground = findViewById(R.id.iv_global_background)
        viewScrim = findViewById(R.id.view_background_scrim)
        // 載入已儲存的背景 (如果有)
        val savedPath = themeManager.getBackgroundPath()
        if (savedPath != null) {
            applyBackground(savedPath)
        } else {
            //如果沒背景，就強制重置 UI
            resetBackgroundUI()
        }
        updateScrimState()
        updateInputScrimState()
        // 沉浸式模式設定
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())

        inputEditText = findViewById<EditText>(R.id.input)
        outputTextView = findViewById<TextView>(R.id.output)
        // [新增] 判斷深色模式來決定輸出框的光暈
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isNightMode) {
            // 預設抓取目前的文字顏色來發光，或者用白色
            val outputColor = outputTextView.currentTextColor
            outputTextView.setShadowLayer(6f, 0f, 0f, outputColor)
        } else {
            outputTextView.setShadowLayer(0f, 0f, 0f, 0)
        }

        outputTextView.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
        val keyboardContainer = findViewById<android.widget.LinearLayout>(R.id.keyboardContainer)
        // 抓到空的電視機
        val advancedPad = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.advancedPad)
        // 指派 KeyboardAdapter()來負責提供畫面
        advancedPad.adapter = KeyboardAdapter(this)
        // 找到按鈕
        btnHistory = findViewById(R.id.btn_history)
        btnClear = findViewById(R.id.btn_clear)
        btnMenu = findViewById(R.id.btn_menu)
        val otherButton = findViewById<ImageButton>(R.id.other)
        val ACButton = findViewById<Button>(R.id.AC)
        val bracketButton = findViewById<Button>(R.id.bracket)
        val deleteButton = findViewById<Button>(R.id.delete)
        val equalButton = findViewById<Button>(R.id.equal)
        val numberIds = listOf(
            R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4,
            R.id.b5, R.id.b6, R.id.b7, R.id.b8, R.id.b9, R.id.dot
        )
        val operatorIds = listOf(
            R.id.add, R.id.minus, R.id.multiply, R.id.divide, R.id.percent
        )
        //右上角選單邏輯
        btnMenu.isSoundEffectsEnabled = false

        btnMenu.setOnClickListener { view ->
            triggerFeedback(view)
            // 建立 PopupMenu
            val popup = android.widget.PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

            // 設定點擊事件
            popup.setOnMenuItemClickListener { item ->
                if (settingsManager.isVibrationEnabled) {
                    window.decorView.performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    )
                }
                when (item.itemId) {
                    R.id.action_settings -> {
                        val settingsSheet = SettingsBottomSheet()
                        // 設定 Callback，當 Slider 移動時直接觸發
                        settingsSheet.onTextSizeChanged = { scale ->
                            applyTextScale(scale)
                        }
                        settingsSheet.show(supportFragmentManager, "settings")
                        true
                    }

                    R.id.action_theme -> {
                        val themeSheet = ThemeBottomSheet()

                        // 1. 設定 "更換背景" 的動作 -> 呼叫原本的裁切函式
                        themeSheet.onChangeBackgroundClick = {
                            startCrop()
                        }

                        // 2. 設定 "恢復預設" 的動作 -> 清除檔案並重置 UI
                        themeSheet.onResetBackgroundClick = {
                            themeManager.clearBackground()
                            resetBackgroundUI()
                            android.widget.Toast.makeText(
                                this,
                                R.string.reset_bg_confirm,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        themeSheet.onScrimStateChanged = {
                            updateScrimState()
                        }
                        themeSheet.onMaterialChanged = { newStyle ->
                            val basicPad = findViewById<android.view.ViewGroup>(R.id.basicPad)
                            val advancedPad = findViewById<android.view.ViewGroup>(R.id.advancedPad)
                            val basicColor = settingsManager.customKeypadColor
                            val advColor = settingsManager.customAdvancedKeypadColor
                            val degColor = settingsManager.customDegColor
                            val radColor = settingsManager.customRadColor

                            KeypadStyler.applyStyle(
                                this, newStyle, blurKeypad, basicPad, advancedPad,
                                isDegree = isDegree, isInverse = isInverse,
                                customBasicColor = basicColor, customAdvancedColor = advColor,
                                customDegColor = degColor, customRadColor = radColor
                            )
                        }

                        themeSheet.onKeypadColorChanged = { _ ->
                            val basicPad = findViewById<android.view.ViewGroup>(R.id.basicPad)
                            val advancedPad = findViewById<android.view.ViewGroup>(R.id.advancedPad)
                            val currentStyle = settingsManager.keypadMaterial
                            val basicColor = settingsManager.customKeypadColor
                            val advColor = settingsManager.customAdvancedKeypadColor
                            val degColor = settingsManager.customDegColor
                            val radColor = settingsManager.customRadColor

                            KeypadStyler.applyStyle(
                                this, currentStyle, blurKeypad, basicPad, advancedPad,
                                isDegree = isDegree, isInverse = isInverse,
                                customBasicColor = basicColor, customAdvancedColor = advColor,
                                customDegColor = degColor, customRadColor = radColor
                            )

                            // [新增] 顏色變了，輸入框文字顏色可能也要變
                            checkAndUpdateColor()
                        }
                        themeSheet.onInputScrimChanged = {
                            updateInputScrimState()
                        }
                        themeSheet.show(supportFragmentManager, "theme")
                        true
                    }

                    R.id.action_help -> {
                        showHelpDialog()
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        }
        // 初始化把手
        viewHandle = findViewById(R.id.view_handle)
        // 計算預設高度與螢幕高度
        val displayMetrics = resources.displayMetrics
        screenHeight = displayMetrics.heightPixels
        // 預設高度
        val defaultHeight = (screenHeight * 0.3).toInt()
        lastHistoryHeight = defaultHeight

        // 讓按鈕自動避開狀態欄 (Status Bar)
        btnHistory.applyTopWindowInsets()
        btnClear.applyTopWindowInsets()
        btnMenu.applyTopWindowInsets()

        applyTextScale(settingsManager.textSizeScale)

        // 初始化歷史紀錄模組
        historyManager = HistoryManager(this)
        rvHistory = findViewById(R.id.rv_history)
        // 設定 RecyclerView
        historyAdapter = HistoryAdapter(settingsManager) { selectedItem ->
            inputEditText.setText(selectedItem.expression)
            inputEditText.setSelection(selectedItem.expression.length)
            toggleHistoryVisibility()
        }
        rvHistory.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        // 讀取舊資料
        historyData.addAll(historyManager.getHistory())
        historyAdapter.submitList(historyData.toList()) // 更新畫面

        // 設定按鈕開關事件
        btnHistory.setFeedbackClickListener {
            toggleHistoryVisibility()
        }
        // 設定鍵盤不自動跳出
        inputEditText.showSoftInputOnFocus = false
        inputEditText.requestFocus()

        //清空歷史紀錄
        btnClear.setOnClickListener {
            // 1. 清空記憶體中的資料
            historyData.clear()

            // 2. 清空硬碟存檔 (呼叫 Manager)
            historyManager.clearHistory()

            // 3. 更新介面 (給它一個空清單)
            historyAdapter.submitList(emptyList())

            // 4. (選做) 給個提示或是自動關閉選單
            android.widget.Toast.makeText(
                this,
                getString(R.string.toast_history_cleared),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            toggleHistoryVisibility() // 清空後順便關起來
        }

        numberIds.forEach { id ->
            val button = findViewById<Button>(id)
            button.setSmartClickListener {
                addInput(button.text.toString())
            }
        }


        operatorIds.forEach { id ->
            val button = findViewById<Button>(id)
            button.setSmartClickListener {
                addInput(button.text.toString())
            }
        }

        ACButton.setFeedbackClickListener {
            inputEditText.setText("")
            outputTextView.text = ""
        }

        deleteButton.setSmartClickListener {
            deleteInput()
        }
        // === 安裝監視器 (TextWatcher) ===
        // 這就像是派一個保全盯著輸入框，只要字有變，就執行裡面的程式
        inputEditText.addTextChangedListener(object : android.text.TextWatcher {

            // 文字改變前 (暫時用不到)
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            // 文字正在改變中 (暫時用不到)
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            // 文字改變「後」：這是我們要的！
            override fun afterTextChanged(s: android.text.Editable?) {
                // 取得輸入框現在的整串文字
                val inputString = s.toString()
                checkAndUpdateColor()
                // 防呆：如果輸入是空的，或者最後一個字是運算符號(如 "10+")，就先不算，避免當機
                if (inputString.isNotEmpty() && !isOperator(inputString.last())) {
                    calculateResult(inputString)
                } else if (inputString.isEmpty()) {
                    outputTextView.text = "" // 如果清空輸入，輸出也要清空
                }
            }
        })

        // === 設定等於鍵 (Equal Button) ===
        equalButton.setFeedbackClickListener {
            val expression = inputEditText.text.toString()
            val result = outputTextView.text.toString()
            val errorString = getString(R.string.text_error)
            val isInvalid =
                result == errorString || result == "NaN" || result == "Infinity" || result == "-Infinity"

            // 只有當輸出框有答案，且不是 Error 時才執行
            if (result.isNotEmpty() && !isInvalid) {

                // === ★ 步驟 1：先存檔 (趁算式還沒被覆蓋掉之前！) ===
                // 注意：這裡我們手動建立 HistoryItem，不透過 calculateResult
                val newItem = HistoryItem(expression, result)

                // 加到清單第一筆
                historyData.add(newItem)

                // 限制筆數 (例如只留 50 筆)
                if (historyData.size > 30) {
                    historyData.removeAt(0)
                }

                // 存入硬碟 & 更新列表 UI
                historyManager.saveHistory(historyData)
                historyAdapter.submitList(historyData.toList())
                //每次新增後，自動捲動到最底部 (讓使用者看到最新的一筆)
                rvHistory.post {
                    rvHistory.scrollToPosition(historyData.size - 1)
                }
                // === 覆蓋輸出框的答案 ===
                // 1. 把輸出框的答案移到輸入框
                inputEditText.setText(result)

                // 2. 把游標移到最後面
                inputEditText.setSelection(inputEditText.length())

                // 3. 清空輸出框
                outputTextView.text = ""
            }
        }

        bracketButton.setFeedbackClickListener {
            // 1. 取得游標位置與全文
            val cursorIndex =
                inputEditText.selectionStart.takeIf { it >= 0 } ?: inputEditText.length()
            val fullText = inputEditText.text.toString()

            // 2. 切割：只分析「游標左邊」的世界
            val textBeforeCursor = fullText.substring(0, cursorIndex)

            // 3. 統計左邊的「欠債狀況」 (Depth)
            val openCount = textBeforeCursor.count { it == '(' }
            val closedCount = textBeforeCursor.count { it == ')' }
            val depth = openCount - closedCount

            // 4. 取得關鍵鄰居 (前一個字 & 後一個字)
            val prevChar = textBeforeCursor.lastOrNull()
            val nextChar = if (cursorIndex < fullText.length) fullText[cursorIndex] else null

            val textToAdd = when {
                // === 1. 剛開始或接在符號後面 -> 開括號 ===
                prevChar == null || isOperator(prevChar) || prevChar == '(' -> "("

                // === 2. 接在數字、% 或 ! 後面 ===
                prevChar.isDigit() || prevChar == '%' || prevChar == 'π' || prevChar == 'e' || prevChar == '!' -> {
                    if (depth > 0) ")" else "("
                }

                // === 3. 接在右括號後面 ===
                prevChar == ')' -> {
                    // 優先權 A：如果後面緊接著「數字」)
                    // 代表使用者想在兩個數字區塊間做運算，即使有欠債，也傾向於開新乘法
                    if (nextChar != null && (nextChar.isDigit() || nextChar == 'π' || nextChar == 'e')) {
                        "("
                    }
                    // 優先權 B：如果有欠債 (Depth > 0)
                    // 且後面不是數字 (例如是 '(' 或沒東西)，那必須先關門
                    else if (depth > 0) {
                        ")"
                    }
                    // 優先權 C：沒欠債了，那就開新乘法
                    else {
                        "("
                    }
                }

                else -> "("
            }
            addInput(textToAdd)
        }
        // === 設定箭頭按鈕的展開/收合邏輯 ===
        otherButton.setOnClickListener {
            android.transition.TransitionManager.beginDelayedTransition(keyboardContainer as android.view.ViewGroup)
            val advancedPadView = findViewById<android.view.ViewGroup>(R.id.advancedPad)
            val basicColor = settingsManager.customKeypadColor
            val advColor = settingsManager.customAdvancedKeypadColor
            val degColor = settingsManager.customDegColor
            val radColor = settingsManager.customRadColor

            if (advancedPad.visibility == View.VISIBLE) {
                // 收起
                advancedPad.visibility = View.GONE
                otherButton.animate().rotation(0f).setDuration(200).start()
                val currentStyle = settingsManager.keypadMaterial
                KeypadStyler.applyStyle(
                    this, currentStyle, blurKeypad, findViewById(R.id.basicPad), advancedPad,
                    isDegree = isDegree, isInverse = isInverse,
                    customBasicColor = basicColor, customAdvancedColor = advColor,
                    customDegColor = degColor, customRadColor = radColor
                )
            } else {
                // 展開
                advancedPad.visibility = View.VISIBLE
                otherButton.animate().rotation(180f).setDuration(200).start()
                val currentStyle = settingsManager.keypadMaterial
                KeypadStyler.applyStyle(
                    this, currentStyle, blurKeypad, basicPad, advancedPadView,
                    isDegree = isDegree, isInverse = isInverse,
                    customBasicColor = basicColor, customAdvancedColor = advColor,
                    customDegColor = degColor, customRadColor = radColor
                )
            }
        }
        // 初始化：預設為 DEG 顏色
        setInputColor(true)
        // 設定拖曳監聽器 (呼叫函式)
        setupDragHandle()
        // === [新增] 初始化全域下滑手勢偵測器 ===
        swipeDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {

            // 用來記錄手指「按下瞬間」的狀態
            // true = 按下時已經在頂端 (或是按在背景)，允許觸發
            // false = 按下時還沒到頂 (代表這是個捲動操作)，本次手勢禁止觸發
            private var shouldTriggerHistory = true

            override fun onDown(e: MotionEvent): Boolean {
                // 1. 取得輸入/輸出框的區域
                val inputRect = android.graphics.Rect()
                inputEditText.getGlobalVisibleRect(inputRect)

                val outputRect = android.graphics.Rect()
                outputTextView.getGlobalVisibleRect(outputRect)

                // 2. 判斷按下位置
                val isTouchingInput = inputRect.contains(e.rawX.toInt(), e.rawY.toInt())
                val isTouchingOutput = outputRect.contains(e.rawX.toInt(), e.rawY.toInt())

                // 3. 核心邏輯：依據「按下時」的狀態決定命運
                if (isTouchingInput) {
                    // 如果按在輸入框：只有當「當下已經在頂端」才允許觸發
                    // canScrollVertically(-1) 為 true 代表還能往上捲 (不在頂)
                    shouldTriggerHistory = !inputEditText.canScrollVertically(-1)
                }
                else if (isTouchingOutput) {
                    // 如果按在輸出框：同上
                    shouldTriggerHistory = !outputTextView.canScrollVertically(-1)
                }
                else {
                    // 如果按在背景 (按鈕旁、頂部空白處)：
                    // 永遠允許觸發
                    shouldTriggerHistory = true
                }

                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                // 4. 第一關：檢查「按下瞬間」的授權
                // 如果按下時是在捲動文字 (shouldTriggerHistory = false)，
                // 就算現在滑很用力，這裡直接擋掉，忽略手勢。
                if (!shouldTriggerHistory) {
                    return false
                }

                val deltaY = e2.rawY - e1.rawY
                val deltaX = e2.rawX - e1.rawX

                // 5. 第二關：檢查是否為有效的下滑手勢
                if (Math.abs(deltaY) > Math.abs(deltaX)) { // 垂直為主
                    if (deltaY > 50 && velocityY > 50) { // 向下 + 有速度
                        // 6. 只有當歷史紀錄沒開的時候才觸發
                        if (rvHistory.visibility != View.VISIBLE) {
                            toggleHistoryVisibility()
                            return true
                        }
                    }
                }
                return false
            }
        })
        // === 首次啟動新手教學 ===
        if (settingsManager.isFirstRun) {
            // 使用 post 確保介面繪製完成後再彈出 Dialog
            window.decorView.post {
                showHelpDialog()
            }
            // 標記為已讀，下次不再顯示
            settingsManager.isFirstRun = false
        }
    }

    override fun onStart() {
        super.onStart()
        // 當 Activity 變為可見時，通知 Tile 更新為「亮起」狀態
        QuickCalcTileService.updateTile(this, isRunning = true)
    }

    override fun onStop() {
        super.onStop()
        // 當 Activity 不再可見時，通知 Tile 更新為「熄滅」狀態
        QuickCalcTileService.updateTile(this, isRunning = false)
    }
    //觸發回饋的輔助函式
    private fun triggerFeedback(view: View) {
        if (settingsManager.isVibrationEnabled) {
            // 改用 VIRTUAL_KEY，震動感比 KEYBOARD_TAP 明顯
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        if (settingsManager.isSoundEnabled) {
            val audioManager =
                getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
        }
    }

    //擴充函式，適用於單次點擊的按鈕 (AC, =, (), History, Menu)
    fun View.setFeedbackClickListener(action: () -> Unit) {
        this.isSoundEffectsEnabled = false
        setOnClickListener {
            triggerFeedback(this) // 先回饋
            action() // 再執行
        }
    }

    private fun resetBackgroundUI() {
        viewScrim.visibility = View.GONE
        ivGlobalBackground.visibility = View.GONE
        Glide.with(this).clear(ivGlobalBackground)
    }

    private fun startCrop() {
        // 1. 取得目前螢幕的解析度 (寬高)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 設定裁切參數
        val options = CropImageContractOptions(
            uri = null,
            cropImageOptions = CropImageOptions(
                imageSourceIncludeGallery = true,
                imageSourceIncludeCamera = false,
                cropShape = CropImageView.CropShape.RECTANGLE,

                // === 【修改】核心邏輯：鎖定螢幕比例 ===
                fixAspectRatio = true,          // 開啟固定比例模式
                aspectRatioX = screenWidth,     // 設定比例的寬 = 螢幕寬
                aspectRatioY = screenHeight,    // 設定比例的高 = 螢幕高
                // ==================================

                guidelines = CropImageView.Guidelines.ON,
            )
        )
        cropImage.launch(options)
    }

    fun setInverseMode(inverse: Boolean) {
        isInverse = inverse
        val basicPad = findViewById<ViewGroup>(R.id.basicPad)
        val advancedPad = findViewById<ViewGroup>(R.id.advancedPad)
        if (advancedPad.visibility == View.VISIBLE) {
            val currentStyle = settingsManager.keypadMaterial
            val basicColor = settingsManager.customKeypadColor
            val advColor = settingsManager.customAdvancedKeypadColor
            val degColor = settingsManager.customDegColor
            val radColor = settingsManager.customRadColor

            KeypadStyler.applyStyle(
                this, currentStyle, blurKeypad, basicPad, advancedPad,
                isDegree = isDegree, isInverse = isInverse,
                customBasicColor = basicColor, customAdvancedColor = advColor,
                customDegColor = degColor, customRadColor = radColor
            )
        }
    }

    // 這是負責管理「分頁」的管理員
// FragmentStateAdapter 需要 import androidx.viewpager2.adapter.FragmentStateAdapter
    class KeyboardAdapter(activity: androidx.fragment.app.FragmentActivity) :
        androidx.viewpager2.adapter.FragmentStateAdapter(activity) {

        // 這裡決定你有幾頁？目前我們只有「工程模式」1頁
        override fun getItemCount(): Int = 1

        // 這裡決定每一頁要顯示哪個 Fragment
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                // 這裡必須呼叫「類別名稱」的建構函式，而不是 xml 檔名
                0 -> AdvancedPad()
                else -> AdvancedPad()
            }
        }
    }

    // 讓任何 View 都能自動避開狀態欄
    fun View.applyTopWindowInsets(extraMargin: Int = 39) {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val params = view.layoutParams as ViewGroup.MarginLayoutParams

            // 自動計算：狀態欄高度 + 額外距離
            params.topMargin = bars.top + extraMargin
            view.layoutParams = params
            insets
        }
    }
}