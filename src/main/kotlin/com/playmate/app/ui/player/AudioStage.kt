package com.playmate.app.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playmate.app.ui.components.PmIcons
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.theme.Accent
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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

// ------------------------------------------------------------------ the stage

/**
 * What the picture turns into in listen-only mode: the video's own picture, blurred, as the room;
 * a cassette lying in it with its reels turning while the sound plays; and the controls underneath.
 * Stateless: PlayerScreen owns the player and the state.
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
    val context = LocalContext.current

    // A skip button winds the tape: the reels spin fast for a moment, the right way round.
    var windCount by remember { mutableIntStateOf(0) }
    var windDirection by remember { mutableFloatStateOf(1f) }
    val skip: (Long) -> Unit = { delta ->
        windDirection = if (delta >= 0L) 1f else -1f
        windCount += 1
        onSeekBy(delta)
    }

    BoxWithConstraints(
        modifier
            .background(StageBackground)
            // The picture and its controls sit underneath: nothing here may fall through to them.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // The room: the video's picture, blurred. Where the phone cannot blur (before Android 12)
        // the picture is decoded tiny and stretched, which gives the same soft wash.
        if (thumb != null) {
            val modern = Build.VERSION.SDK_INT >= 31
            val request = remember(thumb, modern) {
                ImageRequest.Builder(context)
                    .data(thumb)
                    .size(if (modern) 160 else 32, if (modern) 160 else 32)
                    .crossfade(500)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = RoomTint,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (modern) Modifier.blur(64.dp) else Modifier)
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Accent.copy(alpha = 0.22f), StageBackground)
                        )
                    )
            )
        }
        // Darken it so the cassette is the brightest thing here, then light the table under it.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.74f))
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.075f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.40f),
                            radius = size.width * 0.70f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            center = Offset(size.width / 2f, size.height * 0.42f),
                            radius = size.maxDimension * 0.75f
                        )
                    )
                    drawRect(brush = GrainBrush, alpha = 0.55f)
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

        // Middle: the cassette. A tap on it plays or pauses; it gives a little under the finger.
        val top = 48.dp
        val bottom = 124.dp
        val areaHeight = (maxHeight - top - bottom).coerceAtLeast(96.dp)
        val cassetteWidth = minOf(maxWidth * 0.86f, areaHeight * CASSETTE_ASPECT * 0.94f)
        val pressSource = remember { MutableInteractionSource() }
        val pressed by pressSource.collectIsPressedAsState()
        val press by animateFloatAsState(
            targetValue = if (pressed) 0.972f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "cassettePress"
        )
        val settle = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            settle.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 170f))
        }
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
                durationMs = durationMs,
                live = live,
                windCount = windCount,
                windDirection = windDirection,
                modifier = Modifier
                    .width(cassetteWidth)
                    .height(cassetteWidth / CASSETTE_ASPECT)
                    .graphicsLayer {
                        val scale = (0.92f + 0.08f * settle.value) * press
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable(
                        interactionSource = pressSource,
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
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (live) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    StageSkip(Icons.Rounded.Replay10, "Back 10 seconds") { skip(-10_000L) }
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
                    StageSkip(Icons.Rounded.Forward10, "Forward 10 seconds") { skip(10_000L) }
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

/** A touch more colour in the blurred room; blurring alone makes it look washed out. */
private val RoomTint: ColorFilter by lazy {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.3f) })
}

// ------------------------------------------------------------------ fine grain

/**
 * A small tile of film grain: faint white and black specks. Laid over the room, the plastic and the
 * paper it takes the digital smoothness off, and keeps big soft gradients from banding.
 */
