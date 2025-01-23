package com.example.cameraapp.model

data class ReferenceFace(
    val average_distances: Map<String, PoseLandmark>,
    val average_center: PoseLandmark
)