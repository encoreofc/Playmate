package com.playmate.app.ui.components

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playmate.app.ui.splash.LogoGeometry
import com.playmate.app.ui.theme.LocalPmDark

private const val SHORTS_PATH =
    "M17.77,10.32l-1.2,-0.5L18,9.06c1.84,-0.96 2.53,-3.23 1.56,-5.06s-3.24,-2.53 -5.07,-1.56L6,6.94" +
        "c-1.29,0.68 -2.07,2.04 -2,3.49 0.07,1.42 0.93,2.67 2.22,3.25 0.03,0.01 1.2,0.5 1.2,0.5L6,14.93" +
        "c-1.83,0.97 -2.53,3.24 -1.56,5.07 0.97,1.83 3.24,2.53 5.07,1.56l8.5,-4.5c1.29,-0.68 2.06,-2.04 1.99,-3.49" +
        " -0.07,-1.42 -0.94,-2.68 -2.23,-3.25zM10,14.65v-5.3L15,12l-5,2.65z"

/** The real YouTube Shorts logo, drawn from its SVG path (24x24 design grid). Red unless [color] says otherwise. */
@Composable
fun ShortsLogo(size: Dp = 28.dp, modifier: Modifier = Modifier, color: Color = Color(0xFFFF0000)) {
    val path = remember {
        PathParser().parsePathString(SHORTS_PATH).toPath().apply { fillType = PathFillType.EvenOdd }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val scaled = Path().apply { addPath(path); transform(androidx.compose.ui.graphics.Matrix().apply { scale(s, s) }) }
        drawPath(scaled, color)
    }
}

/**
 * Playmate's own mark: the yellow rounded tile with the black "P", drawn from the same geometry as
 * the opening animation and the launcher icon ([LogoGeometry]), so it is always exactly the
 * approved logo at any size. Flat colours, no image. [width] is the tile's width; its height
 * follows the logo's own proportions.
 */
@Composable
fun PlaymateMark(modifier: Modifier = Modifier, width: Dp = 26.dp) {
    Canvas(modifier.size(width = width, height = width * (LogoGeometry.HEIGHT / LogoGeometry.WIDTH))) {
        val unit = this.size.width / LogoGeometry.WIDTH
        scale(unit, unit, pivot = Offset.Zero) {
            drawRoundRect(
                color = LogoGeometry.Yellow,
                size = androidx.compose.ui.geometry.Size(LogoGeometry.WIDTH, LogoGeometry.HEIGHT),
                cornerRadius = CornerRadius(LogoGeometry.TILE_RADIUS)
            )
            drawPath(LogoGeometry.stem, LogoGeometry.Black)
            drawPath(LogoGeometry.bar, LogoGeometry.Black)
            drawPath(LogoGeometry.bowl, LogoGeometry.Black)
        }
    }
}

/** The wordmark font: Roboto Condensed Bold (built into Android), a clean, serious face. */
private val LogoFont = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

/** The brand yellow is too pale to read as text on the light theme, so there "mate" uses a deeper gold of the same hue. */
private val GoldOnLight = Color(0xFF9A6F00)

/**
 * The full Playmate lockup: the mark, then "Playmate" with "mate" in the brand yellow.
 * Used at the top of Home and Subscriptions.
 */
@Composable
fun PlaymateLogo(
    modifier: Modifier = Modifier,
    markWidth: Dp = 26.dp,
    textSize: TextUnit = 21.sp
) {
    val mate = if (LocalPmDark.current) LogoGeometry.Yellow else GoldOnLight
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PlaymateMark(width = markWidth)
        Spacer(Modifier.width(markWidth * 0.3f))
        Text(
            text = buildAnnotatedString {
                append("Play")
                withStyle(SpanStyle(color = mate)) { append("mate") }
            },
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = LogoFont,
                fontSize = textSize,
                lineHeight = textSize * 1.24f,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        )
    }
}