private val GrainTile: ImageBitmap by lazy {
    val side = 256
    val random = Random(7)
    val pixels = IntArray(side * side) {
        val alpha = random.nextInt(0, 24)
        val v = if (random.nextBoolean()) 255 else 0
        (alpha shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
    bitmap.asImageBitmap()
}

private val GrainBrush: Brush by lazy {
    ShaderBrush(ImageShader(GrainTile, TileMode.Repeated, TileMode.Repeated))
}

// ------------------------------------------------------------------ the cassette

/*
 * The cassette is drawn at its real size: a Philips compact cassette is 100.4 x 63.8 mm, the hubs
 * are 44 mm apart, and every number below is a millimetre on that body. Light comes from the top
 * left. What never changes (shadow, plastic, label, screws, the frame of the window) is painted
 * once into bitmaps; only what moves (the tape and the hubs, the glass glare) is drawn each frame.
 */
private const val CW = 100.4f
private const val CH = 63.8f

/** Width over height of the cassette, as on the real thing. */
internal const val CASSETTE_ASPECT = CW / CH

private const val LABEL_X = 7.4f
private const val LABEL_Y = 4.0f
private const val LABEL_W = 85.6f
private const val LABEL_H = 40.0f
private const val LABEL_R = 1.4f

private const val WIN_X = 18.0f
private const val WIN_Y = 21.2f
private const val WIN_W = 64.4f
private const val WIN_H = 16.2f
private const val WIN_R = 6.0f

private const val HUB_L = 28.2f
private const val HUB_R = 72.2f
private const val HUB_Y = 29.3f

/** Radius of a pack with almost no tape on it, and of one holding nearly all of it. */
private const val TAPE_MIN = 8.2f
private const val TAPE_MAX = 17.0f

/** How fast the tape runs, in mm per second (the real one runs at 47.6). */
private const val TAPE_SPEED = 40f

/** How much faster the reels go at the start of a skip. */
private const val WIND_GAIN = 5f

private const val RAD_TO_DEG = 57.29578f

/**
 * The tape is one length that moves from the left pack to the right one, so what one pack loses
 * the other gains: the areas add up to a constant (not the radii), and the left pack is smaller
 * at the end than the right one is at the start only if the tape says so.
 */
private fun packLeft(progress: Float): Float =
    sqrt(TAPE_MAX * TAPE_MAX - progress * (TAPE_MAX * TAPE_MAX - TAPE_MIN * TAPE_MIN))

private fun packRight(progress: Float): Float =
    sqrt(TAPE_MIN * TAPE_MIN + progress * (TAPE_MAX * TAPE_MAX - TAPE_MIN * TAPE_MIN))

/** C-30, C-45, C-60, C-90 or C-120, by the length of the video, as on a real tape. */
private fun tapeType(durationMs: Long, live: Boolean): String {
    if (live) return "LIVE"
    val minutes = durationMs / 60_000L
    return when {
        minutes <= 0L -> "C-90"
        minutes <= 30L -> "C-30"
        minutes <= 45L -> "C-45"
        minutes <= 60L -> "C-60"
        minutes <= 90L -> "C-90"
        else -> "C-120"
    }
}

/** How far the phone is tipped, from -1 to 1 each way. Only the glare and the shadow follow it. */
private class TiltState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

@Composable
private fun rememberTilt(): TiltState {
    val context = LocalContext.current
    val tilt = remember { TiltState() }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var baseY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val g = SensorManager.GRAVITY_EARTH
                val rawX = (-event.values[0] / g).coerceIn(-1f, 1f)
                val rawY = (event.values[2] / g).coerceIn(-1f, 1f)
                // People hold a phone at their own angle: forward and back is measured from the
                // angle it has been held at lately, not from flat.
                if (baseY.isNaN()) baseY = rawY
                baseY += (rawY - baseY) * 0.004f
                val targetX = (rawX * 2.2f).coerceIn(-1f, 1f)
                val targetY = ((rawY - baseY) * 2.2f).coerceIn(-1f, 1f)
                val nextX = tilt.x + (targetX - tilt.x) * 0.12f
                val nextY = tilt.y + (targetY - tilt.y) * 0.12f
                if (abs(nextX - tilt.x) > 0.003f) tilt.x = nextX
                if (abs(nextY - tilt.y) > 0.003f) tilt.y = nextY
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return tilt
}

/**
 * A cassette that behaves like one. The reels turn the way a tape deck turns them: both
 * anticlockwise, each at the speed the tape needs (the nearly empty reel fast, the full one slow),
 * with a hair of wow and flutter, and they speed up and coast to a stop instead of jumping. The
 * tape moves from the left pack to the right one as the video plays, so [progress] can be read off
 * the cassette. [windCount] counts skips, [windDirection] says which way the last one went. Tipping
 * the phone slides the glare over the window and the shadow under the cassette.
 */
@Composable
fun Cassette(
    title: String,
    progress: Float,
    spinning: Boolean,
    modifier: Modifier = Modifier,
    durationMs: Long = 0L,
    live: Boolean = false,
    windCount: Int = 0,
    windDirection: Float = 1f
) {
    val angleLeft = remember { mutableFloatStateOf(0f) }
    val angleRight = remember { mutableFloatStateOf(0f) }
    val speed = remember { Animatable(0f) }
    val wind = remember { Animatable(0f) }
    val shown = remember { Animatable(progress.coerceIn(0f, 1f)) }
    val playing by rememberUpdatedState(spinning)
    val tilt = rememberTilt()

    LaunchedEffect(spinning) {
        speed.animateTo(
            targetValue = if (spinning) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (spinning) 520 else 900,
                easing = if (spinning) FastOutSlowInEasing else LinearOutSlowInEasing
            )
        )
    }
    LaunchedEffect(windCount) {
        if (windCount > 0) {
            wind.snapTo(windDirection)
            wind.animateTo(0f, tween(900, easing = LinearOutSlowInEasing))
        }
    }
    // The packs follow the player, and glide when it jumps.
    LaunchedEffect(progress) {
        val target = progress.coerceIn(0f, 1f)
        if (abs(target - shown.value) > 0.004f) {
            shown.animateTo(target, tween(500, easing = FastOutSlowInEasing))
        } else {
            shown.snapTo(target)
        }
    }
    // The loop sleeps while nothing moves: a paused cassette costs no frames.
    LaunchedEffect(Unit) {
        var last = 0L
        var clock = 0f
        while (isActive) {
            val idle = !playing && speed.value < 0.002f && abs(wind.value) < 0.002f
            if (idle) {
                snapshotFlow { playing || speed.value > 0.002f || abs(wind.value) > 0.002f }.first { it }
                last = 0L
            }
            val now = withFrameNanos { it }
            if (last == 0L) {
                last = now
                continue
            }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            clock += dt
            val flutter = 1f + 0.010f * sin(clock * 5.65f) + 0.005f * sin(clock * 2.1f)
            val rate = speed.value * flutter + wind.value * WIND_GAIN
            val p = shown.value
            // Same tape speed on both reels, so the smaller pack turns faster.
            angleLeft.floatValue =
                (angleLeft.floatValue - dt * rate * TAPE_SPEED / packLeft(p) * RAD_TO_DEG) % 360f
            angleRight.floatValue =
                (angleRight.floatValue - dt * rate * TAPE_SPEED / packRight(p) * RAD_TO_DEG) % 360f
        }
    }

    val accent = Accent
    val accentDark = lerp(accent, Color.Black, 0.28f)
    val onBand = if (accent.luminance() > 0.6f) Color(0xFF1A1612) else Color.White
    val ink = Color(0xFF2A251E)

    BoxWithConstraints(modifier) {
        val mm = maxWidth / CW
        val density = LocalDensity.current

        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width.roundToInt().coerceAtLeast(2)
                    val h = size.height.roundToInt().coerceAtLeast(2)
                    val u = w / CW
                    val pad = (w * 0.18f).roundToInt()
                    val shadow = renderLayer(w + 2 * pad, h + 2 * pad) { paintShadow(u, pad.toFloat()) }
                    val body = renderLayer(w, h) { paintBody(u, accent, accentDark) }
                    val over = renderLayer(w, h) { paintOver(u) }
                    val windowPath = roundRectPath(WIN_X, WIN_Y, WIN_W, WIN_H, WIN_R)
                    val shellPath = roundRectPath(0f, 0f, CW, CH, 3f)

                    onDrawBehind {
                        val tx = tilt.x
                        val ty = tilt.y
                        drawImage(
                            shadow,
                            topLeft = Offset(-pad - tx * 1.4f * u, -pad - ty * 1.0f * u)
                        )
                        drawImage(body)
                        mm(u) {
                            paintReels(shown.value, angleLeft.floatValue, angleRight.floatValue, windowPath)
                        }
                        drawImage(over)
                        mm(u) { paintGlare(tx, ty, windowPath, shellPath) }
                    }
                }
        )

        // The printing on the label. Text, not drawing, so that any script in the title comes out right.
        val type = tapeType(durationMs, live)
        MmText("PLAYMATE", mm, density, 10.4f, 4.05f, 40f, 2.5f, 2.9f, onBand, FontWeight.SemiBold, spacing = 0.14f)
        MmText(type, mm, density, 50f, 4.05f, 40f, 2.5f, 2.9f, onBand, FontWeight.SemiBold, TextAlign.End, 0.14f)
        MmText(title, mm, density, 10.8f, 10.5f, 79.2f, 3.8f, 4.3f, ink.copy(alpha = 0.92f), FontWeight.Bold, maxLines = 2)
        Box(
            Modifier.offset(x = mm * 9.4f, y = mm * 25.8f).size(mm * 7f),
            contentAlignment = Alignment.Center
        ) {
            MmText("A", mm, density, 0f, 0f, 7f, 4.2f, 5f, Color(0xFFF2ECDD), FontWeight.Bold, TextAlign.Center, inset = false)
        }
        MmText("PLAYMATE  HI-FI", mm, density, 10.4f, 39.0f, 40f, 1.9f, 2.2f, Color(0xFF6B6354), FontWeight.SemiBold, spacing = 0.1f)
        MmText("STEREO", mm, density, 50f, 39.0f, 40f, 1.9f, 2.2f, Color(0xFF6B6354), FontWeight.SemiBold, TextAlign.End, 0.1f)
    }
}

