package com.example.cameraapp.ui.theme.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.example.cameraapp.analyzer.PoseComparator

class PoseSuggestionView(context: Context) : View(context) {
    private var comparisonResult: PoseComparator.ComparisonResult? = null
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

    fun updateResult(result: PoseComparator.ComparisonResult) {
        comparisonResult = result
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        comparisonResult?.let { result ->
            // 반투명 배경
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

            // 점수 표시
            val scoreX = width / 2f
            val scoreBaseY = height * 0.15f

            // 전체 점수
            scorePaint.color = when {
                result.overallScore >= 90 -> Color.GREEN
                result.overallScore >= 70 -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawText("전체: ${result.overallScore.toInt()}%", scoreX, scoreBaseY, scorePaint)

            // 자세 점수
            val positionScore = result.positionScore
            scorePaint.color = when {
                positionScore >= 90 -> Color.GREEN
                positionScore >= 70 -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawText("자세: ${positionScore.toInt()}%", scoreX, scoreBaseY + 100f, scorePaint)

            // 구도 점수
            scorePaint.color = when {
                result.thirdsScore >= 90 -> Color.GREEN
                result.thirdsScore >= 70 -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawText("구도: ${result.thirdsScore.toInt()}%", scoreX, scoreBaseY + 200f, scorePaint)

            // 화살표 표시
            drawDirectionalArrows(canvas, result)

            // 제안사항 표시
            drawSuggestions(canvas, result.suggestions)
        }
    }

    private fun drawDirectionalArrows(canvas: Canvas, result: PoseComparator.ComparisonResult) {
        // 점수가 낮을수록 화살표가 더 선명하게 표시
        arrowPaint.alpha = ((100 - result.overallScore) * 2.55f).toInt().coerceIn(0, 255)

        result.detailedScores.forEach { (key, score) ->
            val threshold = 0.5f  // 임계값
            when (key) {
                "NOSE" -> {
                    if (score < threshold) {
                        drawArrow(canvas, "CENTER", arrowPaint)
                    }
                }
                "SHOULDERS" -> {
                    if (score < threshold) {
                        drawArrow(canvas, "LEFT", arrowPaint)
                        drawArrow(canvas, "RIGHT", arrowPaint)
                    }
                }
                "HIPS" -> if (score < threshold) drawArrow(canvas, "DOWN", arrowPaint)
            }
        }

        // 구도 점수가 낮으면 추가 안내 화살표
        if (result.thirdsScore < 70f) {
            drawThirdsGuideArrows(canvas)
        }
    }

    private fun drawThirdsGuideArrows(canvas: Canvas) {
        val arrowSize = width * 0.05f  // 구도 안내 화살표는 좀 더 작게

        // 세로 삼분할선 표시
        val thirdX1 = width / 3f
        val thirdX2 = width * 2 / 3f
        val lineY1 = height * 0.2f
        val lineY2 = height * 0.8f

        arrowPaint.color = Color.WHITE
        arrowPaint.alpha = 128

        canvas.drawLine(thirdX1, lineY1, thirdX1, lineY2, arrowPaint)
        canvas.drawLine(thirdX2, lineY1, thirdX2, lineY2, arrowPaint)
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
            "DOWN" -> {
                paint.color = Color.MAGENTA
                path.moveTo(centerX, centerY + arrowSize * 2 - offset)
                path.lineTo(centerX - arrowSize, centerY + arrowSize - offset)
                path.lineTo(centerX + arrowSize, centerY + arrowSize - offset)
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