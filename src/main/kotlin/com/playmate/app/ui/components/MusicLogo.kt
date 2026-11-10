package com.playmate.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Music tab's icon in the bottom bar: a ring with a play triangle inside. When the tab is open
 * the ring fills in and the triangle is cut out of it. Drawn on a 24 grid, so it is sharp at any size.
 */
@Composable
fun MusicTabIcon(size: Dp, color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    val cut = MaterialTheme.colorScheme.background
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val center = Offset(12f * u, 12f * u)
        val triangle = Path().apply {
            moveTo(10f * u, 8.1f * u)
            lineTo(16.3f * u, 12f * u)
            lineTo(10f * u, 15.9f * u)
            close()
        }
        if (filled) {
            drawCircle(color, radius = 10f * u, center = center)
            drawPath(triangle, cut)
        } else {
            drawCircle(color, radius = 9.1f * u, center = center, style = Stroke(width = 1.8f * u))
            drawPath(triangle, color)
        }
    }
}

/** The YouTube Music mark: a red disc with a thin white ring and a white play triangle. */
@Composable
fun MusicMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val center = Offset(12f * u, 12f * u)
        drawCircle(Color(0xFFFF0000), radius = 12f * u, center = center)
        drawCircle(Color.White, radius = 7.6f * u, center = center, style = Stroke(width = 1.3f * u))
        val triangle = Path().apply {
            moveTo(10.1f * u, 8.6f * u)
            lineTo(15.6f * u, 12f * u)
            lineTo(10.1f * u, 15.4f * u)
            close()
        }
        drawPath(triangle, Color.White)
    }
}

/** The mark with the word "Music" next to it, for the top of the Music home. */
@Composable
fun MusicLogo(modifier: Modifier = Modifier, markSize: Dp = 26.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        MusicMark(size = markSize)
        Spacer(Modifier.width(4.dp))
        // YouTube Music's word is narrower than the system font: about 47 dp wide with a 16 dp tall M.
        Text(
            "Music",
            color = Color.White,
            style = TextStyle(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                textGeometricTransform = TextGeometricTransform(scaleX = 0.8f)
            )
        )
    }
}
