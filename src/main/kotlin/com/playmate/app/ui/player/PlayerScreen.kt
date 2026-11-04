package com.playmate.app.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.playmate.app.data.Load
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.Playing
import com.playmate.app.data.VideoItem
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.SkeletonLines
import com.playmate.app.ui.components.VideoRow
import com.playmate.app.ui.components.findActivity
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.components.metaLine
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playing: Playing,
    onClose: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = remember { createPlayer(context) }
    val fullscreen = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val info: PlayableInfo? = (playing.info as? Load.Ready)?.data

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var firstFrame by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var qualityIndex by remember { mutableIntStateOf(-1) }
    var speed by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var fallbacks by remember { mutableIntStateOf(0) }
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudSide by remember { mutableStateOf(Alignment.Center) }
    var hudTick by remember { mutableIntStateOf(0) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat())
    }
    var brightness by remember {
        val current = activity?.window?.attributes?.screenBrightness ?: -1f
        val system = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
        mutableFloatStateOf(if (current >= 0f) current else system)
    }

    fun showHud(text: String, side: Alignment) {
        hudText = text
        hudSide = side
        hudTick++
    }

    fun seekBy(deltaMs: Long) {
        if (info?.live == true) return
        val limit = if (player.duration > 0) player.duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, limit))
    }

    val switchQuality: (Int) -> Unit = { index ->
        val current = info
        if (current != null) {
            val source = buildSource(current, index)
            if (source != null) {
                val resumeAt = player.currentPosition
                val resume = player.playWhenReady
                qualityIndex = index
                playbackError = null
                player.setMediaSource(source, resumeAt)
                player.prepare()
                player.playWhenReady = resume
            }
        }
    }
    val latestSwitch by rememberUpdatedState(switchQuality)
    val latestInfo by rememberUpdatedState(info)
    val latestQuality by rememberUpdatedState(qualityIndex)

    LaunchedEffect(info) {
        if (info == null) {
            player.stop()
            player.clearMediaItems()
            firstFrame = false
            return@LaunchedEffect
        }
        firstFrame = false
        playbackError = null
        fallbacks = 0
        val index = defaultQualityIndex(info)
        qualityIndex = index
        val source = buildSource(info, index)
        if (source == null) {
            playbackError = "No playable stream was found for this video."
        } else {
            player.setMediaSource(source)
            player.prepare()
            player.setPlaybackSpeed(speed)
            player.playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (player.duration > 0) duration = player.duration
                if (state == Player.STATE_ENDED) controls = true
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                val current = latestInfo
                val index = latestQuality
                if (current != null && !current.live && fallbacks < 2 && index < current.qualities.lastIndex) {
                    fallbacks++
                    latestSwitch(index + 1)
                } else {
                    playbackError = "Playback failed (${error.errorCodeName})."
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val attrs = it.window.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.window.attributes = attrs
                WindowCompat.getInsetsController(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(fullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) position = player.currentPosition
            if (player.duration > 0) duration = player.duration
            delay(400)
        }
    }

    LaunchedEffect(controls, isPlaying, settingsOpen, dragging, position / 4000) {
        if (controls && isPlaying && !settingsOpen && !dragging) {
            delay(3200)
            controls = false
        }
    }

    LaunchedEffect(hudTick) {
        if (hudText != null) {
            delay(750)
            hudText = null
        }
    }

    fun toggleFullscreen() {
        activity?.requestedOrientation =
            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }

    BackHandler {
        if (fullscreen) toggleFullscreen() else onClose()
    }

    val live = info?.live == true
    val title = info?.title ?: playing.seed.title

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .then(if (fullscreen) Modifier.weight(1f) else Modifier)
                .fillMaxWidth()
                .background(Color.Black)
                .then(if (fullscreen) Modifier else Modifier.statusBarsPadding())
        ) {
            Box(
                Modifier
                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                    .fillMaxWidth()
                    .onSizeChanged { boxSize = it }
            ) {
                val engine = player
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            this.player = engine
                        }
                    },
                    update = { it.keepScreenOn = isPlaying },
                    modifier = Modifier.fillMaxSize()
                )

                val poster = info?.thumb ?: playing.seed.thumb
                if (!firstFrame && poster != null) {
                    AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(live) {
                            detectTapGestures(
                                onTap = { controls = !controls },
                                onDoubleTap = { offset ->
                                    if (!live) {
                                        val forward = offset.x > size.width / 2f
                                        seekBy(if (forward) 10_000 else -10_000)
                                        showHud(
                                            if (forward) "+10 s" else "-10 s",
                                            if (forward) Alignment.CenterEnd else Alignment.CenterStart
                                        )
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            var leftSide = false
                            detectVerticalDragGestures(
                                onDragStart = { leftSide = it.x < size.width / 2f },
                                onVerticalDrag = { _, dy ->
                                    val step = -dy / size.height * 1.4f
                                    if (leftSide) {
                                        brightness = (brightness + step).coerceIn(0.02f, 1f)
                                        activity?.let {
                                            val attrs = it.window.attributes
                                            attrs.screenBrightness = brightness
                                            it.window.attributes = attrs
                                        }
                                        showHud("Brightness ${(brightness * 100).roundToInt()}%", Alignment.CenterStart)
                                    } else {
                                        volume = (volume + step).coerceIn(0f, 1f)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            (volume * maxVolume).roundToInt(),
                                            0
                                        )
                                        showHud("Volume ${(volume * 100).roundToInt()}%", Alignment.CenterEnd)
                                    }
                                }
                            )
                        }
                )

                hudText?.let {
                    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = hudSide) {
                        Text(
                            it,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                this@Column.AnimatedVisibility(
                    visible = controls || playbackError != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f))) {
                        Row(
                            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (fullscreen) toggleFullscreen() else onClose() }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
                            }
                            Text(
                                if (fullscreen) title else "",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (info != null) {
                                IconButton(onClick = { settingsOpen = true }) {
                                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                                }
                            }
                        }

                        Box(Modifier.align(Alignment.Center)) {
                            when {
                                playbackError != null -> Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        playbackError ?: "",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(onClick = {
                                        playbackError = null
                                        fallbacks = 0
                                        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                                            player.prepare()
                                        } else {
                                            onRetry()
                                        }
                                    }) { Text("Retry", color = Accent) }
                                }

                                buffering || playing.info is Load.Loading -> CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp)
                                )

                                info != null -> Box(
                                    Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .clickable {
                                            if (isPlaying) player.pause() else {
                                                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                                                player.play()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (live) {
                                Text(
                                    "LIVE",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Accent)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Spacer(Modifier.weight(1f))
                            } else {
                                val shown = if (dragging) (dragValue * duration).toLong() else position
                                Text(
                                    "${formatClock(shown)} / ${formatClock(duration)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = if (duration > 0) (if (dragging) dragValue else position / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                                    onValueChange = {
                                        dragging = true
                                        dragValue = it
                                    },
                                    onValueChangeFinished = {
                                        player.seekTo((dragValue * duration).toLong())
                                        position = (dragValue * duration).toLong()
                                        dragging = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Accent,
                                        activeTrackColor = Accent,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                )
                            }
                            IconButton(onClick = ::toggleFullscreen) {
                                Icon(
                                    if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!fullscreen) {
            Details(playing = playing, info = info, onOpen = onOpen, onRetry = onRetry)
        }
    }

    if (settingsOpen && info != null) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                if (!info.live && info.qualities.isNotEmpty()) {
                    Text(
                        "Quality",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    info.qualities.forEachIndexed { index, quality ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    switchQuality(index)
                                    settingsOpen = false
                                }
                                .padding(horizontal = 20.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(quality.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (index == qualityIndex) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Text(
                    "Speed",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Speeds.forEach { value ->
                        val selected = value == speed
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Accent else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    speed = value
                                    player.setPlaybackSpeed(value)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                if (value == 1f) "1x" else "${value}x",
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Details(
    playing: Playing,
    info: PlayableInfo?,
    onOpen: (VideoItem) -> Unit,
    onRetry: () -> Unit
) {
    var expanded by remember(playing.seed.url) { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        when (val state = playing.info) {
            is Load.Loading -> {
                if (playing.seed.title.isNotBlank()) {
                    item { Header(playing.seed.title, playing.seed.channel, playing.seed.views, playing.seed.uploaded) }
                } else {
                    item { SkeletonLines() }
                }
            }

            is Load.Failed -> item { ErrorState(state.message, onRetry = onRetry) }

            is Load.Ready -> {
                val data = state.data
                item { Header(data.title, data.channel, data.views, data.uploaded) }

                if (data.description.isNotBlank()) {
                    item {
                        Column(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { expanded = !expanded }
                                .padding(14.dp)
                        ) {
                            Text(
                                data.description,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (expanded) "Show less" else "Show more",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                if (data.related.isNotEmpty()) {
                    item {
                        Text(
                            "Up next",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
                        )
                    }
                    items(data.related, key = { it.url }) { video ->
                        VideoRow(video, onClick = { onOpen(video) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, channel: String, views: Long, uploaded: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            metaLine(channel, views, uploaded),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
    }
}
