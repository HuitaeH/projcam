package com.example.cameraapp.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint


// Function to draw an arrow from start (COM) to end (average COM)
fun drawArrow(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float) {
    val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.FILL_AND_STROKE
    }

    // Draw line (shaft of the arrow)
    canvas.drawLine(startX, startY, endX, endY, paint)

    // Calculate arrowhead
    val arrowSize = 20f
    val angle = Math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())

    val arrowAngle1 = angle + Math.toRadians(30.0)
    val arrowAngle2 = angle - Math.toRadians(30.0)

    val x1 = (endX - arrowSize * Math.cos(arrowAngle1)).toFloat()
    val y1 = (endY - arrowSize * Math.sin(arrowAngle1)).toFloat()

    val x2 = (endX - arrowSize * Math.cos(arrowAngle2)).toFloat()
    val y2 = (endY - arrowSize * Math.sin(arrowAngle2)).toFloat()

    // Draw arrowhead
    canvas.drawLine(endX, endY, x1, y1, paint)
    canvas.drawLine(endX, endY, x2, y2, paint)
}


