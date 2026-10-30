package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed
import com.example.ui.theme.VuYellow

@Composable
fun VuMeter(
    rmsDb: Float,
    peakDb: Float,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isMuted) 0f else ((rmsDb + 60f) / 60f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60),
        label = "vu_level"
    )

    val animatedPeak by animateFloatAsState(
        targetValue = if (isMuted) 0f else ((peakDb + 60f) / 60f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100),
        label = "vu_peak"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B141F))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NÍVEL DE ÁUDIO (VU METER)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = if (isMuted) "MUTADO" else String.format("%.1f dBFS", rmsDb),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (isMuted) Color(0xFFFF9100) else if (rmsDb > -6f) VuRed else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF152233))
        ) {
            // Level bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedLevel)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                VuGreen,
                                VuGreen,
                                VuYellow,
                                VuRed
                            )
                        )
                    )
            )

            // Peak indicator line
            if (animatedPeak > 0.05f && !isMuted) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .padding(horizontal = 0.dp)
                        .align(Alignment.CenterStart)
                        .padding(start = (animatedPeak * 280).dp.coerceAtMost(280.dp))
                        .background(Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // dB Tick marks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-60 dB", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("-36 dB", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("-18 dB", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("-6 dB", style = MaterialTheme.typography.labelSmall, color = VuYellow, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("0 dB", style = MaterialTheme.typography.labelSmall, color = VuRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
