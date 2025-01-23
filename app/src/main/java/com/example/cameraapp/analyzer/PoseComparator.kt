package com.example.cameraapp.analyzer

import android.util.Log
import com.example.cameraapp.model.*
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.pow

// app/kotlin+java/com.example.cameraapp/analyzer/PoseComparator.kt
class PoseComparator( private val referencePose: ReferencePoints,
                      private val referenceCom: ReferencePoints) {
    companion object {
        private const val TAG = "PoseComparator"
        private const val POSITION_THRESHOLD = 0.1f
        private const val CRITICAL_THRESHOLD = 0.15f

    }

    private var currentCenterX = 0f
    private var currentCenterY = 0f
    private var isLeftHeavy = false
    private var isRightHeavy = false
    private var isTopHeavy = false
    private var isBottomHeavy = false

    // 복구된 정규화 값 계산
    private fun restoreNormalizedValues(key: String): Pair<Float, Float> {
        val normalizedX = referencePose.average_pose[key]?.x?.plus(referenceCom.average_com?.x ?: 0f) ?: 0f
        val normalizedY = referencePose.average_pose[key]?.y?.plus(referenceCom.average_com?.y ?: 0f) ?: 0f
        return Pair(normalizedX, normalizedY)
    }



    // Access values from average_com and average_pose
    val comX = referenceCom.average_com?.x ?: 0f // Safe access with a default value of 0f if null
    val comY = referenceCom.average_com?.y ?: 0f // Same for y value

    // Using safe calls for average_pose values and providing default values

    val lshoulderDataX = referencePose.average_pose["LEFT_SHOULDER"]?.x?.plus(comX) ?: comX
    val rshoulderDataX = referencePose.average_pose["RIGHT_SHOULDER"]?.x?.plus(comX) ?: comX
    val lshoulderDataY = referencePose.average_pose["LEFT_SHOULDER"]?.y?.plus(comY) ?: comY
    val rshoulderDataY = referencePose.average_pose["RIGHT_SHOULDER"]?.y?.plus(comY) ?: comY
    // Corrected calculation for hip data (using X with X, and Y with Y)
    val lhipDataX = referencePose.average_pose["LEFT_HIP"]?.x?.plus(comX) ?: comX
    val rhipDataX = referencePose.average_pose["RIGHT_HIP"]?.x?.plus(comX) ?: comX
    val lhipDataY = referencePose.average_pose["LEFT_HIP"]?.y?.plus(comY) ?: comY
    val rhipDataY = referencePose.average_pose["RIGHT_HIP"]?.y?.plus(comY) ?: comY



    val noseDataX = referencePose.average_pose["NOSE"]?.x?.plus(comX) ?: comX
    val noseDataY = referencePose.average_pose["NOSE"]?.y?.plus(comY) ?: comY
    // Corrected averaging for shoulder
    val shoulderDataX = (lshoulderDataX + rshoulderDataX) / 2
    val shoulderDataY = (lshoulderDataY + rshoulderDataY) / 2
    // Corrected averaging for hip
    val hipDataX = (lhipDataX + rhipDataX) / 2
    val hipDataY = (lhipDataY + rhipDataY) / 2



    // 마지막 로그 출력 시간을 저장
    private var lastLogTime = 0L

    // 포즈 비교 결과를 담는 데이터 클래스들
    data class ComparisonResult(
        val overallScore: Float,           // 전체 유사도 점수 (0~100)
        val positionScore: Float,          // 자세 점수
        val centerScore: Float,            // 구도 점수
        val suggestions: List<String>,     // 개선을 위한 제안사항
        val detailedScores: Map<String, Float>,  // 각 부위별 유사도 점수
        val isLeftHeavy: Boolean = false,
        val isRightHeavy: Boolean = false,
        val isTopHeavy: Boolean = false,
        val isBottomHeavy: Boolean = false,
        val currentComX: Float = 0f,
        val currentComY: Float = 0f,
        val targetComX: Float = 0f,
        val targetComY: Float = 0f,
        val zoomSuggestion: String = "Perfect Zoom" // 추가: Zoom 상태 가이드
    )




    fun comparePose(
        currentLandmarks: List<NormalizedLandmark>,
        referencePose: ReferencePoints,
        imageWidth: Int,
        imageHeight: Int,
        //shouldLog: Boolean
    ): ComparisonResult {

        val zoomSuggestion = determineZoomState(currentLandmarks, imageWidth, imageHeight)

        val differences = mutableMapOf<String, Float>()
        val suggestions = mutableListOf<String>()

        // 1. 기본 자세 비교
        differences["NOSE"] = comparePosition(
            currentLandmarks[0],
            referencePose.average_pose["NOSE"]!!
        )

        // 어깨
        val leftShoulderDiff = comparePosition(
            currentLandmarks[11],
            referencePose.average_pose["LEFT_SHOULDER"]!!
        )
        val rightShoulderDiff = comparePosition(
            currentLandmarks[12],
            referencePose.average_pose["RIGHT_SHOULDER"]!!
        )
        differences["SHOULDERS"] = (leftShoulderDiff + rightShoulderDiff) / 2

        val leftHipDiff = comparePosition(
            currentLandmarks[23],
            referencePose.average_pose["LEFT_HIP"]!!
        )
        val rightHipDiff = comparePosition(
            currentLandmarks[24],
            referencePose.average_pose["RIGHT_HIP"]!!
        )
        differences["HIPS"] = (leftHipDiff + rightHipDiff) / 2


        val comScore = compareDataProximity(
            currentLandmarks,
            imageWidth,
            imageHeight,
            //shouldLog  // shouldLog 전달
        )

        // 3. 제안사항 생성
        generateSuggestions(differences, comScore, suggestions)

        // 4. 최종 점수 계산
        val positionScore = calculatePositionScore(differences)

        val overallScore = positionScore * 0.7f + comScore * 0.3f

        return ComparisonResult(
            overallScore = overallScore,
            positionScore = positionScore,  // 추가
            centerScore = comScore,
            suggestions = suggestions,
            detailedScores = differences,
            isLeftHeavy = isLeftHeavy,
            isRightHeavy = isRightHeavy,
            isTopHeavy = isTopHeavy,
            isBottomHeavy = isBottomHeavy,
            currentComX=currentCenterX/imageWidth,
            currentComY=currentCenterY/imageHeight,
            targetComX=comX,
            targetComY=comY,
            zoomSuggestion = zoomSuggestion


        )
    }


    private fun determineZoomState(
        currentLandmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val (referenceNoseX, referenceNoseY) = restoreNormalizedValues("NOSE")
        val referenceDistance=calculateDistance(
            referenceNoseX,
            referenceNoseY,
            referenceCom.average_com?.x ?: 0f,
            referenceCom.average_com?.y ?: 0f
        )

        // Normalize current nose
        val currentNose = currentLandmarks[0]
        val normalizedCurrentNoseX = currentNose.x()
        val normalizedCurrentNoseY = currentNose.y()

        // Normalize current COM
        val normalizedCurrentComX = currentCenterX / imageWidth
        val normalizedCurrentComY = currentCenterY / imageHeight

        // Log the normalized values
        Log.d(TAG, "Current COM - X: $normalizedCurrentComX, Y: $normalizedCurrentComY")
        Log.d(TAG, "Current Nose - X: $normalizedCurrentNoseX, Y: $normalizedCurrentNoseY")
        Log.d(TAG, "Reference COM - X: ${referenceCom.average_com?.x}, Y: ${referenceCom.average_com?.y}")
        Log.d(TAG, "Reference Nose - X: $referenceNoseX, Y: $referenceNoseY")
        Log.d(TAG, "Reference Distance: $referenceDistance")

        val currentDistance = calculateDistance(
            normalizedCurrentNoseX,
            normalizedCurrentNoseY,
            normalizedCurrentComX,
            normalizedCurrentComY
        )



        Log.d(TAG, "Current Distance: $currentDistance")

        return when {
            currentDistance > referenceDistance -> "Zoom Out"
            currentDistance < referenceDistance -> "Zoom In"
            else -> "Perfect Zoom"
        }
    }



    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }

    private fun comparePosition(
        current: NormalizedLandmark,
        reference: PoseLandmark
    ): Float {
        return sqrt(
            (current.x() - reference.x) * (current.x() - reference.x) +
                    (current.y() - reference.y) * (current.y() - reference.y)
        )
    }

    private fun calculateNormalizedProximity(coordinate: Float, comCoordinate: Float, dimension: Int): Float {
        // 거리를 계산하고 이미지 크기로 나누어 정규화
        return abs(coordinate - comCoordinate) / dimension
    }


    private fun calculateXScore(diff: Float): Float {
        // 0.05를 기준으로 점수 계산
        val absDiff = abs(diff)
        return when {
            absDiff < 0.05f -> 100f  // 매우 정확
            absDiff < 0.1f -> 80f   // 좋음
            absDiff < 0.15f -> 60f  // 보통
            absDiff < 0.2f -> 40f   // 부족
            else -> 20f          // 매우 부족
        }
    }

    private fun calculateYScore(diff: Float): Float {
        val absDiff = abs(diff)
        return when {
            absDiff >= 0.1f -> 0f     // 너무 멀어서 점수 없음
            absDiff < 0.02f -> 100f   // 매우 정확
            absDiff < 0.04f -> 90f    // 우수
            absDiff < 0.06f -> 70f    // 양호
            absDiff < 0.08f -> 50f    // 부족
            else -> 30f               // 매우 부족
        }
    }

    private fun calculatePartScore(xScore: Float, yScore: Float): Float {
        // 둘 다 높은 점수일 때 보너스
        if (xScore >= 80f && yScore >= 80f) {
            return 100f
        }

        // 둘 중 하나라도 매우 낮으면 감점
        if (xScore <= 40f || yScore <= 40f) {
            return (xScore + yScore) / 4  // 큰 폭의 감점
        }

        // 기본 점수 계산 (x:y = 6:4)
        val baseScore = (xScore * 0.6f) + (yScore * 0.4f)

        // 점수 구간별 보정
        return when {
            baseScore >= 70f -> baseScore * 1.2f  // 상위 점수 보너스
            baseScore >= 50f -> baseScore * 1.1f  // 중상위 점수 약한 보너스
            baseScore >= 30f -> baseScore * 0.9f  // 중하위 점수 약한 감점
            else -> baseScore * 0.8f              // 하위 점수 감점
        }.coerceIn(0f, 100f)  // 최종 점수는 0~100 사이로 제한
    }

    private fun compareDataProximity(
        currentLandmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int,

    ): Float {
        // 현재 랜드마크의 위치를 픽셀 좌표로 변환
        val nose = currentLandmarks[0]
        val leftShoulder = currentLandmarks[11]
        val rightShoulder = currentLandmarks[12]
        val leftHip = currentLandmarks[23]
        val rightHip = currentLandmarks[24]

//        // 현재 좌표 계산
        val currentNoseX = (1 - nose.y()) * imageWidth
        val currentNoseY = nose.x() * imageHeight

        val currentShoulderX = ((1 - leftShoulder.y()) + (1 - rightShoulder.y())) / 2 * imageWidth
        val currentShoulderY = (leftShoulder.x() + rightShoulder.x()) / 2 * imageHeight

        val currentHipX = ((1 - leftHip.y()) + (1 - rightHip.y())) / 2 * imageWidth
        val currentHipY = (leftHip.x() + rightHip.x()) / 2 * imageHeight


        // 정규화된 거리 계산 (0-1 사이의 값)
        val currentNoseXProximity = calculateNormalizedProximity(currentNoseX, comX, imageWidth)
        val currentNoseYProximity = calculateNormalizedProximity(currentNoseY, comY, imageHeight)

        val currentShoulderXProximity = calculateNormalizedProximity(currentShoulderX, comX, imageWidth)
        val currentShoulderYProximity = calculateNormalizedProximity(currentShoulderY, comY, imageHeight)

        val currentHipXProximity = calculateNormalizedProximity(currentHipX, comX, imageWidth)
        val currentHipYProximity = calculateNormalizedProximity(currentHipY, comY, imageHeight)

        // 정규화된 거리값 차이 계산 - diff with the predetermined data
        val noseXDiff = abs(currentNoseXProximity - noseDataX)
        val noseYDiff = abs(currentNoseYProximity - noseDataY)
        val shoulderXDiff = abs(currentShoulderXProximity - shoulderDataX)
        val shoulderYDiff = abs(currentShoulderYProximity - shoulderDataY)
        val hipXDiff = abs(currentHipXProximity - hipDataX)
        val hipYDiff = abs(currentHipYProximity - hipDataY)

        // X, Y 각각의 점수 계산
        val noseXScore = calculateXScore(noseXDiff)
        val noseYScore = calculateYScore(noseYDiff)
        val shoulderXScore = calculateXScore(shoulderXDiff)
        val shoulderYScore = calculateYScore(shoulderYDiff)
        val hipXScore = calculateXScore(hipXDiff)
        val hipYScore = calculateYScore(hipYDiff)

        // 각 부위별 종합 점수 계산
        val noseScore = calculatePartScore(noseXScore, noseYScore)
        val shoulderScore = calculatePartScore(shoulderXScore, shoulderYScore)
        val hipScore = calculatePartScore(hipXScore, hipYScore)

        // 최종 점수 계산
        val finalScore = (noseScore + shoulderScore + hipScore) / 3

        // 방향 판단을 위한 중심점 계산
        val centerX = imageWidth / 2f
        val centerY = imageHeight / 2f

        // 현재 포즈의 중심 계산
        val currentCenterX = (currentNoseX + currentShoulderX + currentHipX) / 3
        val currentCenterY = (currentNoseY + currentShoulderY + currentHipY) / 3

        // 현재 중심점 저장
        this.currentCenterX = currentCenterX
        this.currentCenterY = currentCenterY

        // 방향 판단 (20% 차이를 기준으로)
        val threshold = imageWidth * 0.05f


        // COM 위치 로깅
//        Log.d("PoseComparator", "Current COM - X: ${currentCenterX/imageWidth}, Y: ${currentCenterY/imageHeight}")
//        Log.d("PoseComparator", "Target COM - X: $comX, Y: $comY")
//        Log.d("PoseComparator", "Difference - X: ${currentCenterX/imageWidth - comX}, Y: ${currentCenterY/imageHeight - comY}")

        // 주어진 COM과 현재 COM의 차이로 방향 판단
        isLeftHeavy = currentCenterX < (comX * imageWidth) - threshold
        isRightHeavy = currentCenterX > (comX * imageWidth) + threshold
        isTopHeavy = currentCenterY < (comY * imageHeight) - threshold
        isBottomHeavy = currentCenterY > (comY * imageHeight) + threshold



        return finalScore
    }



    private fun generateSuggestions(
        differences: Map<String, Float>,
        thirdsScore: Float,
        suggestions: MutableList<String>
    ) {
        // 코(얼굴 중심) 위치 확인
        differences["NOSE"]?.let { noseDiff ->
            if (noseDiff > CRITICAL_THRESHOLD) {
                suggestions.add("얼굴 위치를 참조 구도에 맞게 조정해주세요")
            }
        }

        // 어깨 정렬 확인
        differences["SHOULDERS"]?.let { shoulderDiff ->
            if (shoulderDiff > POSITION_THRESHOLD) {
                suggestions.add("어깨 높이를 맞춰주세요")
            }
        }

        // 엉덩이 정렬 확인
        differences["HIPS"]?.let { hipDiff ->
            if (hipDiff > POSITION_THRESHOLD) {
                suggestions.add("허리 위치를 조정해주세요")
            }
        }

        if (thirdsScore < 70f) {
            suggestions.add("삼분할 구도에 맞게 위치를 조정해주세요")
        }

        if (suggestions.isEmpty() && differences.values.average() > POSITION_THRESHOLD) {
            suggestions.add("전체적인 자세와 구도를 참조 이미지와 비슷하게 맞춰주세요")
        }
    }

    private fun calculateOverallScore(differences: Map<String, Float>, thirdsScore: Float): Float {
        val positionScore = calculatePositionScore(differences)
        return positionScore * 0.7f + thirdsScore * 0.3f
    }

    private fun calculatePositionScore(differences: Map<String, Float>): Float {
        val weights = mapOf(
            "NOSE" to 0.4f,        // 얼굴 위치가 가장 중요
            "SHOULDERS" to 0.3f,    // 어깨 정렬 두 번째로 중요
            "HIPS" to 0.3f         // 허리 위치 세 번째로 중요
        )

        var weightedSum = 0f
        var totalWeight = 0f

        differences.forEach { (key, value) ->
            weights[key]?.let { weight ->
                // 거리값이 작을수록 점수는 높아야 함
                val score = (1 - minOf(value, 1f)) * 100
                weightedSum += score * weight
                totalWeight += weight
            }
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0f
    }
}