package com.example.cameraapp.util

import com.example.cameraapp.model.Referencepose
import com.google.gson.Gson

object JsonParser {
    fun parseReferencePose(jsonString: String): Referencepose {
        return Gson().fromJson(jsonString, Referencepose::class.java)
    }
}