/** One line of printing, placed in millimetres on the cassette. */
@Composable
private fun MmText(
    text: String,
    mm: Dp,
    density: androidx.compose.ui.unit.Density,
    x: Float,
    y: Float,
    width: Float,
    size: Float,
    lineHeight: Float,
    color: Color,
    weight: FontWeight,
    align: TextAlign = TextAlign.Start,
    spacing: Float = 0f,
    maxLines: Int = 1,
    inset: Boolean = true
) {
    // Sizes are drawn from the cassette, not from the user's font size: the label is part of the picture.
    val fontSize = with(density) { (mm.value * size).dp.toSp() }
    val lineSize = with(density) { (mm.value * lineHeight).dp.toSp() }
    val tracking = with(density) { (mm.value * size * spacing).dp.toSp() }
    Text(
        text,
        color = color,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = lineSize,
            fontWeight = weight,
            textAlign = align,
            letterSpacing = tracking
        ),
        modifier = (if (inset) Modifier.offset(x = mm * x, y = mm * y) else Modifier).width(mm * width)
    )
}

// ------------------------------------------------------------------ drawing helpers

private fun CacheDrawScope.renderLayer(width: Int, height: Int, block: DrawScope.() -> Unit): ImageBitmap {
    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(this, layoutDirection, Canvas(image), Size(width.toFloat(), height.toFloat()), block)
    return image
}

