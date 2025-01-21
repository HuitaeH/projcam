package com.example.cameraapp.analyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PreviewOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var multiFaceAnalyzer: MultiFaceAnalyzer? = null
    private val paint = Paint().apply {
        color = android.graphics.Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    // 얼굴 경계 박스 설정
    fun setAnalyzer(analyzer: MultiFaceAnalyzer) {
        this.multiFaceAnalyzer = analyzer
        invalidate() // 뷰 갱신
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        multiFaceAnalyzer?.let { analyzer ->
            analyzer.getSelectedFaceBounds()?.let { bounds ->
                // 선택된 얼굴 경계 그리기
                canvas.drawRect(
                    bounds.left * width,
                    bounds.top * height,
                    bounds.right * width,
                    bounds.bottom * height,
                    paint
                )
            }
        }
    }
}
