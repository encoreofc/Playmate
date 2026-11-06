package com.playmate.app.ui.player

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.playmate.app.ui.components.PmIcons
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Listen-only mode. One switch for the whole app while it runs: once it is on, the next video opens
 * as a cassette too. It is not saved, so a fresh start of the app begins with pictures again.
 */
object AudioMode {
    var on by mutableStateOf(false)
}

/** The headphones button in the corner of the player. Lit with the accent while listen-only is on. */
@Composable
fun AudioModeButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Accent.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(
                role = Role.Button,
                onClickLabel = if (active) "Show the video" else "Listen only",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            PmIcons.AudioMode,
            contentDescription = if (active) "Show the video" else "Listen only",
            tint = if (active) Accent else Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

private val StageBackground = Color(0xFF0C0C0E)
private val OnLight = Color(0xFF111111)

/**
 * What the picture turns into in listen-only mode: a dark stage, the cassette in the middle with
 * its reels turning while the sound plays, and the controls underneath. Stateless: PlayerScreen
 * owns the player and the state.
 */
@Composable
fun AudioStage(
    title: String,
    thumb: String?,
    isPlaying: Boolean,
    buffering: Boolean,
    ended: Boolean,
    live: Boolean,
    progress: Float,
    buffered: Float,
    positionMs: Long,
    durationMs: Long,
    scrubbing: Boolean,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onScrubStart: (Float) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onMinimise: () -> Unit,
    onShowVideo: () -> Unit,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier
            .background(StageBackground)
            // The picture and its controls sit underneath: nothing here may fall through to them.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // A soft wash of the video's own picture, only where the phone can blur it properly.
        if (thumb != null && Build.VERSION.SDK_INT >= 31) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp).alpha(0.5f)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.78f)))
                )
        )
        // The accent as a glow behind the cassette.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Accent.copy(alpha = 0.26f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.42f),
                            radius = size.width * 0.62f
                        )
                    )
                }
        )

        // Top: minimise, what this is, back to the picture, settings.
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverlayIconButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                description = "Minimise",
                onClick = onMinimise
            )
            Text(
                "AUDIO ONLY",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            AudioModeButton(active = true, onClick = onShowVideo)
            if (onSettings != null) {
                OverlayIconButton(icon = Icons.Rounded.Settings, description = "Settings", onClick = onSettings)
            }
        }

        // Middle: the cassette. A tap on it plays or pauses.
        val top = 48.dp
        val bottom = 124.dp
        val areaHeight = (maxHeight - top - bottom).coerceAtLeast(96.dp)
        val cassetteWidth = minOf(maxWidth * 0.74f, areaHeight * 1.6f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = top)
                .fillMaxWidth()
                .height(areaHeight),
            contentAlignment = Alignment.Center
        ) {
            Cassette(
                title = title,
                progress = if (live) 0.5f else progress,
                spinning = isPlaying && !buffering,
                modifier = Modifier
                    .width(cassetteWidth)
                    .height(cassetteWidth / 1.6f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = if (isPlaying) "Pause" else "Play",
                        onClick = onTogglePlay
                    )
            )
        }

        // Bottom: skip back, play or pause, skip forward; the seek bar; the times.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                    22.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (live) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    StageSkip(Icons.Rounded.Replay10, "Back 10 seconds") { onSeekBy(-10_000L) }
                }
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (isPlaying) "Pause" else "Play",
                            onClick = onTogglePlay
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (buffering) {
                        CircularProgressIndicator(color = OnLight, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                    } else {
                        Icon(
                            imageVector = when {
                                ended -> Icons.Rounded.Replay
                                isPlaying -> Icons.Rounded.Pause
                                else -> Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = OnLight,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                if (live) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    StageSkip(Icons.Rounded.Forward10, "Forward 10 seconds") { onSeekBy(10_000L) }
                }
            }
            PmSeekBar(
                progress = if (live) 1f else progress,
                buffered = if (live) 1f else buffered,
                scrubbing = scrubbing,
                enabled = !live && durationMs > 0,
                onScrubStart = onScrubStart,
                onScrub = onScrub,
                onScrubEnd = onScrubEnd,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shown = if (scrubbing) (progress * durationMs).toLong() else positionMs
                val clock = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
                if (live) {
                    LiveBadge()
                } else {
                    Text(formatClock(shown), color = Color.White.copy(alpha = 0.85f), style = clock)
                }
                Spacer(Modifier.weight(1f))
                if (!live) {
                    Text(formatClock(durationMs), color = Color.White.copy(alpha = 0.55f), style = clock)
                }
            }
        }
    }
}