/** Runs [block] with the drawing measured in millimetres instead of pixels. */
private fun DrawScope.mm(u: Float, block: DrawScope.() -> Unit) {
    withTransform({ scale(u, u, Offset.Zero) }) { block() }
}

private fun roundRectPath(x: Float, y: Float, w: Float, h: Float, r: Float): Path =
    Path().apply { addRoundRect(RoundRect(x, y, x + w, y + h, r, r)) }

private fun ink(alpha: Float) = Color.Black.copy(alpha = alpha)

private fun milk(alpha: Float) = Color.White.copy(alpha = alpha)

private fun vBrush(y0: Float, y1: Float, vararg stops: Pair<Float, Color>): Brush =
    Brush.verticalGradient(*stops, startY = y0, endY = y1)

private fun hBrush(x0: Float, x1: Float, vararg stops: Pair<Float, Color>): Brush =
    Brush.horizontalGradient(*stops, startX = x0, endX = x1)

private fun lBrush(x0: Float, y0: Float, x1: Float, y1: Float, vararg stops: Pair<Float, Color>): Brush =
    Brush.linearGradient(*stops, start = Offset(x0, y0), end = Offset(x1, y1))

private fun rBrush(cx: Float, cy: Float, radius: Float, vararg stops: Pair<Float, Color>): Brush =
    Brush.radialGradient(*stops, center = Offset(cx, cy), radius = radius)

private fun DrawScope.fillRR(x: Float, y: Float, w: Float, h: Float, r: Float, brush: Brush) =
    drawRoundRect(brush = brush, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(r, r))

private fun DrawScope.fillRR(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color) =
    fillRR(x, y, w, h, r, SolidColor(color))

private fun DrawScope.strokeRR(x: Float, y: Float, w: Float, h: Float, r: Float, line: Float, brush: Brush) =
    drawRoundRect(
        brush = brush,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = line)
    )

