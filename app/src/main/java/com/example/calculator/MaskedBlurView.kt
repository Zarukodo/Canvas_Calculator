package com.example.calculator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import eightbitlab.com.blurview.BlurView

class MaskedBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BlurView(context, attrs, defStyleAttr) {

    private val maskTargets = mutableListOf<View>()
    private val maskPath = Path()
    private val viewRect = Rect()
    private val globalOffset = Point()

    // 監聽器：當 View 樹準備繪製時觸發 (這是動畫流暢的關鍵)
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        // 如果有設定目標，就強制重繪，確保遮罩位置永遠跟著按鈕跑
        if (maskTargets.isNotEmpty()) {
            invalidate()
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 綁定監聽器
        viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 移除監聽器，避免記憶體洩漏
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
    }

    fun setMaskViews(views: List<View>) {
        maskTargets.clear()
        maskTargets.addAll(views)
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        if (maskTargets.isEmpty()) {
            super.draw(canvas)
            return
        }

        maskPath.reset()
        // 取得 BlurView 自己的絕對座標
        this.getGlobalVisibleRect(Rect(), globalOffset)

        for (view in maskTargets) {
            // 只處理看得到的按鈕
            if (view.visibility == View.VISIBLE && view.isAttachedToWindow) {
                view.getGlobalVisibleRect(viewRect)

                // 校正座標：把按鈕的絕對座標轉成 BlurView 的相對座標
                viewRect.offset(-globalOffset.x, -globalOffset.y)

                // 建立路徑
                val rectF = RectF(viewRect)
                // 圓角半徑：建議跟您的 themes.xml 保持一致 (20dp)
                val cornerRadius = 20f * resources.displayMetrics.density
                maskPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
            }
        }

        val saveCount = canvas.save()
        try {
            canvas.clipPath(maskPath)
            super.draw(canvas)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }
}