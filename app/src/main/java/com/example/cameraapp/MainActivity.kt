package com.example.cameraapp

import com.google.gson.Gson
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.Button
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.remember
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalLifecycleOwner
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.cameraapp.analyzer.BodyAnalyzer
import com.example.cameraapp.analyzer.MultiFaceAnalyzer
//import com.example.cameraapp.ui.theme.CameraAppTheme
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.withContext
import com.example.cameraapp.analyzer.PoseComparator
import com.example.cameraapp.model.ReferencePoints
import com.example.cameraapp.ui.theme.overlay.PoseSuggestionView
//import androidx.compose.material3.ButtonDefaults  // 이 import 추가
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Loop
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.TextStyle
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.ui.unit.sp
//import androidx.compose.material3.Icon
import androidx.camera.core.Camera
//import androidx.camera.core.CameraControl
//import android.view.ViewGroup
//import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.os.HandlerCompat.postDelayed
import android.content.Intent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.example.cameraapp.analyzer.FacialComparator
import com.example.cameraapp.model.ReferenceFace
import com.example.cameraapp.ui.theme.overlay.FaceSuggestionView

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "CameraApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private var imageCapture: ImageCapture? = null
    private lateinit var viewFinder: PreviewView
    private lateinit var camera: Camera
    private lateinit var bodyAnalyzer: BodyAnalyzer
    private lateinit var multiFaceAnalyzer: MultiFaceAnalyzer
    private lateinit var poseComparator: PoseComparator
    private lateinit var facialComparator: FacialComparator
    private lateinit var referenceCom: ReferencePoints
    private lateinit var referencePose: ReferencePoints
    private lateinit var referenceFace: ReferenceFace
    private lateinit var poseSuggestionView: PoseSuggestionView
    private lateinit var faceSuggestionView: FaceSuggestionView
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (permission, isGranted) ->
            Log.d(TAG, "Permission result: $permission = $isGranted")
        }

        if (permissions.all { it.value }) {
            Log.d(TAG, "All permissions granted, binding camera")
            bindCameraUseCases()
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Log.e(TAG, "Permissions denied: $deniedPermissions")
            Toast.makeText(this, "Camera and storage permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)

        loadReferenceData()
        initializeOverlays()

        bodyAnalyzer = BodyAnalyzer(this) { result, mpImage ->
            handlePoseResult(result, mpImage)
        }
        multiFaceAnalyzer = MultiFaceAnalyzer(this)

        if (allPermissionsGranted()) {
            bindCameraUseCases()
        } else {
            requestPermissions()
        }

        setupOverlayView()
        setupCameraZoom()

        findViewById<ImageButton>(R.id.camera_capture_button).setOnClickListener { takePhoto() }
        findViewById<ImageButton>(R.id.camera_switch_button).setOnClickListener { toggleCamera() }
        findViewById<ImageButton>(R.id.gallery_button).setOnClickListener { openGallery() }
    }

    private fun loadReferenceData() {
        try {
            val jsonCOM = assets.open("half_average_com.json").bufferedReader().use { it.readText() }
            val jsonPose = assets.open("half_average_posture.json").bufferedReader().use { it.readText() }
            referenceCom = Gson().fromJson(jsonCOM, ReferencePoints::class.java)
            referencePose = Gson().fromJson(jsonPose, ReferencePoints::class.java)
            poseComparator = PoseComparator(referencePose, referenceCom)

            val jsonFace = assets.open("face_average.json").bufferedReader().use { it.readText() }
            referenceFace = Gson().fromJson(jsonFace, ReferenceFace::class.java)
            facialComparator = FacialComparator(referenceFace)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading JSON files", e)
        }
    }

    private fun initializeOverlays() {
        poseSuggestionView = PoseSuggestionView(this).apply {
            visibility = View.VISIBLE
        }
        faceSuggestionView = FaceSuggestionView(this).apply {
            visibility = View.GONE
        }

        findViewById<FrameLayout>(R.id.overlay_container).apply {
            addView(poseSuggestionView)
            addView(faceSuggestionView)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
        startActivity(intent)
    }

    private fun toggleCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        updateComparatorsForCamera()
        bindCameraUseCases()
    }

    private fun updateComparatorsForCamera() {
        if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            poseSuggestionView.visibility = View.GONE
            faceSuggestionView.visibility = View.VISIBLE
            faceSuggestionView.invalidate() // Clears the face suggestion overlay

        } else {
            faceSuggestionView.visibility = View.GONE
            poseSuggestionView.visibility = View.VISIBLE
            poseSuggestionView.invalidate() // Clears the pose suggestion overlay

        }
    }

    private fun setupCameraZoom() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                camera.cameraControl.setZoomRatio(currentZoomRatio * detector.scaleFactor)
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private fun setupOverlayView() {
        val overlayView = object : View(this) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                multiFaceAnalyzer.drawFaces(canvas)
                if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    bodyAnalyzer.lastResult?.landmarks()?.firstOrNull()?.let { landmarks ->
                        bodyAnalyzer.drawPose(canvas, landmarks)
                    }
                }
            }
        }.apply {
            setWillNotDraw(false)
        }

        findViewById<FrameLayout>(R.id.overlay_container).addView(overlayView)

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                overlayView.invalidate()
                handler.postDelayed(this, 16L)
            }
        }
        handler.postDelayed(runnable, 16L)
    }

    private fun bindCameraUseCases() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                val imageCaptureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val imageAnalyzerUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, currentCameraSelector, previewUseCase, imageCaptureUseCase, imageAnalyzerUseCase
                )
                imageCapture = imageCaptureUseCase
            } catch (e: Exception) {
                Log.e(TAG, "Camera use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        val isGranted = ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Permission $it granted: $isGranted")
        isGranted
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()

            if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                multiFaceAnalyzer.detectFaces(mpImage)
                // Handle face landmarks and comparison
                val landmarks = multiFaceAnalyzer.lastResult?.faceLandmarks()?.firstOrNull()
                landmarks?.let {
                    val result = facialComparator.compareFace(
                        it,
                        referenceFace,
                        mpImage.width,
                        mpImage.height,
                        true
                    )
                    runOnUiThread {
                        faceSuggestionView.updateResult(result)
                    }
                }
            } else {
                bodyAnalyzer.detectPose(mpImage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image processing failed", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun handlePoseResult(result: PoseLandmarkerResult, mpImage: MPImage) {
        result.landmarks().firstOrNull()?.let { landmarks ->
            val comparisonResult = poseComparator.comparePose(
                landmarks,
                referencePose,
                mpImage.width,
                mpImage.height
            )
            runOnUiThread {
                poseSuggestionView.updateResult(comparisonResult)
            }
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val byteArray = out.toByteArray()
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(baseContext, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo saved: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        bodyAnalyzer.close()
        multiFaceAnalyzer.close()
    }
}
