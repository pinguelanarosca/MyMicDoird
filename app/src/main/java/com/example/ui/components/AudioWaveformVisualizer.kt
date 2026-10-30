package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.TealSecondary

@Composable
fun AudioWaveformVisualizer(
    waveformHistory: List<Float>,
    isStreaming: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF091019))
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw center baseline
        drawLine(
            color = Color(0xFF1E2D40),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        if (!isStreaming || isMuted || waveformHistory.isEmpty()) {
            // Idle subtle flat ripple
            drawLine(
                color = if (isMuted) Color(0xFFFF9100).copy(alpha = 0.5f) else CyanPrimary.copy(alpha = 0.3f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2.dp.toPx()
            )
            return@Canvas
        }

        val step = width / (waveformHistory.size - 1).coerceAtLeast(1)
        val path = Path()
        val fillPath = Path()

        fillPath.moveTo(0f, centerY)

        waveformHistory.forEachIndexed { index, amp ->
            val x = index * step
            // Modulate positive and negative peaks for audio wave look
            val waveHeight = (amp * (height * 0.45f)).coerceAtLeast(2f)
            val direction = if (index % 2 == 0) -1 else 1
            val y = centerY + (waveHeight * direction)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            fillPath.lineTo(x, y)
        }

        fillPath.lineTo(width, centerY)
        fillPath.close()

        // Fill area beneath wave
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    CyanPrimary.copy(alpha = 0.3f),
                    TealSecondary.copy(alpha = 0.05f)
                ),
                startY = 0f,
                endY = height
            )
        )

        // Draw glowing wave line
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    CyanPrimary,
                    TealSecondary,
                    CyanPrimary
                )
            ),
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}
