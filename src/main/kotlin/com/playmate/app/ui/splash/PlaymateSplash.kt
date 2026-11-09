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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.playmate.app.R
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

private val LogoSize = 168.dp
private val LineWidth = 2.dp
private val FlareRadius = 16.dp

// Timeline in milliseconds, about 0.95 s in all:
//   0 - 120    the mark appears, dark, with its edge faintly drawn
//   120 - 640  light runs once round the edge: top edge, right corner, bottom edge, bottom-left corner, left edge
//   560 - 740  the mark fills with its own colour and the play mark settles in
//   740 - 940  the screen lifts away and the app is there
private const val ENTER_END = 120f
private const val LAP_START = 120f
private const val LAP_END = 640f
private const val FILL_START = 560f
private const val FILL_END = 740f
private const val REVEAL_START = 740f
private const val TOTAL_MS = 940f

private const val HEAD_STEPS = 6
private const val HEAD_SPAN = 0.16f
private const val SPOT_RADIUS = 210f
private const val FLARE_LEAD = 0.035f
private const val FLARE_TAIL = 0.09f

private val Track = Color(0xFF9ED4A0)
private val Lit = Color(0xFFBFE6C2)
private val Glow = Color(0xFF9ED4A0)
private val Head = Color(0xFFF4FBF4)

// Nearly even speed, so the three edges take about the same time; it eases only at the very start and end.
private val LapEasing = Easing { t -> 0.6f * t + 0.4f * (t * t * (3f - 2f * t)) }

/**
 * Opening animation of the app. The logo is drawn here, in code, on the same background as the
 * system launch screen. It appears dark with only its edge traced; a light then runs once round
 * that edge through the three corners, lighting the edge and, a little inside it, the body of the
 * mark. When the lap closes the mark takes its colour and the screen lifts away. [onFinished] is
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
    val measure = remember { PathMeasure().also { it.setPath(LogoGeometry.silhouette, false) } }
    val segment = remember { Path() }

    LaunchedEffect(Unit) {
        // The first frames carry the cost of composing the whole app underneath. Let them go by
        // while only the background is shown, so the light itself always runs smoothly.
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
            val enter = FastOutSlowInEasing.transform(progress(ms, 0f, ENTER_END))
            val lap = LapEasing.transform(progress(ms, LAP_START, LAP_END))
            val fill = FastOutSlowInEasing.transform(progress(ms, FILL_START, FILL_END))
            val pop = FastOutSlowInEasing.transform(progress(ms, FILL_START + 50f, FILL_END))
            val reveal = FastOutSlowInEasing.transform(progress(ms, REVEAL_START, TOTAL_MS))

            val logoPx = LogoSize.toPx()
            val unit = logoPx / LogoGeometry.VIEW
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = (0.965f + 0.035f * enter) * (1f + 0.05f * reveal)
            withTransform({
                scale(scale, scale, pivot = center)
                translate(center.x - logoPx / 2f, center.y - logoPx / 2f)
                scale(unit, unit, pivot = Offset.Zero)
            }) {
                drawIntro(
                    measure = measure,
                    segment = segment,
                    enter = enter,
                    lap = lap,
                    fill = fill,
                    pop = pop,
                    lineWidth = LineWidth.toPx() / unit,
                    flareRadius = FlareRadius.toPx() / unit
                )
            }
        }
    }
}

private fun progress(ms: Float, from: Float, to: Float): Float =
    ((ms - from) / (to - from)).coerceIn(0f, 1f)

private fun DrawScope.drawIntro(
    measure: PathMeasure,
    segment: Path,
    enter: Float,
    lap: Float,
    fill: Float,
    pop: Float,
    lineWidth: Float,
    flareRadius: Float
) {
    val rim = LogoGeometry.rim

    // The body of the mark: dark at first, then taking its own colours.
    val body = 0.10f * enter + 0.90f * fill
    drawPath(LogoGeometry.upper, LogoGeometry.upperBrush, alpha = body)
    drawPath(LogoGeometry.lower, LogoGeometry.lowerBrush, alpha = body)
    val markScale = 0.88f + 0.12f * pop
    withTransform({ scale(markScale, markScale, pivot = LogoGeometry.markCenter) }) {
        drawPath(LogoGeometry.small, LogoGeometry.smallBrush, alpha = 0.10f * enter + 0.90f * pop)
    }

    // The edge, drawn faintly before the light has been along it.
    drawPath(
        LogoGeometry.silhouette, Track,
        alpha = 0.26f * enter * (1f - fill),
        style = Stroke(width = lineWidth, join = StrokeJoin.Round)
    )

    if (lap <= 0f) return
    val head = rim.length * lap
    val headPoint = rim.pointAt(measure, head)
    // Light is full along the lap and eases in and out at its two ends.
    val intensity = min(lap / 0.05f, (1f - lap) / 0.12f).coerceIn(0f, 1f)

    // Inside the glass: the body of the mark is lit around the head while it passes.
    if (intensity > 0f) {
        val spot = Brush.radialGradient(
            0f to Head.copy(alpha = 0.36f * intensity),
            1f to Color.Transparent,
            center = headPoint,
            radius = SPOT_RADIUS
        )
        clipPath(LogoGeometry.upper) { drawCircle(spot, radius = SPOT_RADIUS, center = headPoint) }
        clipPath(LogoGeometry.lower) { drawCircle(spot, radius = SPOT_RADIUS, center = headPoint) }
    }

    // The part of the edge the light has been along stays lit.
    val litAlpha = 0.78f * (1f - fill)
    if (litAlpha > 0f && rim.segment(measure, 0f, head, segment)) {
        drawPath(
            segment, Lit,
            alpha = litAlpha,
            style = Stroke(width = lineWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round)
        )
    }

    if (intensity > 0f) {
        // The head: a bright stretch that builds up towards its tip. The stretches overlap, so
        // the brightness rises smoothly instead of in a visible step.
        for (k in 0 until HEAD_STEPS) {
            val span = rim.length * HEAD_SPAN * (1f - k.toFloat() / HEAD_STEPS)
            if (rim.segment(measure, max(0f, head - span), head, segment)) {
                drawPath(
                    segment, Head,
                    alpha = 0.17f * intensity,
                    style = Stroke(width = lineWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round)
                )
            }
        }
        if (rim.segment(measure, max(0f, head - rim.length * 0.06f), head, segment)) {
            drawPath(
                segment, Glow,
                alpha = 0.10f * intensity,
                style = Stroke(width = lineWidth * 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                segment, Glow,
                alpha = 0.16f * intensity,
                style = Stroke(width = lineWidth * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        drawCircle(Head, radius = lineWidth * 0.62f, center = headPoint, alpha = intensity)
    }

    // A soft light at each corner as the head reaches it.
    for (corner in rim.corners) {
        val strength = flare(lap, corner.at) * (1f - fill)
        if (strength <= 0.01f) continue
        drawCircle(
            brush = Brush.radialGradient(
                0f to Glow.copy(alpha = 0.34f * strength),
                1f to Color.Transparent,
                center = corner.point,
                radius = flareRadius
            ),
            radius = flareRadius,
            center = corner.point
        )
    }
}

/** 0..1 light of a corner: it rises as the head comes up to it and fades once the head has passed. */
private fun flare(head: Float, at: Float): Float {
    val d = head - at
    return when {
        d < -FLARE_LEAD -> 0f
        d < 0f -> {
            val x = (d + FLARE_LEAD) / FLARE_LEAD
            x * x * (3f - 2f * x)
        }
        else -> exp(-d / FLARE_TAIL)
    }
}
