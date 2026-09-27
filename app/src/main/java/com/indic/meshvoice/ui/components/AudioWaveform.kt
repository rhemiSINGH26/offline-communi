package com.indic.meshvoice.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.indic.meshvoice.ui.theme.CyberCyan
import com.indic.meshvoice.ui.theme.NeonEmerald

@Composable
fun AudioWaveform(
    isRecording: Boolean,
    audioLevel: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    activeColor: Color = CyberCyan,
    peakColor: Color = NeonEmerald
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animationDelay = (i * 70)
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400 + (i % 4) * 100, delayMillis = animationDelay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val currentHeight = if (isRecording) {
                (12.dp + 36.dp * (audioLevel * 0.7f + animatedHeight * 0.3f))
            } else {
                6.dp
            }

            val color = if (i % 2 == 0) activeColor else peakColor

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(currentHeight)
                    .clip(RoundedCornerShape(50))
                    .background(if (isRecording) color else Color.Gray.copy(alpha = 0.3f))
            )
        }
    }
}