@Composable
private fun StageSkip(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(28.dp))
    }
}

// ------------------------------------------------------------------ the cassette

/**
 * A cassette drawn in code: charcoal body, a cream label with the video's title under an accent
 * stripe, and the tape window with its two reels. The reels turn while [spinning] (they speed up
 * and slow down smoothly, never jump), and the tape moves from the left reel to the right one as
 * the video plays, so [progress] can be read off the cassette itself.
 */
@Composable
fun Cassette(
    title: String,
    progress: Float,
    spinning: Boolean,
    modifier: Modifier = Modifier
) {
    val angle = remember { mutableFloatStateOf(0f) }
    val speed = remember { Animatable(0f) }

    // The loop only runs while something is moving; a paused cassette costs no frames.
    LaunchedEffect(spinning) {
        coroutineScope {
            launch { speed.animateTo(if (spinning) 1f else 0f, tween(if (spinning) 500 else 900)) }
            var last = withFrameNanos { it }
            while (isActive && (spinning || speed.value > 0.003f)) {
                val now = withFrameNanos { it }
                val seconds = (now - last) / 1_000_000_000f
                last = now
                angle.floatValue = (angle.floatValue + seconds * 140f * speed.value) % 360f
            }
        }
    }

    BoxWithConstraints(modifier) {
        val width = maxWidth
        val height = maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 22.dp,
                    shape = RoundedCornerShape(percent = 8),
                    ambientColor = Color.Black,
                    spotColor = Accent.copy(alpha = 0.55f)
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Reels turn anticlockwise when a tape plays.
                drawCassette(angleDeg = -angle.floatValue, progress = progress.coerceIn(0f, 1f))
            }
        }
        // The label: the title, in the space between the stripe and the tape window.
        Text(
            title,
            color = Color(0xFF24201A),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = (width.value * 0.056f).sp,
                lineHeight = (width.value * 0.068f).sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = width * 0.0875f, end = width * 0.0875f, top = height * 0.165f)
        )
    }
}