private fun DrawScope.strokeRR(x: Float, y: Float, w: Float, h: Float, r: Float, line: Float, color: Color) =
    strokeRR(x, y, w, h, r, line, SolidColor(color))

private fun DrawScope.ring(cx: Float, cy: Float, r: Float, line: Float, color: Color) =
    drawCircle(color = color, radius = r, center = Offset(cx, cy), style = Stroke(width = line))

private fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, brush: Brush) =
    drawRect(brush = brush, topLeft = Offset(x, y), size = Size(w, h))

private fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, color: Color) =
    drawRect(color = color, topLeft = Offset(x, y), size = Size(w, h))

/** A soft shadow in pixels: [blur] is the spread of the blur. */
private fun DrawScope.softShadow(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
    blur: Float,
    color: Color
) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint)
    }
}

// ------------------------------------------------------------------ layer 0: the shadow

/** Three shadows on top of each other (tight, middle, wide), the way a real object sits on a table. */
private fun DrawScope.paintShadow(u: Float, pad: Float) {
    val blurs = floatArrayOf(1.1f, 4.0f, 11f)
    val drops = floatArrayOf(0.9f, 3.2f, 8f)
    val alphas = floatArrayOf(0.70f, 0.48f, 0.46f)
    for (i in blurs.indices) {
        softShadow(
            left = pad + 1f * u,
            top = pad + (1f + drops[i]) * u,
            width = (CW - 2f) * u,
            height = (CH - 2f) * u,
            radius = 3f * u,
            blur = blurs[i] * u,
            color = ink(alphas[i])
        )
    }
}

// ------------------------------------------------------------------ layer 1: the body

