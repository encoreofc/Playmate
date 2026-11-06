package com.playmate.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The thumbs YouTube itself draws, on its 24x24 grid: an outline when off and a solid shape when on,
 * so "liked" and "not liked" can never be mistaken for each other. The dislike thumbs are the
 * same drawings turned half a circle.
 */
object YtIcons {

    private const val THUMB_OUTLINE =
        "M18.77,11h-4.23l1.52-4.94C16.38,5.03,15.54,4,14.38,4c-0.58,0-1.14,0.24-1.52,0.65L7,11H3v10h4h1h9.43" +
            "c1.06,0,1.98-0.67,2.19-1.61l1.34-6C21.23,12.15,20.18,11,18.77,11z M7,20H4v-8h3V20z " +
            "M19.98,13.17l-1.34,6C18.54,19.65,18.03,20,17.43,20H8v-8.61l5.6-6.06C13.79,5.12,14.08,5,14.38,5" +
            "c0.26,0,0.5,0.11,0.63,0.3c0.07,0.1,0.15,0.26,0.09,0.47l-1.52,4.94L13.18,12h1.35h4.23" +
            "c0.41,0,0.8,0.17,1.03,0.46C19.92,12.61,20.05,12.86,19.98,13.17z"

    private const val THUMB_SOLID =
        "M3,11h3v10H3V11z M18.77,11h-4.23l1.52-4.94C16.38,5.03,15.54,4,14.38,4c-0.58,0-1.14,0.24-1.52,0.65" +
            "L7,11v10h10.43c1.06,0,1.98-0.67,2.19-1.61l1.34-6C21.23,12.15,20.18,11,18.77,11z"

    private fun thumb(name: String, d: String, down: Boolean): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        if (down) builder.addGroup(rotate = 180f, pivotX = 12f, pivotY = 12f)
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black)
        )
        if (down) builder.clearGroup()
        return builder.build()
    }

    val ThumbUp: ImageVector by lazy { thumb("YtThumbUp", THUMB_OUTLINE, down = false) }
    val ThumbUpSolid: ImageVector by lazy { thumb("YtThumbUpSolid", THUMB_SOLID, down = false) }
    val ThumbDown: ImageVector by lazy { thumb("YtThumbDown", THUMB_OUTLINE, down = true) }
    val ThumbDownSolid: ImageVector by lazy { thumb("YtThumbDownSolid", THUMB_SOLID, down = true) }
}

private val SparkRed = Color(0xFFFF4E45)
private val SparkAmber = Color(0xFFFFA62B)

/**
 * A thumb that answers a tap the way YouTube's does. Turning on: the outline swaps for the solid
 * thumb, which drops small, tilts back up with a bounce, while short red rays fly outward and fade.
 * Turning off: the thumb dips and settles, no rays. Nothing plays when it first appears already
 * on; [resetKey] (a comment id, say) tells it a different item has taken its place.
 */
@Composable
fun AnimatedThumb(
    on: Boolean,
    outline: ImageVector,
    solid: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    rays: Boolean = true,
    reach: Dp = 11.dp,
    resetKey: Any? = null,
    description: String? = null
) {
    val pop = remember(resetKey) { Animatable(1f) }
    val tilt = remember(resetKey) { Animatable(0f) }
    val spark = remember(resetKey) { Animatable(0f) }
    var seen by remember(resetKey) { mutableStateOf(on) }

    LaunchedEffect(resetKey, on) {
        if (on == seen) return@LaunchedEffect
        seen = on
        if (on) {
            coroutineScope {
                launch {
                    pop.snapTo(0.5f)
                    pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                }
                launch {
                    tilt.snapTo(-24f)
                    tilt.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
                }
                if (rays) {
                    launch {
                        spark.snapTo(0f)
                        spark.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
                    }
                }
            }
        } else {
            pop.snapTo(0.72f)
            pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }

    Box(
        modifier
            .size(size)
            .drawBehind {
                val p = spark.value
                if (p <= 0.001f || p >= 0.999f) return@drawBehind
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                val inner = this.size.minDimension * 0.42f
                val travel = reach.toPx()
                val head = inner + travel * p
                val tail = inner + travel * max(0f, (p - 0.3f) / 0.7f) * 0.9f
                val fade = (1f - p).coerceIn(0f, 1f)
                val rayCount = 8
                for (i in 0 until rayCount) {
                    val angle = (2.0 * PI * i / rayCount) - PI / 2
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(
                        color = (if (i % 2 == 0) SparkRed else SparkAmber).copy(alpha = fade),
                        start = Offset(centre.x + dx * tail, centre.y + dy * tail),
                        end = Offset(centre.x + dx * head, centre.y + dy * head),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (on) solid else outline,
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationZ = tilt.value
                    transformOrigin = TransformOrigin(0.25f, 0.9f)
                }
        )
    }
}

/** A count that rolls up or down when it changes, like the number next to YouTube's like. */
@Composable
fun RollingCount(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val up = targetState.length > initialState.length ||
                (targetState.length == initialState.length && targetState > initialState)
            val enter = slideInVertically(tween(220)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(220))
            val exit = slideOutVertically(tween(220)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(120))
            enter togetherWith exit
        },
        label = "rollingCount"
    ) { value ->
        Text(value, style = style, color = color, maxLines = 1)
    }
}
