package com.jargo.pangkashost

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerGauge(
    progress: Float,
    speedValue: Int,
    unitLabel: String = "FPS",
    gearLabel: String = "READY",
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "GaugeAnimation"
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val center = Offset(canvasWidth / 2, canvasHeight / 2)
            val strokeWidth = 14.dp.toPx()
            val radius = (canvasWidth - strokeWidth - 28.dp.toPx()) / 2

            val startAngle = 140f
            val totalSweepAngle = 260f

            drawArc(
                color = Color(0xFF1E2230),
                startAngle = startAngle,
                sweepAngle = totalSweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )

            val totalTicks = 32
            for (i in 0..totalTicks) {
                val tickAngle = startAngle + (i.toFloat() / totalTicks) * totalSweepAngle
                val angleRad = Math.toRadians(tickAngle.toDouble())
                
                val isMajor = i % 4 == 0
                val isRedline = i >= totalTicks - 6

                val tickLength = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
                val tickWidth = if (isMajor) 3.dp.toPx() else 1.5f.dp.toPx()
                
                val tickColor = when {
                    isRedline -> Color(0xFFFF2A55)
                    i.toFloat() / totalTicks <= animatedProgress -> Color(0xFF00E5FF)
                    else -> Color(0xFF333A4E)
                }

                val outerR = radius - (strokeWidth / 2) - 6.dp.toPx()
                val innerR = outerR - tickLength

                val startX = center.x + outerR * cos(angleRad).toFloat()
                val startY = center.y + outerR * sin(angleRad).toFloat()
                val endX = center.x + innerR * cos(angleRad).toFloat()
                val endY = center.y + innerR * sin(angleRad).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }

            val activeGradient = Brush.sweepGradient(
                0.0f to Color(0xFF00F2FE),
                0.6f to Color(0xFF4FACFE),
                0.85f to Color(0xFFB026FF),
                1.0f to Color(0xFFFF0055),
                center = center
            )

            val currentSweep = totalSweepAngle * animatedProgress
            if (currentSweep > 0f) {
                drawArc(
                    brush = activeGradient,
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }

            if (animatedProgress > 0f) {
                val tipAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
                val tipX = center.x + radius * cos(tipAngleRad).toFloat()
                val tipY = center.y + radius * sin(tipAngleRad).toFloat()

                val tipColor = if (animatedProgress >= 0.85f) Color(0xFFFF0055) else Color(0xFF00F2FE)

                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(tipX, tipY)
                )
                drawCircle(
                    color = tipColor,
                    radius = 8.dp.toPx(),
                    center = Offset(tipX, tipY),
                    alpha = 0.6f
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (animatedProgress >= 0.85f) Color(0xFFFF0055).copy(alpha = 0.2f)
                        else Color(0xFF00E5FF).copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = gearLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (animatedProgress >= 0.85f) Color(0xFFFF5252) else Color(0xFF64FFDA),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$speedValue",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-2).sp
            )

            Text(
                text = unitLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF888A99),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