private fun DrawScope.paintBody(u: Float, accent: Color, accentDark: Color) {
    val shellPx = roundRectPath(0f, 0f, CW * u, CH * u, 3f * u)
    val labelPx = roundRectPath(LABEL_X * u, LABEL_Y * u, LABEL_W * u, LABEL_H * u, LABEL_R * u)
    val labelPath = roundRectPath(LABEL_X, LABEL_Y, LABEL_W, LABEL_H, LABEL_R)
    val shellPath = roundRectPath(0f, 0f, CW, CH, 3f)

    // The shell: graphite plastic, lit from the top left, with grain and a moulded seam.
    mm(u) {
        fillRR(
            0f, 0f, CW, CH, 3f,
            vBrush(0f, CH, 0f to Color(0xFF303036), 0.5f to Color(0xFF222227), 1f to Color(0xFF151519))
        )
        fillRR(0f, 0f, CW, CH, 3f, lBrush(0f, 0f, CW * 0.75f, CH, 0f to milk(0.13f), 0.55f to milk(0f)))
    }
    clipPath(shellPx) { drawRect(brush = GrainBrush) }
    mm(u) {
        strokeRR(1.15f, 1.15f, CW - 2.3f, CH - 2.3f, 2.0f, 0.2f, ink(0.5f))
        strokeRR(1.35f, 1.35f, CW - 2.7f, CH - 2.7f, 1.9f, 0.14f, milk(0.08f))
    }

    // The label: paper stuck on the shell, so it throws a hair of shadow.
    softShadow(LABEL_X * u, (LABEL_Y + 0.3f) * u, LABEL_W * u, LABEL_H * u, LABEL_R * u, 0.7f * u, ink(0.6f))
    mm(u) {
        fillRR(
            LABEL_X, LABEL_Y, LABEL_W, LABEL_H, LABEL_R,
            vBrush(LABEL_Y, LABEL_Y + LABEL_H, 0f to Color(0xFFF4EFE2), 1f to Color(0xFFE2DAC5))
        )
        clipPath(labelPath) {
            val edge = Color(0xFF6E5532)
            rect(
                LABEL_X, LABEL_Y, LABEL_W, LABEL_H,
                hBrush(
                    LABEL_X, LABEL_X + LABEL_W,
                    0f to edge.copy(alpha = 0.18f),
                    0.05f to edge.copy(alpha = 0f),
                    0.95f to edge.copy(alpha = 0f),
                    1f to edge.copy(alpha = 0.20f)
                )
            )
            // The accent band and its fine line.
            rect(LABEL_X, LABEL_Y, LABEL_W, 4.6f, vBrush(LABEL_Y, LABEL_Y + 4.6f, 0f to accent, 1f to accentDark))
            rect(LABEL_X, LABEL_Y + 5.3f, LABEL_W, 0.35f, accent)
            // The two ruled lines the title sits on.
            val rule = Color(0xFF786C56).copy(alpha = 0.55f)
            rect(LABEL_X + 4f, 14.3f, LABEL_W - 8f, 0.16f, rule)
            rect(LABEL_X + 4f, 18.6f, LABEL_W - 8f, 0.16f, rule)
        }
    }
    clipPath(labelPx) { drawRect(brush = GrainBrush, alpha = 0.7f) }

    mm(u) {
        // Side A, left of the window; the two linked reels, the mark of the app, on the right.
        val a = Offset(12.9f, 29.3f)
        drawCircle(
            brush = vBrush(25.8f, 32.8f, 0f to Color(0xFF26221D), 1f to Color(0xFF14110E)),
            radius = 3.5f,
            center = a
        )
        ring(a.x, a.y, 3.5f, 0.15f, milk(0.15f))
        ring(88.0f - 0.95f, 29.3f, 1.7f, 0.5f, Color(0xFF26221D))
        ring(88.0f + 0.95f, 29.3f, 1.7f, 0.5f, Color(0xFF26221D))

        // The window: a dark well in the shell, and the pale liner sheet behind the tape.
        fillRR(
            WIN_X - 0.4f, WIN_Y - 0.4f, WIN_W + 0.8f, WIN_H + 0.8f, WIN_R + 0.4f,
            vBrush(WIN_Y, WIN_Y + WIN_H, 0f to Color(0xFF050506), 1f to Color(0xFF3C3C42))
        )
        fillRR(
            WIN_X, WIN_Y, WIN_W, WIN_H, WIN_R,
            vBrush(WIN_Y, WIN_Y + WIN_H, 0f to Color(0xFFB5B0A3), 1f to Color(0xFFD6D1C4))
        )

        // The foot: the darker, glossier front of the shell, with its openings for the heads.
        clipPath(shellPath) {
            val foot = Path().apply {
                moveTo(27f, 46.2f)
                lineTo(73.4f, 46.2f)
                lineTo(88.4f, CH + 0.2f)
                lineTo(12f, CH + 0.2f)
                close()
            }
            drawPath(foot, brush = vBrush(46f, CH, 0f to Color(0xFF111115), 1f to Color(0xFF08080A)))
            drawPath(
                foot,
                brush = vBrush(46f, CH, 0f to milk(0.22f), 1f to milk(0.04f)),
                style = Stroke(width = 0.35f)
            )
            val tape = Color(0xFF3A2D25)
            fun notch(x: Float, y: Float, w: Float, h: Float, r: Float) {
                fillRR(x, y, w, h, r, Color(0xFF020203))
                strokeRR(x, y, w, h, r, 0.2f, milk(0.12f))
                // The tape runs across every opening.
                clipPath(roundRectPath(x, y, w, h, r)) { rect(x - 1f, 61.4f, w + 2f, 1.0f, tape) }
            }
            notch(13.8f, 57.6f, 13.6f, 7f, 1.0f)
            notch(41.6f, 55.4f, 17.2f, 9f, 1.2f)
            notch(73.0f, 57.6f, 13.6f, 7f, 1.0f)
            // The pressure pad: a little brass.
            fillRR(47.2f, 57.0f, 6.0f, 3.2f, 0.5f, vBrush(57f, 60.2f, 0f to Color(0xFFD9C58A), 1f to Color(0xFF8D7A45)))
            // The white tape guides.
            for (cx in floatArrayOf(17.4f, 82.9f)) {
                drawCircle(
                    brush = rBrush(cx - 0.4f, 60.5f, 1.9f, 0f to Color(0xFFF1EDE4), 1f to Color(0xFF9C978A)),
                    radius = 1.7f,
                    center = Offset(cx, 61.0f)
                )
            }
            // The two round holes.
            for (cx in floatArrayOf(34.0f, 66.4f)) {
                val c = Offset(cx, 52.0f)
                drawCircle(color = Color(0xFF010102), radius = 2.9f, center = c)
                ring(c.x, c.y, 2.9f, 0.2f, milk(0.16f))
                drawCircle(
                    brush = rBrush(c.x, c.y, 2.0f, 0.25f to Color(0xFF050507), 1f to Color(0xFF14141A)),
                    radius = 2.0f,
                    center = c
                )
            }
        }

        // The edge of the shell catches the light on top and left, and is dark at the bottom right.
        strokeRR(
            0.25f, 0.25f, CW - 0.5f, CH - 0.5f, 2.75f, 0.5f,
            lBrush(
                0f, 0f, CW, CH,
                0f to milk(0.50f), 0.35f to milk(0.10f), 0.7f to milk(0f), 1f to ink(0.6f)
            )
        )

        // Five Phillips screws, sunk in their wells.
        fun screw(x: Float, y: Float) {
            drawCircle(
                brush = rBrush(x, y, 2.4f, 0f to Color(0xFF050506), 0.66f to Color(0xFF050506), 1f to Color(0xFF2A2A30)),
                radius = 2.3f,
                center = Offset(x, y)
            )
            drawCircle(
                brush = lBrush(
                    x - 1.2f, y - 1.2f, x + 1.2f, y + 1.2f,
                    0f to Color(0xFF7C7C86), 0.5f to Color(0xFF4A4A52), 1f to Color(0xFF2A2A30)
                ),
                radius = 1.75f,
                center = Offset(x, y)
            )
            rotate(degrees = 34f, pivot = Offset(x, y)) {
                val slot = Color(0xFF14141A)
                rect(x - 1.2f, y - 0.17f, 2.4f, 0.34f, slot)
                rect(x - 0.17f, y - 1.2f, 0.34f, 2.4f, slot)
                rect(x - 1.2f, y + 0.17f, 2.4f, 0.1f, milk(0.18f))
                rect(x + 0.17f, y - 1.2f, 0.1f, 2.4f, milk(0.18f))
            }
        }
        screw(4.0f, 4.0f)
        screw(CW - 4.0f, 4.0f)
        screw(4.0f, CH - 4.6f)
        screw(CW - 4.0f, CH - 4.6f)
        screw(CW / 2f, 50.8f)
    }
}

