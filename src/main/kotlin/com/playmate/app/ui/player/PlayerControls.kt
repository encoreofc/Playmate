package com.playmate.app.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.theme.Accent
import com.playmate.app.ui.theme.tonalPress

/*
 * The video player's own controls. Everything here is drawn in white over the picture, so it
 * looks the same in the light and the dark theme, and it is stateless: PlayerScreen owns the state.
 */

/** Space kept left and right of the track so the thumb never touches the screen edge. */
private val TrackInset = 10.dp

/** Height of the finger target of the seek bar. The line itself is only 3dp. */
val SeekBarTouchHeight = 32.dp

/**
 * The seek bar: a thin line with three layers (empty, buffered, played) and a round thumb.
 * Touching it starts scrubbing at once; while the finger is down the line and the thumb grow,
 * the way the YouTube player does it. A tap jumps to that point.
 *
 * [progress] and [buffered] are fractions from 0 to 1. With [enabled] false (live streams) it is
 * only drawn: no thumb and no touch.
 */
@Composable
fun PmSeekBar(
    progress: Float,
    buffered: Float,
    scrubbing: Boolean,
    enabled: Boolean,
    onScrubStart: (Float) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val track by animateDpAsState(if (scrubbing) 5.dp else 3.dp, tween(120), label = "seekTrack")
    val thumb by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            scrubbing -> 10.dp
            else -> 6.dp
        },
        animationSpec = tween(120),
        label = "seekThumb"
    )
    val halo by animateFloatAsState(if (scrubbing) 1f else 0f, tween(160), label = "seekHalo")
    // Playback reports its position a few times a second; this turns the steps into a smooth glide.
    val glide by animateFloatAsState(progress, tween(300, easing = LinearEasing), label = "seekGlide")
    val shown = if (scrubbing) progress else glide

    val start by rememberUpdatedState(onScrubStart)
    val move by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onScrubEnd)

    Box(
        modifier
            .fillMaxWidth()
            .height(SeekBarTouchHeight)
            .semantics {
                contentDescription = "Seek bar"
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        end(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val inset = TrackInset.toPx()
                    fun fractionOf(x: Float): Float {
                        val width = (size.width - inset * 2f).coerceAtLeast(1f)
                        return ((x - inset) / width).coerceIn(0f, 1f)
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var last = fractionOf(down.position.x)
                    start(last)
                    drag(down.id) { change ->
                        change.consume()
                        last = fractionOf(change.position.x)
                        move(last)
                    }
                    end(last)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = TrackInset.toPx()
            val width = (size.width - inset * 2f).coerceAtLeast(1f)
            val h = track.toPx()
            val cy = size.height / 2f
            val top = Offset(inset, cy - h / 2f)
            val round = CornerRadius(h / 2f, h / 2f)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = top,
                size = Size(width, h),
                cornerRadius = round
            )
            val loaded = buffered.coerceIn(0f, 1f)
            if (loaded > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.50f),
                    topLeft = top,
                    size = Size(width * loaded, h),
                    cornerRadius = round
                )
            }
            val played = shown.coerceIn(0f, 1f)
            drawRoundRect(
                color = Accent,
                topLeft = top,
                size = Size(width * played, h),
                cornerRadius = round
            )
            if (thumb > 0.dp) {
                val center = Offset(inset + width * played, cy)
                if (halo > 0f) {
                    drawCircle(
                        color = Accent.copy(alpha = 0.28f * halo),
                        radius = thumb.toPx() * 1.8f,
                        center = center
                    )
                }
                drawCircle(color = Accent, radius = thumb.toPx(), center = center)
            }
        }
    }
}

/**
 * The thin line that stays at the bottom edge of the picture while the controls are hidden,
 * so the position is always visible.
 */
@Composable
fun MiniProgressLine(progress: Float, buffered: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .background(Color.White.copy(alpha = 0.22f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(buffered.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.38f))
        )
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Accent)
        )
    }
}

/** "0:42 / 4:15": the position in full white, the total a step quieter. Digits keep a fixed width. */
@Composable
fun TimeLabel(position: Long, duration: Long, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = Color.White)) { append(formatClock(position)) }
        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.72f))) {
            append("  /  ")
            append(formatClock(duration))
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
        modifier = modifier
    )
}

/** The big time that follows the finger while scrubbing. */
@Composable
fun ScrubTime(positionMs: Long, modifier: Modifier = Modifier) {
    Text(
        formatClock(positionMs),
        style = TextStyle(
            color = Color.White,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum",
            shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 12f)
        ),
        modifier = modifier
    )
}

/** Red dot and LIVE: a live stream has no seeking, only this. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.38f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Accent)
        )
        Text(
            "LIVE",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** A plain white icon button for the corners of the picture. 44dp target, 24dp icon. */
@Composable
fun OverlayIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

enum class CenterAction { Play, Pause, Replay }

/** The one big button in the middle of the picture. Its icon fades between play, pause and replay. */
@Composable
fun CenterPlayButton(action: CenterAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(64.dp)
            .tonalPress(CircleShape, Color.Black.copy(alpha = 0.42f), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = action, animationSpec = tween(140), label = "centerAction") { current ->
            Icon(
                imageVector = when (current) {
                    CenterAction.Play -> Icons.Rounded.PlayArrow
                    CenterAction.Pause -> Icons.Rounded.Pause
                    CenterAction.Replay -> Icons.Rounded.Replay
                },
                contentDescription = when (current) {
                    CenterAction.Play -> "Play"
                    CenterAction.Pause -> "Pause"
                    CenterAction.Replay -> "Replay"
                },
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

/** Shown on the left or right side after a double tap: arrows and how far the video jumped. */
@Composable
fun SeekFeedback(forward: Boolean, seconds: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
        Text(
            "$seconds seconds",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Shown while a finger slides up or down on the left (brightness) or right (volume) half. */
@Composable
fun LevelFeedback(icon: ImageVector, percent: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            "$percent%",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
        )
    }
}

/** What replaces the controls when the video cannot play: the reason, and one calm Retry. */
@Composable
fun PlaybackErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(30.dp)
        )
        Text(
            message,
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Retry",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(top = 14.dp)
                .tonalPress(RoundedCornerShape(50), Color.White.copy(alpha = 0.18f), onClick = onRetry)
                .padding(horizontal = 22.dp, vertical = 9.dp)
        )
    }
}
