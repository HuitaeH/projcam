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
    private var lastResult: FaceLandmarkerResult? = null
    private var selectedFaceIndex: Int? = null
    private val faceBoxes = mutableListOf<RectF>()

    private val landmarkPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        strokeWidth = 2f
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
            .setNumFaces(5)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result: FaceLandmarkerResult, image: MPImage ->
                lastResult = result
                updateFaceBoxes(result)
            }
            .setErrorListener { error: RuntimeException ->
                Log.e(TAG, "Face detection failed: ${error.message}")
            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun detectFaces(image: MPImage) {
        try {
            faceLandmarker.detectAsync(image, System.currentTimeMillis())
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
                minX = minOf(minX, landmark.x())
                minY = minOf(minY, landmark.y())
                maxX = maxOf(maxX, landmark.x())
                maxY = maxOf(maxY, landmark.y())
            }

            faceBoxes.add(RectF(minX, minY, maxX, maxY))
        }
    }

    fun drawFaces(canvas: Canvas) {
        faceBoxes.forEachIndexed { index, box ->
            boxPaint.color = if (index == selectedFaceIndex) Color.RED else Color.GREEN
            canvas.drawRect(
                box.left * canvas.width,
                box.top * canvas.height,
                box.right * canvas.width,
                box.bottom * canvas.height,
                boxPaint
            )

            lastResult?.faceLandmarks()?.getOrNull(index)?.forEach { landmark ->
                canvas.drawCircle(
                    landmark.x() * canvas.width,
                    landmark.y() * canvas.height,
                    3f,
                    landmarkPaint
                )
            }
        }
    }

    fun selectFace(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        val normalizedX = x / viewWidth
        val normalizedY = y / viewHeight

        selectedFaceIndex = faceBoxes.indexOfFirst { box ->
            normalizedX >= box.left && normalizedX <= box.right &&
                    normalizedY >= box.top && normalizedY <= box.bottom
        }.takeIf { it >= 0 }

        Log.d(TAG, "Selected face index: $selectedFaceIndex")
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