// ------------------------------------------------------------------ layer 2: the tape and the hubs

private fun DrawScope.paintReels(progress: Float, angleLeft: Float, angleRight: Float, windowPath: Path) {
    clipPath(windowPath) {
        paintReel(HUB_L, HUB_Y, packLeft(progress), angleLeft)
        paintReel(HUB_R, HUB_Y, packRight(progress), angleRight)
    }
}

private fun DrawScope.paintReel(cx: Float, cy: Float, r: Float, angle: Float) {
    val c = Offset(cx, cy)

    // The pack throws a soft shadow on the liner around it.
    val halo = r + 2.2f
    drawCircle(
        brush = rBrush(cx, cy, halo, 0f to ink(0f), (r / halo) * 0.98f to ink(0.42f), 1f to ink(0f)),
        radius = halo,
        center = c
    )

    // The tape: brown-black, slightly paler at the rim, with the fine rings of its windings.
    val inner = 5f / r
    drawCircle(
        brush = rBrush(
            cx, cy, r,
            inner to Color(0xFF1B1613),
            inner + (1f - inner) * 0.7f to Color(0xFF2C231E),
            1f to Color(0xFF43372F)
        ),
        radius = r,
        center = c
    )
    var q = 6.8f
    var odd = false
    while (q < r - 0.2f) {
        ring(cx, cy, q, 0.13f, if (odd) milk(0.045f) else ink(0.12f))
        odd = !odd
        q += 0.62f
    }
    drawCircle(
        brush = lBrush(
            cx - r, cy - r, cx + r, cy + r,
            0f to milk(0.28f), 0.5f to milk(0.05f), 1f to milk(0f)
        ),
        radius = r - 0.15f,
        center = c,
        style = Stroke(width = 0.3f)
    )

    // The hub: a pale ring, a dark hole, six teeth, and one dot so the turning can be seen.
    drawCircle(color = Color.Black, radius = 6.3f, center = c)
    drawCircle(
        brush = lBrush(cx - 5f, cy - 5f, cx + 5f, cy + 5f, 0f to Color(0xFFF4F0E6), 1f to Color(0xFFC9C4B7)),
        radius = 6.0f,
        center = c
    )
    drawCircle(
        brush = lBrush(cx - 4f, cy - 4f, cx + 4f, cy + 4f, 0f to Color(0xFFD3CEC1), 1f to Color(0xFFEFEBE0)),
        radius = 4.9f,
        center = c
    )
    ring(cx, cy, 4.9f, 0.12f, ink(0.25f))
    drawCircle(
        brush = rBrush(cx, cy, 3.55f, 0.55f to Color(0xFF0B0B0E), 1f to Color(0xFF17171C)),
        radius = 3.55f,
        center = c
    )
    rotate(degrees = angle, pivot = c) {
        val tooth = hBrush(cx - 0.65f, cx + 0.65f, 0f to Color(0xFFEDE9DE), 1f to Color(0xFFBDB8AB))
        for (i in 0 until 6) {
            rotate(degrees = i * 60f, pivot = c) {
                drawRoundRect(
                    brush = tooth,
                    topLeft = Offset(cx - 0.65f, cy - 3.6f),
                    size = Size(1.3f, 1.35f),
                    cornerRadius = CornerRadius(0.3f, 0.3f)
                )
            }
        }
        drawCircle(color = Color(0xFF464034).copy(alpha = 0.75f), radius = 0.42f, center = Offset(cx, cy - 5.45f))
    }
}

