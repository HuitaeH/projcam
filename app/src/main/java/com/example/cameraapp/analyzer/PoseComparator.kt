package com.example.cameraapp.analyzer

import android.util.Log
import com.example.cameraapp.model.*
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.sqrt

class PoseComparator {
    companion object {
        private const val TAG = "PoseComparator"
        private const val POSITION_THRESHOLD = 0.2f  // 위치 차이 허용 범위
        private const val CRITICAL_THRESHOLD = 0.25f // 중요 부위 허용 범위
    }

    // 포즈 비교 결과를 담는 데이터 클래스들
    data class ComparisonResult(
        val overallScore: Float,           // 전체 유사도 점수 (0~100)
        val suggestions: List<String>,     // 개선을 위한 제안사항
        val detailedScores: Map<String, Float>  // 각 부위별 유사도 점수
    )

    fun comparePose(
        currentLandmarks: List<NormalizedLandmark>,
        referencePose: Referencepose
    ): ComparisonResult {
        val differences = mutableMapOf<String, Float>()
        val suggestions = mutableListOf<String>()

        // 주요 신체 부위 비교
        // 얼굴
        differences["NOSE"] = comparePosition(
            currentLandmarks[0],
            referencePose.NOSE
        )

        // 어깨
        val leftShoulderDiff = comparePosition(
            currentLandmarks[11],
            referencePose.LEFT_SHOULDER
        )
        val rightShoulderDiff = comparePosition(
            currentLandmarks[12],
            referencePose.RIGHT_SHOULDER
        )
        differences["SHOULDERS"] = (leftShoulderDiff + rightShoulderDiff) / 2

        // 허리
        val leftHipDiff = comparePosition(
            currentLandmarks[23],
            referencePose.LEFT_HIP
        )
        val rightHipDiff = comparePosition(
            currentLandmarks[24],
            referencePose.RIGHT_HIP
        )
        differences["HIPS"] = (leftHipDiff + rightHipDiff) / 2

        // 구도 제안사항 생성
        generateSuggestions(differences, suggestions)

        // 전체 점수 계산 (100점 만점)
        val overallScore = calculateOverallScore(differences)

        return ComparisonResult(
            overallScore = overallScore,
            suggestions = suggestions,
            detailedScores = differences
        )
    }

    private fun comparePosition(
        current: NormalizedLandmark,
        reference: PoseLandmark
    ): Float {
        // 좌표계 변환 적용 (카메라 회전 고려)
        val currentX = 1 - current.y()
        val currentY = current.x()

        // 유클리드 거리 계산
        val distance = sqrt(
            (currentX - reference.x) * (currentX - reference.x) +
                    (currentY - reference.y) * (currentY - reference.y)
        )

        return distance
    }

    private fun generateSuggestions(
        differences: Map<String, Float>,
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
            suggestions.add("전체적인 자세를 참조 구도와 비슷하게 맞춰주세요")
        }
    }

    private fun calculateOverallScore(differences: Map<String, Float>): Float {
        val weights = mapOf(
            "NOSE" to 0.6f,        // 얼굴 위치가 가장 중요
            "SHOULDERS" to 0.4f,    // 어깨 정렬 두 번째로 중요
            "HIPS" to 0f         // 허리 위치 세 번째로 중요
        )

        var weightedSum = 0f
        var totalWeight = 0f

        differences.forEach { (key, value) ->
            weights[key]?.let { weight ->
                // 거리값이 작을수록 점수는 높아야 함
                val score = minOf((1 - minOf(value, 1f)) * 100 *2, 100f)
                weightedSum += score * weight
                totalWeight += weight
            }
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0f
    }
}