package com.example.cameraapp.ui.theme.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.example.cameraapp.analyzer.FacialComparator

class FaceSuggestionView(context: Context) : View(context) {
    private var comparisonResult: FacialComparator.ComparisonResult? = null
    private var arrowPhase = 0f
    private val arrowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500  // 1.5초 주기
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animation ->
            arrowPhase = animation.animatedValue as Float
            invalidate()
        }
        start()
    }

    private val gridPaint = Paint().apply {
        color = Color.WHITE
        alpha = 70  // 투명도 설정
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val scorePaint = Paint().apply {
        color = Color.WHITE
        textSize = 80f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }

    private val arrowPaint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 8f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val suggestionPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isFakeBoldText = true
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    fun updateResult(result: FacialComparator.ComparisonResult) {
        comparisonResult = result
        invalidate()
    }

    private val logButtonPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 180
    }

    private val logButtonTextPaint = Paint().apply {
        color = Color.BLACK
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var shouldLog = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 삼분할선 먼저 그리기
        drawThirdsGrid(canvas)

        comparisonResult?.let { result ->
            // 반투명 배경
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

            // 점수 표시
            val scoreX = width / 2f
            val scoreBaseY = height * 0.15f

            // 얼굴 점수
            scorePaint.color = when {
                result.overallScore >= 90 -> Color.GREEN
                result.overallScore >= 70 -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawText("아재력: ${result.overallScore.toInt()}%", scoreX, scoreBaseY, scorePaint)

            // 화살표 표시
            drawDirectionalArrows(canvas, result)




        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 버튼 영역 체크
                val buttonWidth = 150f
                val buttonHeight = 80f
                val buttonLeft = width - buttonWidth - 20f
                val buttonTop = 20f
                val x = event.x
                val y = event.y

                if (x >= buttonLeft && x <= buttonLeft + buttonWidth &&
                    y >= buttonTop && y <= buttonTop + buttonHeight) {
                    shouldLog = true
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                shouldLog = false
            }
        }
        return super.onTouchEvent(event)
    }

    fun getShouldLog(): Boolean {
        return shouldLog
    }

    private fun drawThirdsGrid(canvas: Canvas) {
        // 세로 삼분할선
        val thirdX1 = width / 3f
        val thirdX2 = width * 2 / 3f

        // 가로 삼분할선
        val thirdY1 = height / 3f
        val thirdY2 = height * 2 / 3f

        // 세로선 그리기
        canvas.drawLine(thirdX1, 0f, thirdX1, height.toFloat(), gridPaint)
        canvas.drawLine(thirdX2, 0f, thirdX2, height.toFloat(), gridPaint)

        // 가로선 그리기
        canvas.drawLine(0f, thirdY1, width.toFloat(), thirdY1, gridPaint)
        canvas.drawLine(0f, thirdY2, width.toFloat(), thirdY2, gridPaint)
    }

    private fun drawDirectionalArrows(canvas: Canvas, result: FacialComparator.ComparisonResult) {
        // 점수가 낮을수록 화살표가 더 선명하게 표시
        arrowPaint.alpha = ((100 - result.overallScore) * 2.55f).toInt().coerceIn(0, 255)

        result.detailedScores.forEach { (key, score) ->
            val threshold = 0.5f  // 임계값
            when (key) {
                "LEFT_EYE" -> {
                    if (score < threshold) {
                        drawArrow(canvas, "LEFT", arrowPaint)
                    }
                }
                "RIGHT_EYE" -> {
                    if (score < threshold) {
                        drawArrow(canvas, "RIGHT", arrowPaint)
                    }
                }
                "MOUTH" -> if (score < threshold) drawArrow(canvas, "UP", arrowPaint)
            }
        }
    }

    private fun drawArrow(canvas: Canvas, direction: String, paint: Paint) {
        val path = Path()
        val arrowSize = width * 0.1f
        val centerX = width / 2f
        val centerY = height / 2f

        // 화살표 움직임을 위한 오프셋 계산
        val offset = (arrowSize * 0.2f) * Math.sin(arrowPhase * 2 * Math.PI).toFloat()

        when (direction) {
            "CENTER" -> {
                paint.color = Color.YELLOW
                val radius = arrowSize * 0.5f
                val pulseRadius = radius + (arrowSize * 0.1f) * Math.sin(arrowPhase * 2 * Math.PI).toFloat()
                canvas.drawCircle(centerX, centerY, pulseRadius, paint)
            }
            "LEFT" -> {
                paint.color = Color.CYAN
                path.moveTo(centerX - arrowSize * 2 + offset, centerY)
                path.lineTo(centerX - arrowSize + offset, centerY - arrowSize)
                path.lineTo(centerX - arrowSize + offset, centerY + arrowSize)
                path.close()
            }
            "RIGHT" -> {
                paint.color = Color.CYAN
                path.moveTo(centerX + arrowSize * 2 - offset, centerY)
                path.lineTo(centerX + arrowSize - offset, centerY - arrowSize)
                path.lineTo(centerX + arrowSize - offset, centerY + arrowSize)
                path.close()
            }
            "UP" -> {
                paint.color = Color.MAGENTA
                path.moveTo(centerX, centerY - arrowSize * 2 + offset)
                path.lineTo(centerX - arrowSize, centerY - arrowSize - offset)
                path.lineTo(centerX + arrowSize, centerY - arrowSize - offset)
                path.close()
            }
        }

        if (direction != "CENTER") {
            canvas.drawPath(path, paint)
        }
    }

    private fun drawSuggestions(canvas: Canvas, suggestions: List<String>) {
        suggestions.forEachIndexed { index, suggestion ->
            val y = height * 0.8f + (index * 60f)
            canvas.drawText(suggestion, width * 0.1f, y, suggestionPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        arrowAnimator.cancel()
    }
}