// ------------------------------------------------------------------ layer 3: the glass frame

/** What sits over the tape and does not move: the shade under the rim, the smoked glass, the lip. */
private fun DrawScope.paintOver(u: Float) {
    val window = roundRectPath(WIN_X, WIN_Y, WIN_W, WIN_H, WIN_R)
    mm(u) {
        clipPath(window) {
            rect(WIN_X, WIN_Y, WIN_W, 4.2f, vBrush(WIN_Y, WIN_Y + 4.2f, 0f to ink(0.55f), 1f to ink(0f)))
            rect(WIN_X, WIN_Y, 3.5f, WIN_H, hBrush(WIN_X, WIN_X + 3.5f, 0f to ink(0.30f), 1f to ink(0f)))
            rect(
                WIN_X + WIN_W - 3.5f, WIN_Y, 3.5f, WIN_H,
                hBrush(WIN_X + WIN_W, WIN_X + WIN_W - 3.5f, 0f to ink(0.22f), 1f to ink(0f))
            )
            rect(WIN_X, WIN_Y, WIN_W, WIN_H, Color(0xFF18130F).copy(alpha = 0.20f))
        }
        strokeRR(
            WIN_X - 0.3f, WIN_Y - 0.3f, WIN_W + 0.6f, WIN_H + 0.6f, WIN_R + 0.3f, 0.5f,
            vBrush(WIN_Y, WIN_Y + WIN_H, 0f to ink(0.65f), 0.5f to ink(0f), 1f to milk(0.30f))
        )
    }
}

// ------------------------------------------------------------------ layer 4: glare

/** Reflections. They slide as the phone is tipped, and the glass slides a little more than the shell. */
private fun DrawScope.paintGlare(tx: Float, ty: Float, windowPath: Path, shellPath: Path) {
    val o = tx * 14f + ty * 3f
    clipPath(windowPath) {
        rect(
            WIN_X, WIN_Y, WIN_W, WIN_H,
            lBrush(
                WIN_X + o, WIN_Y, WIN_X + 28f + o, WIN_Y + 17f,
                0f to milk(0f), 0.28f to milk(0f), 0.5f to milk(0.24f), 0.62f to milk(0.04f), 1f to milk(0f)
            )
        )
        rect(
            WIN_X, WIN_Y, WIN_W, WIN_H,
            lBrush(
                WIN_X + 30f + o * 1.4f, WIN_Y, WIN_X + 44f + o * 1.4f, WIN_Y + 17f,
                0f to milk(0f), 0.45f to milk(0.15f), 0.55f to milk(0f), 1f to milk(0f)
            )
        )
        // A thin bright line along the top edge of the glass.
        rect(
            WIN_X + 3f, WIN_Y + 0.25f, WIN_W - 6f, 0.22f,
            hBrush(
                WIN_X, WIN_X + WIN_W,
                0f to milk(0f), 0.3f to milk(0.35f), 0.7f to milk(0.12f), 1f to milk(0f)
            )
        )
    }
    // A broad, faint sheen across the gloss of the shell.
    val s = tx * 30f
    clipPath(shellPath) {
        rect(
            0f, 0f, CW, CH,
            lBrush(
                10f + s, 0f, 60f + s, CH,
                0f to milk(0f), 0.4f to milk(0f), 0.5f to milk(0.05f), 0.6f to milk(0f), 1f to milk(0f)
            )
        )
    }
}
