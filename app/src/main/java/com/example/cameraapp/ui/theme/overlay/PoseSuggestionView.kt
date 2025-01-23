package com.example.cameraapp.ui.theme.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import com.example.cameraapp.analyzer.PoseComparator
import kotlin.math.abs

class PoseSuggestionView(context: Context) : View(context) {
    private var comparisonResult: PoseComparator.ComparisonResult? = null
    private var arrowPhase = 0f


    private val arrowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animation ->
            arrowPhase = animation.animatedValue as Float
            invalidate()
        }
        start()
    }
    private val zoomGuidePaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }



    // COM 점 텍스트를 위한 Paint 추가
    private val comTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.LEFT
    }
    // COM 점 표시를 위한 Paint 추가
    private val currentComPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val targetComPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    private val connectionPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }


    private val gridPaint = Paint().apply {
        color = Color.WHITE
        alpha = 70
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

    private val arrowPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val targetFramePaint = Paint().apply {
        color = Color.WHITE
        alpha = 50
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    fun updateResult(result: PoseComparator.ComparisonResult) {
        comparisonResult = result
        invalidate()
    }






    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawThirdsGrid(canvas)

        comparisonResult?.let { result ->
            // 게이지 바 그리기
            val gaugeHeight = 20f
            val gaugeY = height * 0.02f
            val cornerRadius = 10f
            val padding = width * 0.05f

            // 게이지 배경
            val backgroundRect = RectF(padding, gaugeY, width - padding, gaugeY + gaugeHeight)
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, gaugeBackgroundPaint)

            // 게이지 채우기
            val fillWidth = minOf(
                width - padding,
                (((width - (padding * 2)) * (result.centerScore / 100f)) + padding)
            )

            gaugePaint.color = when {
                result.centerScore >= 90 -> Color.GREEN
                result.centerScore >= 70 -> Color.YELLOW
                else -> Color.RED
            }

            val fillRect = RectF(padding, gaugeY, fillWidth, gaugeY + gaugeHeight)
            canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, gaugePaint)

            // 반투명 배경
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

            // 점수 표시
            val scoreX = width / 2f
            val scoreBaseY = height * 0.15f

            scorePaint.color = when {
                result.centerScore >= 90 -> Color.GREEN
                result.centerScore >= 70 -> Color.YELLOW
                else -> Color.RED
            }
//            canvas.drawText(
//                "구도: ${result.centerScore.toInt()}%",
//                scoreX,
//                scoreBaseY + 200f,
//                scorePaint
//            )



//            getArrowDirection(result)?.let { direction ->
//                drawDirectionalArrow(canvas, direction)
//            }

            // COM 점들과 연결선 그리기 부분 수정
            val currentX = width * result.currentComX
            val currentY = height * result.currentComY
            val targetX = width * result.targetComX
            val targetY = height * result.targetComY

// 화면 범위 체크
            if (currentX in 0f..width.toFloat() && currentY in 0f..height.toFloat()) {
                // 점선 연결
                canvas.drawLine(currentX, currentY, targetX, targetY, connectionPaint)

                // 현재 COM 점과 텍스트
                canvas.drawCircle(currentX, currentY, 15f, currentComPaint)
                canvas.drawText("Current", currentX + 20f, currentY, comTextPaint)

                // 목표 COM 점과 텍스트
                canvas.drawCircle(targetX, targetY, 15f, targetComPaint)
                canvas.drawText("Target", targetX + 20f, targetY, comTextPaint)
            }


            drawZoomGuide(canvas, result.zoomSuggestion)


        }
    }

    private fun drawZoomGuide(canvas: Canvas, zoomSuggestion: String) {
        val zoomGuideX = width / 2f
        val zoomGuideY = height * 0.1f

        // 줌 가이드 색상
        val color = when (zoomSuggestion) {
            "Zoom Out" -> Color.YELLOW
            "Zoom In" -> Color.RED
            else -> Color.GREEN
        }

        val paint = Paint().apply {
            this.color = color
            textSize = 60f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(zoomSuggestion, zoomGuideX, zoomGuideY, paint)
    }




    private fun drawThirdsGrid(canvas: Canvas) {
        val thirdX1 = width / 3f
        val thirdX2 = width * 2 / 3f
        val thirdY1 = height / 3f
        val thirdY2 = height * 2 / 3f

        canvas.drawLine(thirdX1, 0f, thirdX1, height.toFloat(), gridPaint)
        canvas.drawLine(thirdX2, 0f, thirdX2, height.toFloat(), gridPaint)
        canvas.drawLine(0f, thirdY1, width.toFloat(), thirdY1, gridPaint)
        canvas.drawLine(0f, thirdY2, width.toFloat(), thirdY2, gridPaint)
    }

    // PoseSuggestionView 클래스에 추가
    data class ArrowDirection(val angle: Float, val distance: Float)

    // PoseSuggestionView.kt의 getArrowDirection 함수 수정
    private fun getArrowDirection(result: PoseComparator.ComparisonResult): ArrowDirection? {
        if (result.centerScore >= 90) return null

        // 현재 COM에서 목표 COM으로 향하는 벡터 계산
        val dx = (result.targetComX - result.currentComX) * width
        val dy = (result.targetComY - result.currentComY) * height

        if (abs(dx) < 0.01f && abs(dy) < 0.01f) return null

        val angle = kotlin.math.atan2(dy, dx)
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

        return ArrowDirection(angle, distance)
    }

    private fun drawDirectionalArrow(canvas: Canvas, direction: ArrowDirection) {
        comparisonResult?.let { result ->
            // 현재 COM 좌표 (화살표 시작점)
            val startX = width * result.currentComX
            val startY = height * result.currentComY

            // 목표 COM 좌표 (화살표 끝점)
            val endX = width * result.targetComX
            val endY = height * result.targetComY

            // 화살표 그리기
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            // 화살표 머리 크기는 화면 크기에 비례하게 설정
            val arrowSize = width * 0.05f

            // 화살표 머리 각도 계산 (45도)
            val angle = kotlin.math.atan2(endY - startY, endX - startX)
            val arrowAngle = Math.PI / 4

            // 화살표 머리 그리기
            path.moveTo(
                endX - arrowSize * kotlin.math.cos(angle - arrowAngle).toFloat(),
                endY - arrowSize * kotlin.math.sin(angle - arrowAngle).toFloat()
            )
            path.lineTo(endX, endY)
            path.lineTo(
                endX - arrowSize * kotlin.math.cos(angle + arrowAngle).toFloat(),
                endY - arrowSize * kotlin.math.sin(angle + arrowAngle).toFloat()
            )

            // 화살표 애니메이션 효과
            arrowPaint.alpha = ((kotlin.math.sin(arrowPhase * kotlin.math.PI * 2) + 1) * 127.5f).toInt()

            canvas.drawPath(path, arrowPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        arrowAnimator.cancel()
    }

    enum class Direction {
        LEFT, RIGHT, UP, DOWN
    }
}