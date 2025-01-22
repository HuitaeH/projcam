package com.example.cameraapp.analyzer

import android.util.Log
import com.example.cameraapp.model.*
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// app/kotlin+java/com.example.cameraapp/analyzer/PoseComparator.kt
class PoseComparator {
    companion object {
        private const val TAG = "PoseComparator"
        private const val POSITION_THRESHOLD = 0.1f
        private const val CRITICAL_THRESHOLD = 0.15f
        private const val THIRDS_THRESHOLD = 100f
        private const val LOG_INTERVAL = 5000L  // 5초
    }


    // 마지막 로그 출력 시간을 저장
    private var lastLogTime = 0L

    data class ComparisonResult(
        val overallScore: Float,           // 전체 유사도 점수 (0~100)
        val positionScore: Float,          // 자세 점수
        val thirdsScore: Float,            // 구도 점수
        val suggestions: List<String>,     // 개선을 위한 제안사항
        val detailedScores: Map<String, Float>  // 각 부위별 유사도 점수
    )



    fun comparePose(
        currentLandmarks: List<NormalizedLandmark>,
        referencePose: Referencepose,
        imageWidth: Int,
        imageHeight: Int,
        //shouldLog: Boolean  // 추가된 파라미터
    ): ComparisonResult {
        val differences = mutableMapOf<String, Float>()
        val suggestions = mutableListOf<String>()

        // 1. 기본 자세 비교
        differences["NOSE"] = comparePosition(
            currentLandmarks[0],
            referencePose.average_posture["NOSE"]!!
        )

        val leftShoulderDiff = comparePosition(
            currentLandmarks[11],
            referencePose.average_posture["LEFT_SHOULDER"]!!
        )
        val rightShoulderDiff = comparePosition(
            currentLandmarks[12],
            referencePose.average_posture["RIGHT_SHOULDER"]!!
        )
        differences["SHOULDERS"] = (leftShoulderDiff + rightShoulderDiff) / 2

        val leftHipDiff = comparePosition(
            currentLandmarks[23],
            referencePose.average_posture["LEFT_HIP"]!!
        )
        val rightHipDiff = comparePosition(
            currentLandmarks[24],
            referencePose.average_posture["RIGHT_HIP"]!!
        )
        differences["HIPS"] = (leftHipDiff + rightHipDiff) / 2

        // 2. 삼분할 구도 비교
        val thirdsScore = compareThirdsProximity(
            currentLandmarks,
            referencePose.average_proximity_to_thirds,
            imageWidth,
            imageHeight,
            //shouldLog  // shouldLog 전달
        )

        // 3. 제안사항 생성
        generateSuggestions(differences, thirdsScore, suggestions)

        // 4. 최종 점수 계산
        val positionScore = calculatePositionScore(differences)

        val overallScore = positionScore * 0.7f + thirdsScore * 0.3f

        return ComparisonResult(
            overallScore = overallScore,
            positionScore = positionScore,  // 추가
            thirdsScore = thirdsScore,
            suggestions = suggestions,
            detailedScores = differences
        )
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

    private fun calculateNormalizedProximity(coordinate: Float, thirdLine: Float, dimension: Int): Float {
        // 거리를 계산하고 이미지 크기로 나누어 정규화
        return abs(coordinate - thirdLine) / dimension
    }

    private fun calculateThirdsScore(diff: Float): Float {
        // 지수적으로 감소하는 기본 점수
        val baseScore = (1 - Math.pow(diff.toDouble(), 2.0)).toFloat() * 100

        // 구간별 가중치 적용
        val weight = when {
            diff < 0.1f -> 1.2f  // 매우 가까우면 추가 보너스
            diff < 0.2f -> 1.0f  // 기준점
            diff < 0.3f -> 0.8f  // 약간 감점
            else -> 0.6f         // 크게 감점
        }

        return (baseScore * weight).coerceIn(0f, 100f)
    }

    private fun calculateXScore(diff: Float): Float {
        // 0.05를 기준으로 점수 계산
        return when {
            diff < 0.05f -> 100f  // 매우 정확
            diff < 0.1f -> 80f   // 좋음
            diff < 0.15f -> 60f  // 보통
            diff < 0.2f -> 40f   // 부족
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

    private fun compareThirdsProximity(
        currentLandmarks: List<NormalizedLandmark>,
        referenceThirds: ThirdsProximity,
        imageWidth: Int,
        imageHeight: Int,
        //shouldLog: Boolean
    ): Float {
        // 현재 랜드마크의 위치를 픽셀 좌표로 변환
        val nose = currentLandmarks[0]
        val leftShoulder = currentLandmarks[11]
        val rightShoulder = currentLandmarks[12]
        val leftHip = currentLandmarks[23]
        val rightHip = currentLandmarks[24]

        // 현재 좌표 계산
        val currentNoseX = (1 - nose.y()) * imageWidth
        val currentNoseY = nose.x() * imageHeight

        val currentShoulderX = ((1 - leftShoulder.y()) + (1 - rightShoulder.y())) / 2 * imageWidth
        val currentShoulderY = (leftShoulder.x() + rightShoulder.x()) / 2 * imageHeight

        val currentHipX = ((1 - leftHip.y()) + (1 - rightHip.y())) / 2 * imageWidth
        val currentHipY = (leftHip.x() + rightHip.x()) / 2 * imageHeight

        // 첫 번째 삼분할선 위치 계산
        val firstVerticalThird = imageWidth / 3f
        val firstHorizontalThird = imageHeight / 3f

        // 정규화된 거리 계산 (0-1 사이의 값)
        val currentNoseXProximity = calculateNormalizedProximity(currentNoseX, firstVerticalThird, imageWidth)
        val currentNoseYProximity = calculateNormalizedProximity(currentNoseY, firstHorizontalThird, imageHeight)

        val currentShoulderXProximity = calculateNormalizedProximity(currentShoulderX, firstVerticalThird, imageWidth)
        val currentShoulderYProximity = calculateNormalizedProximity(currentShoulderY, firstHorizontalThird, imageHeight)

        val currentHipXProximity = calculateNormalizedProximity(currentHipX, firstVerticalThird, imageWidth)
        val currentHipYProximity = calculateNormalizedProximity(currentHipY, firstHorizontalThird, imageHeight)

        // 정규화된 거리값 차이 계산
        val noseXDiff = abs(currentNoseXProximity - referenceThirds.average_nose_x_proximity)
        val noseYDiff = abs(currentNoseYProximity - referenceThirds.average_nose_y_proximity)
        val shoulderXDiff = abs(currentShoulderXProximity - referenceThirds.average_shoulder_x_proximity)
        val shoulderYDiff = abs(currentShoulderYProximity - referenceThirds.average_shoulder_y_proximity)
        val hipXDiff = abs(currentHipXProximity - referenceThirds.average_hip_x_proximity)
        val hipYDiff = abs(currentHipYProximity - referenceThirds.average_hip_y_proximity)

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

        // 5초마다 로그 출력
//        if (shouldLog) {
//            Log.d("ThirdsGuide", """
//                코 (X):
//                - 현재: $currentNoseXProximity
//                - 참조: ${referenceThirds.average_nose_x_proximity}
//                - 차이: $noseXDiff
//
//                코 (Y):
//                - 현재: $currentNoseYProximity
//                - 참조: ${referenceThirds.average_nose_y_proximity}
//                - 차이: $noseYDiff
//
//                어깨 (X):
//                - 현재: $currentShoulderXProximity
//                - 참조: ${referenceThirds.average_shoulder_x_proximity}
//                - 차이: $shoulderXDiff
//
//                어깨 (Y):
//                - 현재: $currentShoulderYProximity
//                - 참조: ${referenceThirds.average_shoulder_y_proximity}
//                - 차이: $shoulderYDiff
//
//                엉덩이 (X):
//                - 현재: $currentHipXProximity
//                - 참조: ${referenceThirds.average_hip_x_proximity}
//                - 차이: $hipXDiff
//
//                엉덩이 (Y):
//                - 현재: $currentHipYProximity
//                - 참조: ${referenceThirds.average_hip_y_proximity}
//                - 차이: $hipYDiff
//            """.trimIndent())
//        }

        return finalScore
    }



    private fun generateSuggestions(
        differences: Map<String, Float>,
        thirdsScore: Float,
        suggestions: MutableList<String>
    ) {
        differences["NOSE"]?.let { noseDiff ->
            if (noseDiff > CRITICAL_THRESHOLD) {
                suggestions.add("얼굴 위치를 참조 구도에 맞게 조정해주세요")
            }
        }

        differences["SHOULDERS"]?.let { shoulderDiff ->
            if (shoulderDiff > POSITION_THRESHOLD) {
                suggestions.add("어깨 높이를 맞춰주세요")
            }
        }

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
            "NOSE" to 0.4f,
            "SHOULDERS" to 0.3f,
            "HIPS" to 0.3f
        )

        var weightedSum = 0f
        var totalWeight = 0f

        differences.forEach { (key, value) ->
            weights[key]?.let { weight ->
                val score = (1 - minOf(value, 1f)) * 100
                weightedSum += score * weight
                totalWeight += weight
            }
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0f
    }
}