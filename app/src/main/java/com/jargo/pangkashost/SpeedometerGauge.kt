package com.jargo.pangkashost

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WorkerGauge(
    workerTitle: String,
    progress: Float,
    speedValue: Int,
    unitLabel: String = "FPS",
    activeFileName: String = "Idle",
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "GaugeAnimation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = workerTitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64FFDA),
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val center = Offset(canvasWidth / 2, canvasHeight / 2)
                val strokeWidth = 10.dp.toPx()
                val radius = (canvasWidth - strokeWidth - 16.dp.toPx()) / 2

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

                val totalTicks = 20
                for (i in 0..totalTicks) {
                    val tickAngle = startAngle + (i.toFloat() / totalTicks) * totalSweepAngle
                    val angleRad = Math.toRadians(tickAngle.toDouble())
                    val isMajor = i % 5 == 0

                    val tickLength = if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                    val tickWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()

                    val outerR = radius - (strokeWidth / 2) - 4.dp.toPx()
                    val innerR = outerR - tickLength

                    val startX = center.x + outerR * cos(angleRad).toFloat()
                    val startY = center.y + outerR * sin(angleRad).toFloat()
                    val endX = center.x + innerR * cos(angleRad).toFloat()
                    val endY = center.y + innerR * sin(angleRad).toFloat()

                    drawLine(
                        color = if (i.toFloat() / totalTicks <= animatedProgress) Color(0xFF00E5FF) else Color(0xFF333A4E),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = tickWidth,
                        cap = StrokeCap.Round
                    )
                }

                val activeGradient = Brush.sweepGradient(
                    0.0f to Color(0xFF00F2FE),
                    0.7f to Color(0xFF4FACFE),
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
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$speedValue",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = unitLabel,
                    fontSize = 10.sp,
                    color = Color(0xFF888A99),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .width(140.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF141824))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = activeFileName,
                fontSize = 10.sp,
                color = if (activeFileName != "Idle") Color(0xFF64FFDA) else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
