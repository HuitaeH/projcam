package com.example.cameraapp.model

// app/kotlin+java/com.example.cameraapp/model/Referencepose.kt
data class ReferencePoints(
    val average_pose: Map<String, PoseLandmark>,
    val average_com: PoseLandmark
)