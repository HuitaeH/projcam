package com.example.cameraapp.ui.theme.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
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





    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    private val gaugePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val gaugeBackgroundPaint = Paint().apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    fun updateResult(result: PoseComparator.ComparisonResult) {
        comparisonResult = result
        invalidate()
    }








    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 삼분할선 먼저 그리기
        drawThirdsGrid(canvas)

        comparisonResult?.let { result ->
            // 게이지 바 그리기
            val gaugeHeight = 20f
            val gaugeY = height * 0.02f


            val cornerRadius = 10f  // 둥근 모서리
            val padding = width * 0.05f  // 좌우 패딩


            // 게이지 배경 (둥근 모서리)
            val backgroundRect = RectF(padding, gaugeY, width - padding, gaugeY + gaugeHeight)
            //val backgroundRect = RectF(gaugeX, padding, gaugeX + gaugeWidth, height - padding)
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, gaugeBackgroundPaint)

            // 점수에 따른 게이지 너비와 색상
            val fillWidth = minOf(
               width - padding,  // 최대 너비 제한
                (((width - (padding * 2)) * (result.centerScore / 100f)) + padding)
            )


            gaugePaint.color = when {
                result.centerScore >= 90 -> Color.GREEN
                result.centerScore >= 70 -> Color.YELLOW
                else -> Color.RED
            }




            // 게이지 채우기 (둥근 모서리)
            val fillRect = RectF(padding, gaugeY, fillWidth, gaugeY + gaugeHeight)
            //val fillRect = RectF(gaugeX, height - fillHeight, gaugeX + gaugeWidth, height - padding)
            canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, gaugePaint)

//

            // 반투명 배경
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

            // 점수 표시
            val scoreX = width / 2f
            val scoreBaseY = height * 0.15f

            // 구도 점수
            scorePaint.color = when {
                result.thirdsScore >= 90 -> Color.GREEN
                result.thirdsScore >= 70 -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawText(
                "구도: ${result.thirdsScore.toInt()}%",
                scoreX,
                scoreBaseY + 200f,
                scorePaint
            )
        }
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






    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        arrowAnimator.cancel()
    }
}