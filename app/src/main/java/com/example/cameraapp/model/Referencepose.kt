package com.example.cameraapp.model

// app/kotlin+java/com.example.cameraapp/model/Referencepose.kt
data class Referencepose(
    val average_posture: Map<String, PoseLandmark>,
    val average_proximity_to_thirds: ThirdsProximity
)