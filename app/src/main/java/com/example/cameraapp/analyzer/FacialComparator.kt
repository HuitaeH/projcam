package com.example.cameraapp.analyzer

import android.util.Log
import com.example.cameraapp.model.PoseLandmark
import com.example.cameraapp.model.ReferenceFace
import com.example.cameraapp.model.ReferencePoints
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.sqrt

class FacialComparator(referenceFace: ReferenceFace) {
    companion object {
        private const val TAG = "FacialComparator"
        private const val POSITION_THRESHOLD = 0.1f
        private const val CRITICAL_THRESHOLD = 0.15f
        private const val LOG_INTERVAL = 5000L  // 5초
    }

    // Parse facial data from the JSON
    val centerX = referenceFace.average_center?.x ?: 0f
    val centerY = referenceFace.average_center?.y ?: 0f

    val leftEyeDistanceX = referenceFace.average_distances["left_eye"]?.x ?: 0f - centerX
    val leftEyeDistanceY = referenceFace.average_distances["left_eye"]?.y ?: 0f - centerY
    val rightEyeDistanceX = referenceFace.average_distances["right_eye"]?.x ?: 0f- centerX
    val rightEyeDistanceY = referenceFace.average_distances["right_eye"]?.y ?: 0f - centerY
    val leftEarDistanceX = referenceFace.average_distances["left_ear"]?.x ?: 0f- centerX
    val leftEarDistanceY = referenceFace.average_distances["left_ear"]?.y ?: 0f - centerY
    val rightEarDistanceX = referenceFace.average_distances["right_ear"]?.x ?: 0f- centerX
    val rightEarDistanceY = referenceFace.average_distances["right_ear"]?.y ?: 0f - centerY
    val mouthCenterDistanceX = referenceFace.average_distances["mouth_center"]?.x ?: 0f- centerX
    val mouthCenterDistanceY = referenceFace.average_distances["mouth_center"]?.y ?: 0f - centerY
    val chinDistanceX = referenceFace.average_distances["chin"]?.x ?: 0f- centerX
    val chinDistanceY = referenceFace.average_distances["chin"]?.y ?: 0f - centerY
    val foreheadDistanceX = referenceFace.average_distances["forehead"]?.x ?: 0f- centerX
    val foreheadDistanceY = referenceFace.average_distances["forehead"]?.y ?: 0f - centerY

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

    fun compareFace(
        currentLandmarks: List<NormalizedLandmark>,
        referenceFace: ReferenceFace,
        imageWidth: Int,
        imageHeight: Int,
        shouldLog: Boolean
    ): ComparisonResult {
        val differences = mutableMapOf<String, Float>()
        val suggestions = mutableListOf<String>()


        // Compare the average center of the face
        differences["NOSE"] = comparePosition(
            currentLandmarks[1], // Assuming the nose or a similar center point
            referenceFace.average_center.x,
            referenceFace.average_center.y
        )

        Log.d(TAG, "NOSE - Current: (${currentLandmarks[1].x()}, ${currentLandmarks[1].y()}), " +
                "Reference: (${referenceFace.average_center.x}, ${referenceFace.average_center.y}), " +
                "Diff: ${differences["NOSE"]}")


        // Compare left and right eye distances
        val leftEyeDiff = compareDistance(
            currentLandmarks[33], // Left eye
            referenceFace.average_distances["left_eye"]!!
        )
        val rightEyeDiff = compareDistance(
            currentLandmarks[263], // Right eye
            referenceFace.average_distances["right_eye"]!!
        )
        differences["EYES"] = (leftEyeDiff + rightEyeDiff) / 2

        Log.d(TAG, "EYES - Left Eye - Current: (${currentLandmarks[33].x()}, ${currentLandmarks[33].y()}), " +
                "Reference: (${referenceFace.average_distances["left_eye"]?.x}, ${referenceFace.average_distances["left_eye"]?.y}), " +
                "Diff: $leftEyeDiff")
        Log.d(TAG, "EYES - Right Eye - Current: (${currentLandmarks[263].x()}, ${currentLandmarks[263].y()}), " +
                "Reference: (${referenceFace.average_distances["right_eye"]?.x}, ${referenceFace.average_distances["right_eye"]?.y}), " +
                "Diff: $rightEyeDiff")
        Log.d(TAG, "chin - Current: (${currentLandmarks[152].x()}, ${currentLandmarks[152].y()}), " +
                "Reference: (${referenceFace.average_distances["chin"]?.x}, ${referenceFace.average_distances["chin"]?.y}), " +
                "Diff: $rightEyeDiff")


        // Compare ear distances
        val leftEarDiff = compareDistance(
            currentLandmarks[234], // Left ear
            referenceFace.average_distances["left_ear"]!!
        )
        val rightEarDiff = compareDistance(
            currentLandmarks[454], // Right ear
            referenceFace.average_distances["right_ear"]!!
        )
        differences["EARS"] = (leftEarDiff + rightEarDiff) / 2

        // Compare mouth center and chin distances
        val mouthCenterDiff = compareDistance(
            currentLandmarks[13], // Mouth center
            referenceFace.average_distances["mouth_center"]!!

        )
        val chinDiff = compareDistance(
            currentLandmarks[152], // Chin
            referenceFace.average_distances["chin"]!!
        )
        differences["MOUTH_AND_CHIN"] = (mouthCenterDiff + chinDiff) / 2

        // Compare forehead distance
        val foreheadDiff = compareDistance(
            currentLandmarks[10], // Forehead
            referenceFace.average_distances["forehead"]!!
        )
        differences["FOREHEAD"] = foreheadDiff

        // Calculate the face score
//        val overallScore = calculateFaceScore(differences)
        val overallScore = compareDataProximity(
            currentLandmarks,
            imageWidth,
            imageHeight,
            shouldLog
        )

        // Generate suggestions based on differences
        generateSuggestions(differences, suggestions)

        // Calculate overall score

        return ComparisonResult(
            overallScore = overallScore,
            suggestions = suggestions,
            detailedScores = differences
        )
    }