private fun DrawScope.drawCassette(angleDeg: Float, progress: Float) {
    // Everything below is laid out on a 160 x 100 grid and scaled to the size it is drawn at.
    val u = size.width / 160f
    fun at(x: Float, y: Float) = Offset(x * u, y * u)
    fun box(w: Float, h: Float) = Size(w * u, h * u)
    fun round(v: Float) = CornerRadius(v * u, v * u)

    val cream = Color(0xFFF2EEE6)
    val dark = Color(0xFF15110F)
    val tape = Color(0xFF3B2B22)

    // Body, with a little light from the top left and a thin edge.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF34343A), Color(0xFF1B1B1F))),
        size = size,
        cornerRadius = round(8f)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            start = Offset.Zero,
            end = Offset(size.width * 0.6f, size.height * 0.6f)
        ),
        size = size,
        cornerRadius = round(8f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.14f),
        size = size,
        cornerRadius = round(8f),
        style = Stroke(width = 0.8f * u)
    )

    // Label with the accent stripe.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFEEE8DA), Color(0xFFDDD5C3)),
            startY = 7f * u,
            endY = 76f * u
        ),
        topLeft = at(8f, 7f),
        size = box(144f, 69f),
        cornerRadius = round(4f)
    )
    drawRoundRect(color = Accent, topLeft = at(8f, 7f), size = box(144f, 8f), cornerRadius = round(4f))
    drawRect(color = Accent, topLeft = at(8f, 11f), size = box(144f, 1f))
    drawRect(color = Accent.copy(alpha = 0.45f), topLeft = at(8f, 13.2f), size = box(144f, 1f))

    // Tape window, and the tape on the two reels inside it.
    val windowLeft = 34f * u
    val windowTop = 44f * u
    val windowRight = 126f * u
    val windowBottom = 70f * u
    drawRoundRect(color = dark, topLeft = at(34f, 44f), size = box(92f, 26f), cornerRadius = round(13f))

    val leftTape = 12.6f - 4f * progress
    val rightTape = 8.6f + 4f * progress
    val window = Path().apply {
        addRoundRect(RoundRect(windowLeft, windowTop, windowRight, windowBottom, 13f * u, 13f * u))
    }
    clipPath(window) {
        drawCircle(color = tape, radius = leftTape * u, center = at(52f, 57f))
        drawCircle(color = tape, radius = rightTape * u, center = at(108f, 57f))
        drawCircle(
            color = Color.White.copy(alpha = 0.07f),
            radius = leftTape * u,
            center = at(52f, 57f),
            style = Stroke(width = 0.7f * u)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.07f),
            radius = rightTape * u,
            center = at(108f, 57f),
            style = Stroke(width = 0.7f * u)
        )
        drawLine(
            color = tape,
            start = at(52f, 57f + leftTape),
            end = at(108f, 57f + rightTape),
            strokeWidth = 1.2f * u
        )
    }

    // The two hubs: a pale ring, a dark hole, six teeth pointing in, and a mark that shows the turning.
    fun hub(cx: Float, cy: Float) {
        val centre = at(cx, cy)
        drawCircle(color = cream, radius = 7.4f * u, center = centre)
        drawCircle(color = dark, radius = 4.3f * u, center = centre)
        for (i in 0 until 6) {
            rotate(degrees = angleDeg + i * 60f, pivot = centre) {
                drawLine(
                    color = cream,
                    start = at(cx, cy - 4.5f),
                    end = at(cx, cy - 2.9f),
                    strokeWidth = 1.5f * u,
                    cap = StrokeCap.Round
                )
            }
        }
        rotate(degrees = angleDeg, pivot = centre) {
            drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 0.8f * u, center = at(cx, cy - 6f))
        }
    }
    hub(52f, 57f)
    hub(108f, 57f)

    // A faint glass sheen over the window, and its edge.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            startY = windowTop,
            endY = windowBottom
        ),
        topLeft = at(34f, 44f),
        size = box(92f, 26f),
        cornerRadius = round(13f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = at(34f, 44f),
        size = box(92f, 26f),
        cornerRadius = round(13f),
        style = Stroke(width = 0.7f * u)
    )

    // The darker foot of the cassette, with its holes.
    val foot = Path().apply {
        moveTo(30f * u, 100f * u)
        lineTo(40f * u, 78f * u)
        lineTo(120f * u, 78f * u)
        lineTo(130f * u, 100f * u)
        close()
    }
    drawPath(foot, color = Color(0xFF111114))
    drawPath(foot, color = Color.White.copy(alpha = 0.08f), style = Stroke(width = 0.7f * u))
    for (x in listOf(58f, 102f)) {
        drawCircle(color = Color(0xFF050506), radius = 3.3f * u, center = at(x, 91f))
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = 3.3f * u,
            center = at(x, 91f),
            style = Stroke(width = 0.6f * u)
        )
    }
    drawRoundRect(color = Color(0xFF050506), topLeft = at(76f, 86f), size = box(8f, 8f), cornerRadius = round(1.4f))

    // Four screws.
    for (p in listOf(4.2f to 4.2f, 155.8f to 4.2f, 4.2f to 95.8f, 155.8f to 95.8f)) {
        val centre = at(p.first, p.second)
        drawCircle(color = Color(0xFF4A4A52), radius = 2.3f * u, center = centre)
        rotate(degrees = 45f, pivot = centre) {
            drawLine(
                color = Color(0xFF1B1B1F),
                start = at(p.first - 1.6f, p.second),
                end = at(p.first + 1.6f, p.second),
                strokeWidth = 0.7f * u
            )
        }
    }
}
