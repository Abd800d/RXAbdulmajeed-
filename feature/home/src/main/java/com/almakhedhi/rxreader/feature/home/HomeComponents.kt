package com.almakhedhi.rxreader.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.designsystem.theme.TealGradientEnd
import com.almakhedhi.rxreader.core.designsystem.theme.TealGradientStart
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

/** Teal gradient button matching the reference design. */
@Composable
fun GradientButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(TealGradientStart, TealGradientEnd)),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Decorative ECG heartbeat line used in the hero banner and footer. */
@Composable
fun EcgLine(
    modifier: Modifier = Modifier,
    color: Color = TealPrimary,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val points = listOf(
            0.00f to 0.50f,
            0.28f to 0.50f,
            0.33f to 0.15f,
            0.38f to 0.85f,
            0.43f to 0.50f,
            0.62f to 0.50f,
            0.66f to 0.30f,
            0.70f to 0.70f,
            0.74f to 0.50f,
            1.00f to 0.50f,
        )
        val path = Path().apply {
            moveTo(points[0].first * w, points[0].second * h)
            points.drop(1).forEach { (x, y) -> lineTo(x * w, y * h) }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
