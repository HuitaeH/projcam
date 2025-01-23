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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import com.example.cameraapp.analyzer.PoseComparator
import com.example.cameraapp.analyzer.FacialComparator
import com.example.cameraapp.model.ReferencePoints
import com.example.cameraapp.ui.theme.overlay.PoseSuggestionView
import androidx.compose.material3.ButtonDefaults  // 이 import 추가
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Loop
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.camera.core.Camera
import android.view.ViewGroup
import android.view.View

class MainActivity_copy : ComponentActivity() {
    private var imageCapture: ImageCapture? = null

    private lateinit var previewView: PreviewView // PreviewView를 클래스 변수로 선언
    private lateinit var camera: Camera // 카메라 객체 추가
    private lateinit var bodyAnalyzer: BodyAnalyzer
    private lateinit var multiFaceAnalyzer: MultiFaceAnalyzer
    private lateinit var poseComparator: PoseComparator
    private lateinit var facialComparator: FacialComparator
    private lateinit var referenceCom: ReferencePoints
    private lateinit var referencePose: ReferencePoints
    private lateinit var poseSuggestionView: PoseSuggestionView
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA // 기본은 후면 카메라

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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




        poseSuggestionView = PoseSuggestionView(this)

        bodyAnalyzer = BodyAnalyzer(this) { result: PoseLandmarkerResult, mpImage: MPImage ->
            handlePoseResult(result, mpImage)
        }
        multiFaceAnalyzer = MultiFaceAnalyzer(this)

        if (allPermissionsGranted()) {
            bindCameraUseCases()
        } else {
            requestPermissions()
        }

        setContent {
            CameraAppTheme {
                CameraScreen(
                    previewView = previewView,
                    onTakePhoto = { takePhoto() },
                    onToggleCamera = { toggleCamera() }
                )
            }
        }
    }

    private fun toggleCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        bindCameraUseCases() // 카메라 재설정
    }

    private fun bindCameraUseCases() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
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

    @Composable
    fun CameraScreen(
        previewView: PreviewView,
        onTakePhoto: () -> Unit,
        onToggleCamera: () -> Unit
    ) {
        val context = LocalContext.current  // context 정의 추가
        Box(modifier = Modifier
                .fillMaxSize()
            .background(Color.Black)
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,

        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .background(Color.Black) // 검정색 배경
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)  // 3:4 비율 설정
                    .background(Color.Black)

            ) {

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .aspectRatio(3f / 4f)

                ) {

                    // 카메라 프리뷰
                    AndroidView(
                        factory = {
                            // 부모 뷰가 있으면 제거
                            previewView.parent?.let { parent ->
                                (parent as? ViewGroup)?.removeView(previewView)
                            }
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 오버레이 추가
                    val overlayView = remember {
                        object : View(context) {
                            override fun onDraw(canvas: android.graphics.Canvas) {
                                super.onDraw(canvas)
                                multiFaceAnalyzer.drawFaces(canvas)
                                bodyAnalyzer.lastResult?.landmarks()?.firstOrNull()
                                    ?.let { landmarks ->
                                        bodyAnalyzer.drawPose(canvas, landmarks)
                                    }
                            }
                        }.apply {
                            setWillNotDraw(false)
                        }
                    }

                    AndroidView(
                        factory = { overlayView },
                        modifier = Modifier.fillMaxSize()
                    )

                    AndroidView(
                        factory = { poseSuggestionView },
                        modifier = Modifier.fillMaxSize()
                    )


                    // 오버레이 갱신
                    LaunchedEffect(overlayView) {
                        while (true) {
                            overlayView.invalidate()
                            delay(16) // 약 60fps
                        }
                    }
                }
            }

            //Spacer(modifier = Modifier.height(16.dp)) // 프리뷰와 버튼 사이 여백
            // 하단 검정색 공백 및 버튼 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.8f) // 남은 공간을 차지
                    .background(Color.Black), // 검정색 배경
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    //horizontalArrangement = Arrangement.Center, // 버튼 간격 조정
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // 왼쪽 Spacer (중앙 정렬을 위한 여백)
                    Spacer(modifier = Modifier.weight(1.5f))

                    // 사진 촬영 버튼
                    Button(
                        onClick = onTakePhoto,
                        modifier = Modifier.size(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        )
                    ) {

                    }

                    // 오른쪽 Spacer (셀카 모드 전환 버튼 간격)
                    Spacer(modifier = Modifier.weight(0.8f))

                    // 셀카 모드 전환 버튼
                    Button(
                        onClick = onToggleCamera,
                        modifier = Modifier.size(45.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray,
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp) // 기본 패딩 제거
                    ) {
                        Icon(
                            imageVector = Icons.Default.Loop,
                            contentDescription = "Flip Camera",
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }
        }

        }
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
        val byteArray = out.toByteArray()
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    private fun handlePoseResult(result: PoseLandmarkerResult, mpImage: MPImage) {
        result.landmarks().firstOrNull()?.let { landmarks ->

            val imageWidth = mpImage.width
            val imageHeight = mpImage.height

            // 포즈 비교 실행
            val comparisonResult = poseComparator.comparePose(
                landmarks,
                referencePose,
                imageWidth,
                imageHeight
            )

            // UI 업데이트
            runOnUiThread {
                poseSuggestionView.updateResult(comparisonResult)
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
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

    override fun onDestroy() {
        super.onDestroy()
        bodyAnalyzer.close()
        multiFaceAnalyzer.close()
    }

    companion object {
        private const val TAG = "CameraApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
