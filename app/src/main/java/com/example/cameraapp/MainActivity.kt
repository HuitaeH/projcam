package com.example.cameraapp

import com.google.gson.Gson
import android.Manifest
import android.content.ContentValues
import android.content.ContentValues.TAG
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.cameraapp.analyzer.BodyAnalyzer
import com.example.cameraapp.analyzer.MultiFaceAnalyzer
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.example.cameraapp.analyzer.PoseComparator
import com.example.cameraapp.analyzer.FacialComparator
import com.example.cameraapp.model.ReferencePoints
import com.example.cameraapp.ui.theme.overlay.PoseSuggestionView
import com.example.cameraapp.ui.theme.overlay.FaceSuggestionView // Import FaceSuggestionView
import androidx.camera.core.Camera
import com.example.cameraapp.model.ReferenceFace
import android.widget.FrameLayout
import android.widget.ImageButton
import android.content.Intent
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class MainActivity : ComponentActivity() {
    // Add the companion object here
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private var imageCapture: ImageCapture? = null
    private lateinit var viewFinder: PreviewView

    private lateinit var previewView: PreviewView // PreviewView를 클래스 변수로 선언
    private lateinit var camera: Camera // 카메라 객체 추가
    private lateinit var bodyAnalyzer: BodyAnalyzer
    private lateinit var multiFaceAnalyzer: MultiFaceAnalyzer
    private lateinit var poseComparator: PoseComparator
    private lateinit var facialComparator: FacialComparator
    private lateinit var referenceCom: ReferencePoints
    private lateinit var referencePose: ReferencePoints
    private lateinit var referenceFace: ReferenceFace
    private lateinit var poseSuggestionView: PoseSuggestionView
    private lateinit var faceSuggestionView: FaceSuggestionView // Declare FaceSuggestionView
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA // 기본은 후면 카메라
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // 권한이 승인됨
            bindCameraUseCases() // 권한이 승인된 후 카메라 바인딩
        } else {
            Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
        }
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Ensure the layout is set first

        viewFinder = findViewById(R.id.viewFinder) // Proper initialization

        // PreviewView 초기화
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }

        // JSON 파일 로드
        try {
            val jsonCOM = assets.open("half_average_com.json").bufferedReader().use { it.readText() }
            val jsonPose = assets.open("half_average_posture.json").bufferedReader().use { it.readText() }
            referenceCom = Gson().fromJson(jsonCOM, ReferencePoints::class.java)
            referencePose = Gson().fromJson(jsonPose, ReferencePoints::class.java)
            poseComparator = PoseComparator(referencePose, referenceCom)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading JSON files", e)
        }

        // JSON for facial comparator
        try {
            val jsonFace = assets.open("face_average.json").bufferedReader().use { it.readText() }
            referenceFace = Gson().fromJson(jsonFace, ReferenceFace::class.java)
            facialComparator = FacialComparator(referenceFace)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading JSON files", e)
        }

        // Initialize PoseSuggestionView for back camera
        poseSuggestionView = PoseSuggestionView(this).apply {
            visibility = View.VISIBLE // Default visibility for back camera
        }

        // Initialize FaceSuggestionView for front camera
        faceSuggestionView = FaceSuggestionView(this).apply {
            visibility = View.GONE // Default visibility for front camera
        }

        // Add both views to overlay container
        findViewById<FrameLayout>(R.id.overlay_container).apply {
            addView(poseSuggestionView)
            addView(faceSuggestionView)
        }

        poseSuggestionView = PoseSuggestionView(this)
        bodyAnalyzer = BodyAnalyzer(this) { result: PoseLandmarkerResult, mpImage: MPImage ->
            handlePoseResult(result, mpImage)
        }
        multiFaceAnalyzer = MultiFaceAnalyzer(this)

        val overlayContainer = findViewById<FrameLayout>(R.id.overlay_container)
        if (overlayContainer == null) {
            Log.e("ERROR", "overlay_container is null")
        } else {
            overlayContainer.addView(View(this))
        }

        if (allPermissionsGranted()) {
            bindCameraUseCases()
        } else {
            requestPermissions()
        }
        setupOverlayView()

        val galleryButton: ImageButton = findViewById(R.id.gallery_button)
        galleryButton.setOnClickListener { openGallery() }
    }

    private fun handlePoseResult(result: PoseLandmarkerResult, mpImage: MPImage) {
        val landmarks = result.poseLandmarks()
        if (landmarks != null) {
            // Comparing pose with reference pose
            poseComparator.comparePose(landmarks)

            // Updating suggestions on PoseSuggestionView
            poseSuggestionView.updateSuggestions(landmarks)
        }
    }
    // Handle facial landmarks and provide suggestions based on facial analysis
    private fun handleFacialResult(facialLandmarks: List<Landmark>?) {
        if (facialLandmarks != null) {
            facialComparator.compareFace(facialLandmarks)

            // Update FaceSuggestionView
            faceSuggestionView.updateSuggestions(facialLandmarks)
        }
    }


    private fun toggleCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Switch comparators based on the selected camera
        if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            // Front camera: use FaceSuggestionView
            poseSuggestionView.visibility = View.GONE
            faceSuggestionView.visibility = View.VISIBLE // Show faceSuggestionView
            facialComparator = FacialComparator(referenceFace)  // Use facial comparator for front camera
        } else {
            // Back camera: use PoseSuggestionView
            faceSuggestionView.visibility = View.GONE
            poseSuggestionView.visibility = View.VISIBLE // Show poseSuggestionView
            poseComparator = PoseComparator(referencePose, referenceCom)  // Use pose comparator for back camera
        }

        bindCameraUseCases() // Re-bind camera use cases
    }

    private fun setupCameraZoom() {
        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val camera = camera
                    val currentZoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val delta = detector.scaleFactor
                    camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                    return true
                }
            })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
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
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }

                // 기존 바인딩 해제
                cameraProvider.unbindAll()

                // 새 카메라 설정 및 바인딩
                camera = cameraProvider.bindToLifecycle(
                    this,
                    currentCameraSelector,
                    previewUseCase,
                    imageCaptureUseCase,
                    imageAnalyzerUseCase
                )
                imageCapture = imageCaptureUseCase
            } catch (e: Exception) {
                Log.e(TAG, "Camera use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun setupOverlayView() {
        val overlayView = object : View(this) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                multiFaceAnalyzer.drawFaces(canvas)
                bodyAnalyzer.lastResult?.landmarks()?.firstOrNull()?.let { landmarks ->
                    bodyAnalyzer.drawPose(canvas, landmarks)
                }
            }
        }.apply {
            setWillNotDraw(false)
        }

        findViewById<FrameLayout>(R.id.overlay_container).addView(overlayView)

        // 60fps 갱신
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                overlayView.invalidate()
                handler.postDelayed(this, 16L)
            }
        }
        handler.postDelayed(runnable, 16L)
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            bodyAnalyzer.detectPose(mpImage)
            multiFaceAnalyzer.detectFaces(mpImage)
        } catch (e: Exception) {
            Log.e(TAG, "Image processing failed", e)
        } finally {
            imageProxy.close()
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
        val byteArray = out.toByteArray
