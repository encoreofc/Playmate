package com.playmate.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.playmate.app.ui.theme.Accent

private const val SHORTS_PATH =
    "M17.77,10.32l-1.2,-0.5L18,9.06c1.84,-0.96 2.53,-3.23 1.56,-5.06s-3.24,-2.53 -5.07,-1.56L6,6.94" +
        "c-1.29,0.68 -2.07,2.04 -2,3.49 0.07,1.42 0.93,2.67 2.22,3.25 0.03,0.01 1.2,0.5 1.2,0.5L6,14.93" +
        "c-1.83,0.97 -2.53,3.24 -1.56,5.07 0.97,1.83 3.24,2.53 5.07,1.56l8.5,-4.5c1.29,-0.68 2.06,-2.04 1.99,-3.49" +
        " -0.07,-1.42 -0.94,-2.68 -2.23,-3.25zM10,14.65v-5.3L15,12l-5,2.65z"

/** The real YouTube Shorts logo, drawn from its SVG path (24x24 design grid). */
@Composable
fun ShortsLogo(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val path = remember {
        PathParser().parsePathString(SHORTS_PATH).toPath().apply { fillType = PathFillType.EvenOdd }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val scaled = Path().apply { addPath(path); transform(androidx.compose.ui.graphics.Matrix().apply { scale(s, s) }) }
        drawPath(scaled, Color(0xFFFF0000))
    }
}

/** Playmate's own mark: rounded badge with a play triangle, in the app accent colour. */
@Composable
fun PlaymateMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 28.dp, height = 20.dp)) {
        val u = this.size.width / 28f
        drawRoundRect(Accent, size = this.size, cornerRadius = CornerRadius(8f * u, 8f * u))
        val tri = Path().apply {
            moveTo(11f * u, 5.4f * u)
            lineTo(11f * u, 14.6f * u)
            lineTo(19f * u, 10f * u)
            close()
        }
        drawPath(tri, Color.White)
    }
}
