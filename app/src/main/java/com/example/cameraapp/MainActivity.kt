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
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.cameraapp.analyzer.BodyAnalyzer
import com.example.cameraapp.analyzer.MultiFaceAnalyzer
import com.example.cameraapp.ui.theme.CameraAppTheme
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.example.cameraapp.analyzer.PoseComparator
import com.example.cameraapp.model.PoseLandmark
import com.example.cameraapp.model.Referencepose
import com.example.cameraapp.ui.theme.overlay.PoseSuggestionView
import com.google.mediapipe.formats.proto.LandmarkProto


// MainActivity class
class MainActivity : ComponentActivity() {
    // Declare imageCapture instance for capturing photos
    private var imageCapture: ImageCapture? = null
    // Declare analyzers for body pose and face detection
    private lateinit var bodyAnalyzer: BodyAnalyzer
    private lateinit var multiFaceAnalyzer: MultiFaceAnalyzer
    private lateinit var poseComparator: PoseComparator
    private lateinit var referencePose: Referencepose
    private lateinit var poseSuggestionView: PoseSuggestionView

    // Request permissions at runtime
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // Permission granted
        } else {
            Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // onCreate method
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // JSON 파일 로드
        val jsonString = assets.open("reference_pose.json").bufferedReader().use { it.readText() }
        referencePose = Gson().fromJson(jsonString, Referencepose::class.java)

        poseComparator = PoseComparator()
        poseSuggestionView = PoseSuggestionView(this)


        // Initialize analyzers for pose and face detection
        bodyAnalyzer = BodyAnalyzer(this) { result: PoseLandmarkerResult, mpImage: MPImage ->
            handlePoseResult(result, mpImage)
        }
        multiFaceAnalyzer = MultiFaceAnalyzer(this)

        // Check permissions and request if not granted
        if (!allPermissionsGranted()) {
            requestPermissions()
        }

        // Set up the UI
        setContent {
            CameraAppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Display the camera preview
                    CameraPreview(
                        imageCapture = { capture -> imageCapture = capture },
                        processImage = { imageProxy -> processImage(imageProxy) }
                    )
                    // Button for capturing a photo
                    Button(
                        onClick = { takePhoto() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    ) {
                        Text("사진 촬영")
                    }
                }
            }
        }
    }

    // Check if all necessary permissions are granted
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // Request necessary permissions
    private fun requestPermissions() {
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    // CameraPreview composable function
    @Composable
    private fun CameraPreview(
        imageCapture: (ImageCapture) -> Unit,
        processImage: (ImageProxy) -> Unit
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current

        // Camera preview setup
        Box(modifier = Modifier.fillMaxSize()) {
            val previewView = remember {
                PreviewView(context).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            }

            // Launch camera preview setup
            LaunchedEffect(previewView) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // Setup preview and camera selector
                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    // Setup image capture use case
                    val imageCaptureUseCase = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    // Pass imageCapture instance to callback
                    imageCapture(imageCaptureUseCase)

                    // Setup image analyzer
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                        .also {
                            it.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                                processImage(imageProxy)
                            }
                        }

                    try {
                        // Bind camera to lifecycle
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCaptureUseCase,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            // Display the previewView
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay for face and body pose drawing
            val overlayView = remember {
                object : android.view.View(context) {
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
            }

            // Display the overlay view
            AndroidView(
                factory = { overlayView },
                modifier = Modifier.fillMaxSize()
            )

            AndroidView(
                factory = { poseSuggestionView },
                modifier = Modifier.fillMaxSize()
            )

            // Continuously update the overlay for rendering
            LaunchedEffect(Unit) {
                while(true) {
                    withContext(Dispatchers.Main) {
                        overlayView.invalidate()
                    }
                    delay(16) // approx 60 FPS
                }
            }
        }
    }

    // Process the image from the camera
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        try {
            val bitmap = imageProxy.toBitmap() // Convert to bitmap
            val mpImage = BitmapImageBuilder(bitmap).build() // Convert bitmap to MediaPipe image
            bodyAnalyzer.detectPose(mpImage) // Detect body pose
            multiFaceAnalyzer.detectFaces(mpImage) // Detect faces
        } catch (e: Exception) {
            Log.e(TAG, "Image processing failed", e)
        } finally {
            imageProxy.close() // Close the image proxy after processing
        }
    }

    // Extension function to convert ImageProxy to Bitmap
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

    private val smoothingFactor = 0.8f  // Adjust between 0 (more smoothing) and 1 (less smoothing)
    private var previousLandmarks: List<PoseLandmark>? = null



    private fun smoothLandmarks(currentLandmarks: List<PoseLandmark>): List<PoseLandmark> {
        if (previousLandmarks == null) {
            previousLandmarks = currentLandmarks
            return currentLandmarks
        }

        return currentLandmarks.mapIndexed { index, current ->
            val previous = previousLandmarks!![index]
            PoseLandmark(
                x = smoothingFactor * previous.x + (1 - smoothingFactor) * current.x,
                y = smoothingFactor * previous.y + (1 - smoothingFactor) * current.y
                // If Z is not available, omit it
            )
        }.also {
            previousLandmarks = it
        }
    }

    private fun smoothLandmarks(currentLandmarks: List<LandmarkProto.NormalizedLandmark>): List<PoseLandmark> {
        val poseLandmarks = currentLandmarks.map {
            PoseLandmark(x = it.x(), y = it.y())  // Assuming PoseLandmark has a similar constructor
        }

        if (previousLandmarks == null) {
            previousLandmarks = poseLandmarks
            return poseLandmarks
        }

        return poseLandmarks.mapIndexed { index, current ->
            val previous = previousLandmarks!![index]
            PoseLandmark(
                x = smoothingFactor * previous.x + (1 - smoothingFactor) * current.x,
                y = smoothingFactor * previous.y + (1 - smoothingFactor) * current.y
            )
        }.also {
            previousLandmarks = it
        }
    }



    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Set the photo file name with current time
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
            }
        }

        // Set output options for saving the image
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        // Capture the photo and save it
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "사진 촬영 실패: ${exc.message}", exc)
                    Toast.makeText(baseContext, "사진 촬영 실패", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "사진 저장 완료: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    // Clean up resources onDestroy
    override fun onDestroy() {
        super.onDestroy()
        bodyAnalyzer.close()
        multiFaceAnalyzer.close()
    }

    companion object {
        private const val TAG = "CameraApp" // Log tag
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS" // Filename format for images
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA) // Permissions required for camera
    }
}
