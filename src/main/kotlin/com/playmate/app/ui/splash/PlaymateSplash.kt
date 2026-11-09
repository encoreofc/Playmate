package com.playmate.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.playmate.app.R
import kotlin.math.hypot

private val LogoWidth = 168.dp

// Timeline in milliseconds, about 1.2 s in all:
//   0 - 760     the mark turns clockwise into place while a sweep, starting at twelve o'clock,
//               goes once round it and uncovers it: slow at first, then free, then easing out
//   760 - 860   the turn and the size come to rest
//   860 - 1000  the whole mark rests
//   1000 - 1180 the screen lifts away and the app is there
private const val SWEEP_END = 760f
private const val SETTLE_END = 860f
private const val REVEAL_START = 1000f
private const val TOTAL_MS = 1180f

private const val TURN_DEG = 40f
private const val SCALE_FROM = 0.88f
private const val LIFT = 0.05f

// Width of the sweep's leading edge, as a share of the full circle: just enough to keep it smooth.
private const val EDGE = 0.002f
private const val SWEEP_FULL = 0.985f

// Starts slowly, then takes up speed and slows to a stop.
private val SweepEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

// Slows to a stop and never overshoots.
private val OutQuart = Easing { t ->
    val u = 1f - t
    1f - u * u * u * u
}

/**
 * Opening animation of the app. The mark is drawn here, in code, on the same background as the
 * system launch screen. A sweep starts at twelve o'clock and goes once round the mark, uncovering
 * it as it turns into place; then the screen lifts away. Colours are the logo's own flat colours.
 * [onFinished] is called when it is gone.
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
        // Drawn in its own layer, so the sweep can cut the mark without touching the background.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val ms = clock.value
            // Reaches the full circle a little before the easing ends, so the last sliver closes cleanly.
            val sweep = (SweepEasing.transform(progress(ms, 0f, SWEEP_END)) / SWEEP_FULL).coerceAtMost(1f)
            if (sweep <= 0f) return@Canvas
            val settle = OutQuart.transform(progress(ms, 0f, SETTLE_END))
            val reveal = FastOutSlowInEasing.transform(progress(ms, REVEAL_START, TOTAL_MS))

            val unit = LogoWidth.toPx() / LogoGeometry.WIDTH
            val logoWidth = LogoGeometry.WIDTH * unit
            val logoHeight = LogoGeometry.HEIGHT * unit
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = (SCALE_FROM + (1f - SCALE_FROM) * settle) * (1f + LIFT * reveal)

            withTransform({
                rotate(-TURN_DEG * (1f - settle), pivot = center)
                scale(scale, scale, pivot = center)
                translate(center.x - logoWidth / 2f, center.y - logoHeight / 2f)
                scale(unit, unit, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = LogoGeometry.Yellow,
                    size = Size(LogoGeometry.WIDTH, LogoGeometry.HEIGHT),
                    cornerRadius = CornerRadius(LogoGeometry.TILE_RADIUS)
                )
                drawPath(LogoGeometry.stem, LogoGeometry.Black)
                drawPath(LogoGeometry.bar, LogoGeometry.Black)
                drawPath(LogoGeometry.bowl, LogoGeometry.Black)
            }

            // The sweep: keeps the mark only inside the angle it has gone round so far, counted
            // clockwise from twelve o'clock. Once it has gone all the way round nothing is cut.
            if (sweep < 1f) {
                val mask = Brush.sweepGradient(
                    0f to Color.Black,
                    sweep to Color.Black,
                    (sweep + EDGE).coerceAtMost(1f) to Color.Transparent,
                    1f to Color.Transparent,
                    center = center
                )
                val reach = hypot(size.width, size.height)
                // A sweep gradient starts at three o'clock; turn it a quarter back to start at twelve.
                rotate(-90f, pivot = center) {
                    drawRect(
                        brush = mask,
                        topLeft = Offset(center.x - reach, center.y - reach),
                        size = Size(reach * 2f, reach * 2f),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
        }
    }
}

private fun progress(ms: Float, from: Float, to: Float): Float =
    ((ms - from) / (to - from)).coerceIn(0f, 1f)
