package com.playmate.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.playmate.app.R
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Layout of the intro. The logo is drawn at the same size and place as the launch screen the
// system shows (see res/drawable/ic_splash_icon.xml), so the hand-over between the two is invisible.
private val LogoSize = 160.dp
private val RingGap = 5.dp
private val RingWidth = 1.8.dp
private val FlareRadius = 12.dp

// Timing: one lap round the three corners, then the reveal of the app. About 0.9 s in all.
private const val LAP_MS = 640
private const val REVEAL_MS = 240

private const val TRAIL_STEPS = 24
private const val SEAM = 4f
private const val HEAD_SPAN = 0.07f
private const val FLARE_LEAD = 0.035f
private const val FLARE_TAIL = 0.10f

private val LineTail = Color(0xFF9ED4A0)
private val LineHead = Color(0xFFF4FBF4)

// Mostly even speed, so each of the three edges takes about the same time; it eases only at the very start and end.
private val LapEasing = Easing { t -> 0.6f * t + 0.4f * (t * t * (3f - 2f * t)) }

private val Ring: LogoRing by lazy {
    LogoRing.build(gap = RingGap.value / LogoSize.value * LogoGeometry.VIEW)
}

/**
 * Opening animation of the app. It starts on the still logo (the same picture as the system launch
 * screen), a thin line then runs once round the logo through its three corners, and the moment it
 * closes the screen lifts away and the app is there. [onFinished] is called when it is gone.
 *
 * While it plays it covers the whole window and swallows touches, so nothing underneath can be
 * tapped by accident.
 */
@Composable
fun PlaymateSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val ink = colorResource(R.color.pm_ink)
    val lap = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    val measure = remember { PathMeasure().also { it.setPath(Ring.path, false) } }
    val segment = remember { Path() }

    LaunchedEffect(Unit) {
        // The first frames carry the cost of composing the whole app underneath. Let them go by
        // while the still logo is on screen, so the line itself always runs smoothly.
        repeat(2) { withFrameNanos { } }
        lap.animateTo(1f, tween(LAP_MS, easing = LapEasing))
        reveal.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
        finished()
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - reveal.value }
            .background(ink)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val logoPx = LogoSize.toPx()
            val unit = logoPx / LogoGeometry.VIEW
            val center = Offset(size.width / 2f, size.height / 2f)
            val settle = 1f + 0.05f * reveal.value
            withTransform({
                scale(settle, settle, pivot = center)
                translate(center.x - logoPx / 2f, center.y - logoPx / 2f)
                scale(unit, unit, pivot = Offset.Zero)
            }) {
                drawLogo()
                drawRing(
                    ring = Ring,
                    measure = measure,
                    segment = segment,
                    progress = lap.value,
                    lineWidth = RingWidth.toPx() / unit,
                    flareRadius = FlareRadius.toPx() / unit,
                    ink = ink
                )
            }
        }
    }
}

private fun DrawScope.drawLogo() {
    drawPath(LogoGeometry.upper, LogoGeometry.upperBrush)
    drawPath(LogoGeometry.lower, LogoGeometry.lowerBrush)
    drawPath(LogoGeometry.small, LogoGeometry.smallBrush)
}

private fun DrawScope.drawRing(
    ring: LogoRing,
    measure: PathMeasure,
    segment: Path,
    progress: Float,
    lineWidth: Float,
    flareRadius: Float,
    ink: Color
) {
    if (progress <= 0f) return
    val head = ring.length * progress
    val trail = Stroke(width = lineWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round)

    // What the line has already drawn stays on screen: dim where it started, bright at the head.
    // Each step is one opaque colour (the dimming is mixed with the flat background), and the steps
    // overlap by a hair, so no seams show between them.
    for (step in 0 until TRAIL_STEPS) {
        val from = head * step / TRAIL_STEPS
        val to = min(head * (step + 1) / TRAIL_STEPS + SEAM, head)
        val f = (step + 0.5f) / TRAIL_STEPS
        val strength = 0.22f + 0.78f * f.pow(1.6f)
        segment.reset()
        if (measure.getSegment(from, to, segment, true)) {
            drawPath(segment, lerp(ink, lerp(LineTail, LineHead, f), strength), style = trail)
        }
    }

    // The head itself: a short, bright stretch with a soft halo.
    segment.reset()
    if (measure.getSegment(max(0f, head - ring.length * HEAD_SPAN), head, segment, true)) {
        drawPath(
            segment, LineTail.copy(alpha = 0.10f),
            style = Stroke(width = lineWidth * 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            segment, LineTail.copy(alpha = 0.18f),
            style = Stroke(width = lineWidth * 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            segment, LineHead,
            style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // A soft light at each corner as the head reaches it.
    for (corner in ring.corners) {
        val strength = flare(progress, corner.at)
        if (strength <= 0.01f) continue
        drawCircle(
            brush = Brush.radialGradient(
                0f to LineTail.copy(alpha = 0.34f * strength),
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
