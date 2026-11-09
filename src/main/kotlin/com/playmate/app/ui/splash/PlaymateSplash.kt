package com.playmate.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.playmate.app.R

private val LogoWidth = 168.dp

// Timeline in milliseconds, 1.1 s in all:
//   0 - 360     the tile comes up from slightly smaller and settles
//   220 - 500   the stem rises from below into place
//   340 - 620   the bar slides in from the left
//   460 - 780   the bowl closes round the bar from the right: the P is whole
//   780 - 920   the mark rests
//   920 - 1100  the screen lifts away and the app is there
private const val TILE_FADE_END = 200f
private const val TILE_END = 360f
private const val STEM_START = 220f
private const val STEM_END = 500f
private const val BAR_START = 340f
private const val BAR_END = 620f
private const val BOWL_START = 460f
private const val BOWL_END = 780f
private const val REVEAL_START = 920f
private const val TOTAL_MS = 1100f

private const val TILE_FROM = 0.90f
private const val FADE_SHARE = 0.4f
private const val LIFT = 0.05f

// Every movement slows to a stop and never overshoots.
private val OutQuart = Easing { t ->
    val u = 1f - t
    1f - u * u * u * u
}
private val Smooth = Easing { t -> t * t * (3f - 2f * t) }

/**
 * Opening animation of the app. The mark is drawn here, in code, on the same background as the
 * system launch screen: the tile settles, then the three parts of the P come in one after another
 * and join, and the screen lifts away. Colours are the logo's own flat colours. [onFinished] is
 * called when it is gone.
 *
 * It covers the whole window and swallows touches while it plays, so nothing underneath can be
 * tapped by accident.
 */
@Composable
fun PlaymateSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val ink = colorResource(R.color.pm_ink)
    val clock = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        // The first frames carry the cost of composing the whole app underneath. Let them go by
        // while only the background is shown, so the movement itself always runs smoothly.
        repeat(2) { withFrameNanos { } }
        clock.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
        finished()
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - FastOutSlowInEasing.transform(progress(clock.value, REVEAL_START, TOTAL_MS))
            }
            .background(ink)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ms = clock.value
            val settle = OutQuart.transform(progress(ms, 0f, TILE_END))
            val tileAlpha = Smooth.transform(progress(ms, 0f, TILE_FADE_END))
            val reveal = FastOutSlowInEasing.transform(progress(ms, REVEAL_START, TOTAL_MS))

            val unit = LogoWidth.toPx() / LogoGeometry.WIDTH
            val logoWidth = LogoGeometry.WIDTH * unit
            val logoHeight = LogoGeometry.HEIGHT * unit
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = (TILE_FROM + (1f - TILE_FROM) * settle) * (1f + LIFT * reveal)

            withTransform({
                scale(scale, scale, pivot = center)
                translate(center.x - logoWidth / 2f, center.y - logoHeight / 2f)
                scale(unit, unit, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = LogoGeometry.Yellow,
                    size = Size(LogoGeometry.WIDTH, LogoGeometry.HEIGHT),
                    cornerRadius = CornerRadius(LogoGeometry.TILE_RADIUS),
                    alpha = tileAlpha
                )
                part(LogoGeometry.stem, LogoGeometry.stemFrom, ms, STEM_START, STEM_END)
                part(LogoGeometry.bar, LogoGeometry.barFrom, ms, BAR_START, BAR_END)
                part(LogoGeometry.bowl, LogoGeometry.bowlFrom, ms, BOWL_START, BOWL_END)
            }
        }
    }
}

private fun progress(ms: Float, from: Float, to: Float): Float =
    ((ms - from) / (to - from)).coerceIn(0f, 1f)

/**
 * One part of the P on its way in: it starts [from] away from its place, comes to rest exactly
 * there, and becomes visible during the first part of the move.
 */
private fun DrawScope.part(path: Path, from: Offset, ms: Float, start: Float, end: Float) {
    val p = progress(ms, start, end)
    if (p <= 0f) return
    val rest = 1f - OutQuart.transform(p)
    val alpha = Smooth.transform((p / FADE_SHARE).coerceIn(0f, 1f))
    translate(left = from.x * rest, top = from.y * rest) {
        drawPath(path, LogoGeometry.Black, alpha = alpha)
    }
}
