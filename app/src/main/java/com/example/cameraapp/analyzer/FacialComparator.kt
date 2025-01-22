package com.example.cameraapp.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

class FacialComparator(
    private val referenceFacialData: FacialData
) {
    companion object {
        private const val TAG = "FacialComparator"
        private const val POSITION_THRESHOLD = 0.1f
        private const val CRITICAL_THRESHOLD = 0.15f
        private const val LOG_INTERVAL = 5000L  // 5초
    }

    // Parse facial data from the JSON
    val centerX = referenceFacialData.average_center?.x ?: 0f
    val centerY = referenceFacialData.average_center?.y ?: 0f

    val leftEyeDistance = referenceFacialData.average_distances?.left_eye ?: 0f
    val rightEyeDistance = referenceFacialData.average_distances?.right_eye ?: 0f
    val leftEarDistance = referenceFacialData.average_distances?.left_ear ?: 0f
    val rightEarDistance = referenceFacialData.average_distances?.right_ear ?: 0f
    val mouthCenterDistance = referenceFacialData.average_distances?.mouth_center ?: 0f
    val chinDistance = referenceFacialData.average_distances?.chin ?: 0f
    val foreheadDistance = referenceFacialData.average_distances?.forehead ?: 0f

    // Store the last log time
    private var lastLogTime = 0L

    // Data class for comparison results
    data class ComparisonResult(
        val overallScore: Float,
        val suggestions: List<String>,
        val detailedScores: Map<String, Float>
    )

    private fun shouldLogNow(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime >= LOG_INTERVAL) {
            lastLogTime = currentTime
            return true
        }
        return false
    }

    fun compareFacialFeatures(
        currentLandmarks: List<NormalizedLandmark>,
        shouldLog: Boolean
    ): ComparisonResult {
        val differences = mutableMapOf<String, Float>()
        val suggestions = mutableListOf<String>()

        // Compare the average center of the face
        differences["CENTER"] = comparePosition(
            currentLandmarks[0], // Assuming the nose or a similar center point
            centerX,
            centerY
        )

        // Compare left and right eye distances
        val leftEyeDiff = compareDistance(
            currentLandmarks[1], // Left eye
            leftEyeDistance
        )
        val rightEyeDiff = compareDistance(
            currentLandmarks[2], // Right eye
            rightEyeDistance
        )
        differences["EYES"] = (leftEyeDiff + rightEyeDiff) / 2

        // Compare ear distances
        val leftEarDiff = compareDistance(
            currentLandmarks[3], // Left ear
            leftEarDistance
        )
        val rightEarDiff = compareDistance(
            currentLandmarks[4], // Right ear
            rightEarDistance
        )
        differences["EARS"] = (leftEarDiff + rightEarDiff) / 2

        // Compare mouth center and chin distances
        val mouthCenterDiff = compareDistance(
            currentLandmarks[5], // Mouth center
            mouthCenterDistance
        )
        val chinDiff = compareDistance(
            currentLandmarks[6], // Chin
            chinDistance
        )
        differences["MOUTH_AND_CHIN"] = (mouthCenterDiff + chinDiff) / 2

        // Compare forehead distance
        val foreheadDiff = compareDistance(
            currentLandmarks[7], // Forehead
            foreheadDistance
        )
        differences["FOREHEAD"] = foreheadDiff

        // Calculate the face score
        val overallScore = calculateFaceScore(differences)

        // Generate suggestions based on differences
        generateSuggestions(differences, suggestions)

        // Calculate overall score

        return ComparisonResult(
            overallScore = overallScore,
            suggestions = suggestions,
            detailedScores = differences
        )
    }

    private fun comparePosition(
        current: NormalizedLandmark,
        referenceX: Float,
        referenceY: Float
    ): Float {
        return sqrt(
            (current.x() - referenceX) * (current.x() - referenceX) +
                    (current.y() - referenceY) * (current.y() - referenceY)
        )
    }

    private fun compareDistance(
        current: NormalizedLandmark,
        referenceDistance: Float
    ): Float {
        // Compare distance between current landmark and reference distance
        val distance = sqrt(
            (current.x() - referenceDistance) * (current.x() - referenceDistance) +
                    (current.y() - referenceDistance) * (current.y() - referenceDistance)
        )
        return distance
    }

    private fun calculateFaceScore(differences: Map<String, Float>): Float {
        var score = 0f
        differences.forEach { (key, value) ->
            val weightedScore = when (key) {
                "CENTER" -> (1 - value) * 100
                else -> (1 - value) * 50
            }
            score += weightedScore
        }
        return score / differences.size
    }

    private fun generateSuggestions(
        differences: Map<String, Float>,
        suggestions: MutableList<String>
    ) {
        differences["CENTER"]?.let { centerDiff ->
            if (centerDiff > CRITICAL_THRESHOLD) {
                suggestions.add("얼굴 중심을 조정해주세요")
            }
        }

        differences["EYES"]?.let { eyesDiff ->
            if (eyesDiff > POSITION_THRESHOLD) {
                suggestions.add("눈 위치를 조정해주세요")
            }
        }

        differences["EARS"]?.let { earsDiff ->
            if (earsDiff > POSITION_THRESHOLD) {
                suggestions.add("귀 위치를 조정해주세요")
            }
        }

        differences["MOUTH_AND_CHIN"]?.let { mouthAndChinDiff ->
            if (mouthAndChinDiff > POSITION_THRESHOLD) {
                suggestions.add("입과 턱 위치를 맞춰주세요")
            }
        }

        if (suggestions.isEmpty() && differences.values.average() > POSITION_THRESHOLD) {
            suggestions.add("전체적인 얼굴 위치를 참조 이미지와 비슷하게 맞춰주세요")
        }
    }
}

// Model for parsing JSON data
data class FacialData(
    val average_center: Point?,
    val average_distances: Distances?
)

data class Point(val x: Float, val y: Float)

data class Distances(
    val left_eye: Float,
    val right_eye: Float,
    val left_ear: Float,
    val right_ear: Float,
    val mouth_center: Float,
    val chin: Float,
    val forehead: Float
)