    private fun calculateNormalizedProximity(coordinate: Float, centerCoordinate: Float, dimension: Int): Float {
        // 거리를 계산하고 이미지 크기로 나누어 정규화
        return coordinate - centerCoordinate
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
        shouldLog: Boolean
    ): Float {
        fun transformCoordinates(landmark: NormalizedLandmark): Pair<Float, Float> {
            return Pair(landmark.y(), landmark.x()) // Swap x and y
        }

        val currentCenter = currentLandmarks[1]
        val (currentLeftEyeX, currentLeftEyeY) = transformCoordinates(currentLandmarks[33])
        val (currentRightEyeX, currentRightEyeY) = transformCoordinates(currentLandmarks[263])
        val (currentLeftEarX, currentLeftEarY) = transformCoordinates(currentLandmarks[234])
        val (currentRightEarX, currentRightEarY) = transformCoordinates(currentLandmarks[254])
        val (currentMouthCenterX, currentMouthCenterY) = transformCoordinates(currentLandmarks[13])
        val (currentChinX, currentChinY) = transformCoordinates(currentLandmarks[152])
        val (currentForeheadX, currentForeheadY) = transformCoordinates(currentLandmarks[10])

//        // 현재 랜드마크의 위치를 픽셀 좌표로 변환
//        val currentCenter = currentLandmarks[1]
//
//        val currentLeftEye = currentLandmarks[33]
//        val currentLeftEyeX = currentLeftEye.x()
//        val currentLeftEyeY = currentLeftEye.y()
//
//        val currentRightEye = currentLandmarks[263]
//        val currentRightEyeX = currentRightEye.x()
//        val currentRightEyeY = currentRightEye.y()
//
//        val currentLeftEar = currentLandmarks[234]
//        val currentLeftEarX = currentLeftEar.x()
//        val currentLeftEarY = currentLeftEar.y()
//
//        val currentRightEar = currentLandmarks[254]
//        val currentRightEarX = currentRightEar.x()
//        val currentRightEarY = currentRightEar.y()
//
//        val currentMouthCenter = currentLandmarks[13]
//        val currentMouthCenterX = currentMouthCenter.x()
//        val currentMouthCenterY = currentMouthCenter.y()
//
//        val currentChin = currentLandmarks[152]
//        val currentChinX = currentChin.x()
//        val currentCHinY = currentChin.y()
//
//        val currentForehead = currentLandmarks[10]
//        val currentForeheadX = currentForehead.x()
//        val currentForeheadY = currentForehead.y()
//        Log.d(TAG, "centerX: $centerX, centerY: $centerY")
//        Log.d(TAG, "Current coordinates - LeftEye: (${currentLeftEyeX}, ${currentLeftEyeY})")


        // 정규화된 거리 계산 (0-1 사이의 값)
        val currentLeftEyeXProximity = calculateNormalizedProximity(currentLeftEyeX, centerX, imageWidth)
        val currentLeftEyeYProximity = calculateNormalizedProximity(currentLeftEyeY, centerY, imageHeight)

        val currentRightEyeXProximity =
            calculateNormalizedProximity(currentRightEyeX, centerX, imageWidth)
        val currentRightEyeYProximity =
            calculateNormalizedProximity(currentRightEyeY, centerY, imageHeight)

        val currentLeftEarXProximity = calculateNormalizedProximity(currentLeftEarX, centerX, imageWidth)
        val currentLeftEarYProximity = calculateNormalizedProximity(currentLeftEarY, centerY, imageHeight)

        val currentRightEarXProximity = calculateNormalizedProximity(currentRightEarX, centerX, imageWidth)
        val currentRightEarYProximity = calculateNormalizedProximity(currentRightEarY, centerY, imageHeight)

        val currentMouthCenterXProximity = calculateNormalizedProximity(currentMouthCenterX, centerX, imageWidth)
        val currentMouthCenterYProximity = calculateNormalizedProximity(currentMouthCenterY, centerY, imageHeight)

        val currentChinXProximity = calculateNormalizedProximity(currentChinX, centerX, imageWidth)
        val currentChinYProximity = calculateNormalizedProximity(currentChinY, centerY, imageHeight)

        val currentForeheadXProximity = calculateNormalizedProximity(currentForeheadX, centerX, imageWidth)
        val currentForeheadYProximity = calculateNormalizedProximity(currentForeheadY, centerY, imageHeight)

        // After calculating proximities
        Log.d(TAG, "Proximities - LeftEye: ($currentLeftEyeXProximity, $currentLeftEyeYProximity)")



        // 정규화된 거리값 차이 계산 - diff with the predetermined data
        val leftEyeXDiff = abs(currentLeftEyeXProximity - centerX + leftEyeDistanceX)
        val leftEyeYDiff = abs(currentLeftEyeYProximity - centerY + leftEyeDistanceY)
        val rightEyeXDiff = abs(currentRightEyeXProximity - centerX + rightEyeDistanceX)
        val rightEyeYDiff = abs(currentRightEyeYProximity - centerY + rightEyeDistanceY)
        val leftEarXDiff = abs(currentLeftEarXProximity - centerX + leftEarDistanceX)
        val leftEarYDiff = abs(currentLeftEarYProximity - centerY + leftEarDistanceY)
        val rightEarXDiff = abs(currentRightEarXProximity - centerX + rightEarDistanceX)
        val rightEarYDiff = abs(currentRightEarYProximity - centerY + rightEarDistanceY)
        val mouthCenterXDiff = abs(currentMouthCenterXProximity - centerX + mouthCenterDistanceX)
        val mouthCenterYDiff = abs(currentMouthCenterYProximity - centerY + mouthCenterDistanceY)
        val chinXDiff = abs(currentChinXProximity - centerX + chinDistanceX)
        val chinYDiff = abs(currentChinYProximity - centerY + chinDistanceY)
        val foreheadXDiff = abs(currentForeheadXProximity - centerX + foreheadDistanceX)
        val foreheadYDiff = abs(currentForeheadYProximity - centerY + foreheadDistanceY)
        // After calculating differences
        Log.d(TAG, "Differences - LeftEye: X=$leftEyeXDiff, Y=$leftEyeYDiff, !!!$leftEyeDistanceX")


        // X, Y 각각의 점수 계산
        val leftEyeXScore = calculateXScore(leftEyeXDiff)
        val leftEyeYScore = calculateYScore(leftEyeYDiff)
        val rightEyeXScore = calculateXScore(rightEyeXDiff)
        val rightEyeYScore = calculateYScore(rightEyeYDiff)
        val leftEarXScore = calculateXScore(leftEarXDiff)
        val leftEarYScore = calculateYScore(leftEarYDiff)
        val rightEarXScore = calculateXScore(rightEarXDiff)
        val rightEarYScore = calculateYScore(rightEarYDiff)
        val mouthCenterXScore = calculateXScore(mouthCenterXDiff)
        val mouthCenterYScore = calculateYScore(mouthCenterYDiff)
        val chinXScore = calculateXScore(chinXDiff)
        val chinYScore = calculateYScore(chinYDiff)
        val foreheadXScore = calculateXScore(foreheadXDiff)
        val foreheadYScore = calculateYScore(foreheadYDiff)

        // After calculating scores
        Log.d(TAG, "Scores - LeftEye: X=$leftEyeXScore, Y=$leftEyeYScore")

        // 각 부위별 종합 점수 계산
        val leftEyeScore = calculatePartScore(leftEyeXScore, leftEyeYScore)
        val rightEyeScore = calculatePartScore(rightEyeXScore, rightEyeYScore)
        val leftEarScore = calculatePartScore(leftEarXScore, leftEarYScore)
        val rightEarScore = calculatePartScore(rightEarXScore, rightEarYScore)
        val mouthCenterScore = calculatePartScore(mouthCenterXScore, mouthCenterYScore)
        val chinScore = calculatePartScore(chinXScore, chinYScore)
        val foreheadScore = calculatePartScore(foreheadXScore, foreheadYScore)


        // 최종 점수 계산
        val finalScore = minOf(100f, (leftEyeScore + rightEyeScore + mouthCenterScore)* 4 / 3)
        // Before returning
        Log.d(TAG, "Final Score: $finalScore")

        return finalScore
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
        referenceDistance: PoseLandmark?
    ): Float {
        if (referenceDistance == null) return 0f

        // Convert negative coordinates to positive
        val refX = abs(referenceDistance.x)
        val refY = abs(referenceDistance.y)

        return sqrt(
            (current.x() - refX) * (current.x() - refX) +
                    (current.y() - refY) * (current.y() - refY)
        )
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


