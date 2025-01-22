package com.example.cameraapp.analyzer

import android.util.Log
import com.example.cameraapp.model.*
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.sqrt

// app/kotlin+java/com.example.cameraapp/analyzer/PoseComparator.kt
class PoseComparator {
    companion object {
        private const val TAG = "PoseComparator"
        private const val POSITION_THRESHOLD = 0.1f
        private const val CRITICAL_THRESHOLD = 0.15f
        private const val THIRDS_THRESHOLD = 100f
    }

    // 포즈 비교 결과를 담는 데이터 클래스들
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
        imageHeight: Int
    ): ComparisonResult {
        val differences = mutableMapOf<String, Float>()
        val suggestions = mutableListOf<String>()

        // 1. 기본 자세 비교
        differences["NOSE"] = comparePosition(
            currentLandmarks[0],
            referencePose.average_posture["NOSE"]!!
        )

        // 어깨
        val leftShoulderDiff = comparePosition(
            currentLandmarks[11],
            referencePose.average_posture["LEFT_SHOULDER"]!!
        )
        val rightShoulderDiff = comparePosition(
            currentLandmarks[12],
            referencePose.average_posture["RIGHT_SHOULDER"]!!
        )
        differences["SHOULDERS"] = (leftShoulderDiff + rightShoulderDiff) / 2

        // 허리
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
            imageHeight
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

    private fun compareThirdsProximity(
        currentLandmarks: List<NormalizedLandmark>,
        referenceThirds: ThirdsProximity,
        imageWidth: Int,
        imageHeight: Int
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

        // 각 부위별 삼분할 구도 차이 계산
        val noseXDiff = abs(currentNoseX - referenceThirds.average_nose_x_proximity)
        val noseYDiff = abs(currentNoseY - referenceThirds.average_nose_y_proximity)
        val shoulderXDiff = abs(currentShoulderX - referenceThirds.average_shoulder_x_proximity)
        val shoulderYDiff = abs(currentShoulderY - referenceThirds.average_shoulder_y_proximity)
        val hipXDiff = abs(currentHipX - referenceThirds.average_hip_x_proximity)
        val hipYDiff = abs(currentHipY - referenceThirds.average_hip_y_proximity)

        // 점수 계산
        val maxDiff = maxOf(imageWidth, imageHeight).toFloat()
        val noseScore = (1 - minOf((noseXDiff + noseYDiff) / maxDiff, 1f)) * 100
        val shoulderScore = (1 - minOf((shoulderXDiff + shoulderYDiff) / maxDiff, 1f)) * 100
        val hipScore = (1 - minOf((hipXDiff + hipYDiff) / maxDiff, 1f)) * 100

        // 가중치 적용
        return (noseScore * 0.4f + shoulderScore * 0.3f + hipScore * 0.3f)
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

        // 전체적인 자세가 많이 다른 경우
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