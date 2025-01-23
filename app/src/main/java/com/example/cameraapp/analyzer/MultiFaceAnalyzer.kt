package com.example.cameraapp.analyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class MultiFaceAnalyzer(context: Context) {
    private var faceLandmarker: FaceLandmarker
    var lastResult: FaceLandmarkerResult? = null
    private var selectedFaceIndex: Int? = null
    private val faceBoxes = mutableListOf<RectF>()
    private var lastProcessingTimeMs = 0L
    private val MIN_TIME_BETWEEN_FRAMES_MS = 16L


    private val landmarkPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        strokeWidth = 4f
        alpha = 200
    }

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(3)
            .setMinFaceDetectionConfidence(0.5f)    // 얼굴 감지 신뢰도 임계값 낮춤
            .setMinFacePresenceConfidence(0.5f)     // 얼굴 존재 신뢰도 임계값 낮춤
            .setMinTrackingConfidence(0.5f)         // 추적 신뢰도 임계값 낮춤
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result: FaceLandmarkerResult, image: MPImage ->
                lastResult = result
                updateFaceBoxes(result)
                //Log.d(TAG, "Detected faces: ${result.faceLandmarks().size}")
            }
            .setErrorListener { error: RuntimeException ->
                //Log.e(TAG, "Face detection failed: ${error.message}")
            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun detectFaces(image: MPImage) {
        val currentTimeMs = System.currentTimeMillis()
        if (currentTimeMs - lastProcessingTimeMs < MIN_TIME_BETWEEN_FRAMES_MS) {
            return
        }

        try {
            faceLandmarker.detectAsync(image, currentTimeMs)
            lastProcessingTimeMs = currentTimeMs
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed", e)
        }
    }

    private fun updateFaceBoxes(result: FaceLandmarkerResult) {
        faceBoxes.clear()
        result.faceLandmarks().forEachIndexed { index, landmarks ->
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            landmarks.forEach { landmark ->
                minX = minOf(minX, 1 - landmark.y())  // y 좌표를 x로 변환
                minY = minOf(minY, landmark.x())      // x 좌표를 y로 변환
                maxX = maxOf(maxX, 1 - landmark.y())  // y 좌표를 x로 변환
                maxY = maxOf(maxY, landmark.x())      // x 좌표를 y로 변환
            }

            // 경계 상자 여유 공간을 더 크게
            val paddingX = 0.1f  // 좌우 여유 공간 증가
            val paddingY = 0.1f  // 상하 여유 공간 증가



            faceBoxes.add(RectF(
                (minX - paddingX).coerceIn(0f, 1f),
                (minY - paddingY).coerceIn(0f, 1f),
                (maxX + paddingX).coerceIn(0f, 1f),
                (maxY + paddingY).coerceIn(0f, 1f)
            ))


//            Log.d(TAG, "Face $index bounds: left=$minX, top=$minY, right=$maxX, bottom=$maxY")
        }
    }

    fun drawFaces(canvas: Canvas) {
//        faceBoxes.forEachIndexed { index, box ->
//            boxPaint.color = if (index == selectedFaceIndex) Color.RED else Color.GREEN
//            canvas.drawRect(
//                box.left * canvas.width,      // x 좌표
//                box.top * canvas.height,      // y 좌표
//                box.right * canvas.width,     // x 좌표
//                box.bottom * canvas.height,   // y 좌표
//                boxPaint
//            )
//
////            lastResult?.faceLandmarks()?.getOrNull(index)?.forEach { landmark ->
////                canvas.drawCircle(
////                    (1 - landmark.y()) * canvas.width,  // x 좌표를 y로 변환
////                    canvas.height - (landmark.x() * canvas.height),  // flip y 좌표 (upside down)
////                    3f,
////                    landmarkPaint
////                )
////            }
//        }
    }

    fun selectFace(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        val normalizedX = x / viewWidth
        val normalizedY = y / viewHeight

        selectedFaceIndex = faceBoxes.indexOfFirst { box ->
            normalizedX >= box.left && normalizedX <= box.right &&
                    normalizedY >= box.top && normalizedY <= box.bottom
        }.takeIf { it >= 0 }

//        Log.d(TAG, "Selected face index: $selectedFaceIndex")
    }

    fun getSelectedFaceBounds(): RectF? {
        return selectedFaceIndex?.let { faceBoxes.getOrNull(it) }
    }

    fun close() {
        faceLandmarker.close()
    }

    companion object {
        private const val TAG = "MultiFaceAnalyzer"
    }
}