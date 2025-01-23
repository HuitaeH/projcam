package com.example.cameraapp.analyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class BodyAnalyzer(
    context: Context,
    private val onPoseDetected: (PoseLandmarkerResult, MPImage) -> Unit
) {
    private var poseLandmarker: PoseLandmarker
    var lastResult: PoseLandmarkerResult? = null
    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(3)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.3f)
            .setResultListener { result: PoseLandmarkerResult, mpImage: MPImage ->
                lastResult = result
                onPoseDetected(result, mpImage)
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detectPose(image: MPImage) {
        try {
            val timestampMs = System.currentTimeMillis()
            poseLandmarker.detectAsync(image, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Pose detection failed", e)
        }
    }

    fun drawPose(canvas: Canvas, landmarks: List<NormalizedLandmark>) {
        val rect = Rect()
        canvas.getClipBounds(rect)
        val topMargin = rect.height() * 0.007f  // 1.3/6 비율로 상단 여백 계산

        canvas.save()
        canvas.clipRect(0f, topMargin, rect.width().toFloat(), rect.height().toFloat())

//        // 랜드마크 점 그리기
//        for (landmark in landmarks) {
//            canvas.drawCircle(
//                (1 - landmark.y()) * canvas.width,
//                landmark.x() * canvas.height,
//                8f,
//                paint
//            )
//        }

//        // 연결선 그리기
//        drawConnections(canvas, landmarks)

        canvas.restore()
    }

    private fun drawConnections(canvas: Canvas, landmarks: List<NormalizedLandmark>) {  // NormalizedLandmark로 변경
        // 몸통
        drawLine(canvas, landmarks[11], landmarks[12]) // 어깨
        drawLine(canvas, landmarks[11], landmarks[23]) // 왼쪽 몸통
        drawLine(canvas, landmarks[12], landmarks[24]) // 오른쪽 몸통
        drawLine(canvas, landmarks[23], landmarks[24]) // 힙

        // 팔
        drawLine(canvas, landmarks[11], landmarks[13]) // 왼팔 상부
        drawLine(canvas, landmarks[13], landmarks[15]) // 왼팔 하부
        drawLine(canvas, landmarks[12], landmarks[14]) // 오른팔 상부
        drawLine(canvas, landmarks[14], landmarks[16]) // 오른팔 하부

        // 다리
        drawLine(canvas, landmarks[23], landmarks[25]) // 왼쪽 다리 상부
        drawLine(canvas, landmarks[25], landmarks[27]) // 왼쪽 다리 하부
        drawLine(canvas, landmarks[24], landmarks[26]) // 오른쪽 다리 상부
        drawLine(canvas, landmarks[26], landmarks[28]) // 오른쪽 다리 하부
    }

    private fun drawLine(canvas: Canvas, start: NormalizedLandmark, end: NormalizedLandmark) {
        canvas.drawLine(
            (1 - start.y()) * canvas.width,    // start의 y 좌표를 x로 변환
            start.x() * canvas.height,         // start의 x 좌표를 y로 변환
            (1 - end.y()) * canvas.width,      // end의 y 좌표를 x로 변환
            end.x() * canvas.height,           // end의 x 좌표를 y로 변환
            paint
        )
    }

    fun close() {
        poseLandmarker.close()
    }

    companion object {
        private const val TAG = "BodyAnalyzer"
    }
}