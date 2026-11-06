package com.playmate.app.ui.player

import android.content.Context
import android.content.Intent
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
import com.playmate.app.AppViewModel
import com.playmate.app.PlaybackService
import com.playmate.app.data.Load
import com.playmate.app.data.NowPlayingStore
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.Playing
import com.playmate.app.data.VideoItem
import com.playmate.app.data.videoIdOf
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.SkeletonLines
import com.playmate.app.ui.components.VideoRow
import com.playmate.app.ui.components.findActivity
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.media3.common.C
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.playmate.app.ui.theme.Tonal

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** "1x", "0.75x", "2x": no stray ".0". */
private fun speedLabel(value: Float): String = value.toString().removeSuffix(".0") + "x"

/** What the little feedback on the picture shows right now. */
private sealed interface Hud {
    /** A jump after a double tap; [seconds] adds up while the taps keep coming. */
    data class Seek(val forward: Boolean, val seconds: Int) : Hud

    /** Brightness (left half) or volume (right half) while a finger slides. */
    data class Level(val brightness: Boolean, val percent: Int) : Hud
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playing: Playing,
    expanded: Boolean = true,
    onClose: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onRetry: () -> Unit,
    vm: AppViewModel
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = remember { PlayerHolder.get(context) }
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
    var hud by remember { mutableStateOf<Hud?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var buffered by remember { mutableLongStateOf(0L) }
    var ended by remember { mutableStateOf(false) }
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

    fun showHud(next: Hud) {
        hud = next
        hudTick++
    }

    fun seekBy(deltaMs: Long) {
        if (info?.live == true) return
        val limit = if (player.duration > 0) player.duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, limit))
    }

    // Double tap jumps 10 s; while the feedback is still showing, single taps keep jumping (as on YouTube).
    val skip: (Boolean) -> Unit = { forward ->
        if (info?.live != true) {
            seekBy(if (forward) 10_000L else -10_000L)
            val before = hud as? Hud.Seek
            val total = if (before != null && before.forward == forward) before.seconds + 10 else 10
            showHud(Hud.Seek(forward, total))
        }
    }
    val latestSkip by rememberUpdatedState(skip)

    val switchQuality: (Int) -> Unit = { index ->
        val current = info
        if (current != null) {
            val source = buildSource(current, index)
            if (source != null) {
                val resumeAt = player.currentPosition
                val resume = player.playWhenReady
                qualityIndex = index
                ActivePlayback.set(current, index)
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
    val handleFatal: (PlaybackException) -> Unit = { error ->
        val current = latestInfo
        val index = latestQuality
        if (current != null && !current.live && fallbacks < 2 && index < current.qualities.lastIndex) {
            fallbacks++
            latestSwitch(index + 1)
        } else {
            playbackError = "Playback failed (${error.errorCodeName})."
        }
    }
    val latestFatal by rememberUpdatedState(handleFatal)

    LaunchedEffect(info) {
        if (info == null) {
            // Just reopened while the sound was still playing from the background service: leave it alone.
            if (!vm.adoptPlayback) {
                player.stop()
                player.clearMediaItems()
            }
            firstFrame = false
            return@LaunchedEffect
        }
        firstFrame = false
        playbackError = null
        fallbacks = 0
        PlaybackRecovery.reset()

        // The app was reopened and this very video is still playing: pick it up without a restart.
        val adopting = vm.adoptPlayback &&
            player.mediaItemCount > 0 &&
            player.currentMediaItem?.mediaId == info.url
        vm.adoptPlayback = false
        if (adopting) {
            val known = ActivePlayback.quality
            if (ActivePlayback.info?.url == info.url && known in info.qualities.indices) {
                // Same process, same streams: keep playing as is, only refresh the links for later switches.
                qualityIndex = known
                ActivePlayback.set(info, known)
            } else {
                // Sound-only playback (resumed after the app was killed): bring the picture back at the same second.
                val index = defaultQualityIndex(info)
                qualityIndex = index
                val resumeSource = buildSource(info, index)
                if (resumeSource != null) {
                    val at = player.currentPosition
                    val go = player.playWhenReady
                    player.setMediaSource(resumeSource, if (info.live) C.TIME_UNSET else at)
                    player.prepare()
                    player.playWhenReady = go
                    ActivePlayback.set(info, index)
                }
            }
            return@LaunchedEffect
        }

        val index = defaultQualityIndex(info)
        qualityIndex = index
        val source = buildSource(info, index)
        if (source == null) {
            playbackError = "No playable stream was found for this video."
        } else {
            ActivePlayback.set(info, index)
            NowPlayingStore.save(context, info, playing.seed.durationSec)
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
                ended = state == Player.STATE_ENDED
                if (player.duration > 0) duration = player.duration
                if (state == Player.STATE_ENDED) controls = true
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                // Network trouble and expired links are repaired by the background service
                // (retries, waits for the network, fresh links). Only the rest is handled here.
                if (PlaybackRecovery.isRecoverable(error)) return
                latestFatal(error)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // The service tried everything and gave up: lower the quality, or show the error.
    val gaveUp = PlaybackRecovery.gaveUp
    LaunchedEffect(gaveUp) {
        if (gaveUp != null) {
            PlaybackRecovery.reset()
            latestFatal(gaveUp)
        }
    }

    DisposableEffect(Unit) {
        val serviceIntent = Intent(context, PlaybackService::class.java)
        runCatching { context.startService(serviceIntent) }
        // No stop here: this runs when the app is swiped away too, and the sound must go on then.
        // Closing the player (AppViewModel.closePlayer) is what stops playback and the service.
        onDispose {
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

    // Background and mini-player: audio keeps playing, only the video track is switched off.
    var started by remember { mutableStateOf(true) }
    LaunchedEffect(expanded, started) { player.setVideoEnabled(expanded && started) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                started = false
                // Background play switched off: leaving the app pauses the video.
                if (!com.playmate.app.data.PlaybackPrefs.background(context)) player.pause()
            }
            if (event == Lifecycle.Event.ON_START) started = true
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
            buffered = player.bufferedPosition
            if (player.duration > 0) duration = player.duration
            delay(250)
        }
    }

    LaunchedEffect(controls, isPlaying, settingsOpen, dragging, position / 4000) {
        if (controls && isPlaying && !settingsOpen && !dragging) {
            delay(3200)
            controls = false
        }
    }

    LaunchedEffect(hudTick) {
        if (hud != null) {
            delay(if (hud is Hud.Seek) 900L else 750L)
            hud = null
        }
    }

    fun toggleFullscreen() {
        activity?.requestedOrientation =
            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }

    BackHandler(enabled = expanded) {
        if (fullscreen) toggleFullscreen() else onClose()
    }

    val live = info?.live == true
    val title = info?.title ?: playing.seed.title

    var commentsOpen by remember(playing.seed.url) { mutableStateOf(false) }
    var saveOpen by remember(playing.seed.url) { mutableStateOf(false) }

    val shareVideo: () -> Unit = {
        val id = videoIdOf(playing.seed.url)
        val link = if (id != null) "https://youtu.be/$id" else playing.seed.url
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, if (title.isNotBlank()) "$title\n$link" else link)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Share")) }
    }
    val saveVideo: () -> Unit = {
        if (vm.ensureSignedIn("save videos")) {
            vm.loadPlaylists()
            saveOpen = true
        }
    }

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
                    update = { it.keepScreenOn = isPlaying && expanded },
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

                // Touch layer: tap shows or hides the controls, double tap jumps, sliding changes brightness or volume.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(live) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (!live && hud is Hud.Seek) latestSkip(offset.x > size.width / 2f)
                                    else controls = !controls
                                },
                                onDoubleTap = { offset ->
                                    if (!live) latestSkip(offset.x > size.width / 2f)
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
                                        showHud(Hud.Level(brightness = true, percent = (brightness * 100).roundToInt()))
                                    } else {
                                        volume = (volume + step).coerceIn(0f, 1f)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            (volume * maxVolume).roundToInt(),
                                            0
                                        )
                                        showHud(Hud.Level(brightness = false, percent = (volume * 100).roundToInt()))
                                    }
                                }
                            )
                        }
                )

                when (val feedback = hud) {
                    is Hud.Seek -> Box(
                        Modifier.fillMaxSize().padding(horizontal = 40.dp),
                        contentAlignment = if (feedback.forward) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        SeekFeedback(forward = feedback.forward, seconds = feedback.seconds)
                    }

                    is Hud.Level -> Box(
                        Modifier.fillMaxSize().padding(horizontal = 28.dp),
                        contentAlignment = if (feedback.brightness) Alignment.CenterStart else Alignment.CenterEnd
                    ) {
                        LevelFeedback(
                            icon = when {
                                feedback.brightness -> Icons.Rounded.BrightnessMedium
                                feedback.percent == 0 -> Icons.Rounded.VolumeOff
                                else -> Icons.Rounded.VolumeUp
                            },
                            percent = feedback.percent
                        )
                    }

                    null -> Unit
                }

                // The fractions the seek bar and the thin bottom line are drawn from.
                val playedFraction = when {
                    live -> 1f
                    duration > 0 -> (if (dragging) dragValue else position / duration.toFloat()).coerceIn(0f, 1f)
                    else -> 0f
                }
                val loadedFraction = when {
                    live -> 1f
                    duration > 0 -> (buffered / duration.toFloat()).coerceIn(0f, 1f)
                    else -> 0f
                }

                // Controls hidden: only a hairline of progress stays at the bottom edge.
                this@Column.AnimatedVisibility(
                    visible = !controls && playbackError == null && !live && duration > 0,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    MiniProgressLine(progress = playedFraction, buffered = loadedFraction)
                }

                this@Column.AnimatedVisibility(
                    visible = controls || playbackError != null || dragging,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(220)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // In landscape, keep the controls clear of a camera cut-out.
                    val safe = if (fullscreen) {
                        Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    } else {
                        Modifier
                    }
                    val sidePad = if (fullscreen) 16.dp else 4.dp

                    Box(Modifier.fillMaxSize()) {
                        // A light veil for the centre button, and a soft shade at top and bottom for the white controls.
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.26f)))
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .height(96.dp)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(132.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))))
                        )

                        // Top: minimise (or leave fullscreen), the title in fullscreen, settings.
                        Row(
                            Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .then(safe)
                                .padding(horizontal = sidePad, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OverlayIconButton(
                                icon = Icons.Rounded.KeyboardArrowDown,
                                description = if (fullscreen) "Exit fullscreen" else "Minimise",
                                onClick = { if (fullscreen) toggleFullscreen() else onClose() }
                            )
                            Text(
                                if (fullscreen) title else "",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )
                            if (info != null) {
                                OverlayIconButton(
                                    icon = Icons.Rounded.Settings,
                                    description = "Settings",
                                    onClick = { settingsOpen = true }
                                )
                            }
                        }

                        // Centre: the error, the scrub time, the spinner, or the one big button.
                        Box(Modifier.align(Alignment.Center)) {
                            when {
                                playbackError != null -> PlaybackErrorPanel(
                                    message = playbackError ?: "",
                                    onRetry = {
                                        playbackError = null
                                        fallbacks = 0
                                        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                                            player.prepare()
                                        } else {
                                            onRetry()
                                        }
                                    }
                                )

                                dragging -> ScrubTime((dragValue * duration).toLong())

                                buffering || playing.info is Load.Loading -> CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp)
                                )

                                info != null -> CenterPlayButton(
                                    action = when {
                                        ended -> CenterAction.Replay
                                        isPlaying -> CenterAction.Pause
                                        else -> CenterAction.Play
                                    },
                                    onClick = {
                                        if (isPlaying) player.pause() else {
                                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                                            player.play()
                                        }
                                    }
                                )
                            }
                        }

                        // Bottom: the time (or LIVE) and fullscreen, and under them the seek bar.
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .then(safe)
                                .padding(horizontal = sidePad)
                                .padding(bottom = if (fullscreen) 8.dp else 0.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (live) {
                                    LiveBadge()
                                } else {
                                    val shownMs = if (dragging) (dragValue * duration).toLong() else position
                                    TimeLabel(position = shownMs, duration = duration)
                                }
                                Spacer(Modifier.weight(1f))
                                OverlayIconButton(
                                    icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    description = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                                    onClick = { toggleFullscreen() }
                                )
                            }
                            PmSeekBar(
                                progress = playedFraction,
                                buffered = loadedFraction,
                                scrubbing = dragging,
                                enabled = !live && duration > 0,
                                onScrubStart = {
                                    dragging = true
                                    dragValue = it
                                },
                                onScrub = { dragValue = it },
                                onScrubEnd = { value ->
                                    val target = (value * duration).toLong()
                                    player.seekTo(target)
                                    position = target
                                    dragging = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!fullscreen) {
            Details(
                playing = playing,
                info = info,
                vm = vm,
                onOpen = onOpen,
                onRetry = onRetry,
                onComments = { commentsOpen = true },
                onShare = shareVideo,
                onSave = saveVideo
            )
        }
    }

    if (settingsOpen && info != null) {
        val scheme = MaterialTheme.colorScheme
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            containerColor = scheme.surfaceContainerHigh
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                if (!info.live && info.qualities.isNotEmpty()) {
                    SheetHeading("Quality")
                    info.qualities.forEachIndexed { index, quality ->
                        val selected = index == qualityIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    switchQuality(index)
                                    settingsOpen = false
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                quality.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = scheme.primary)
                            }
                        }
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Tonal.hairline())
                    )
                }

                SheetHeading("Playback speed")
                // One soft track with the chosen speed lifted out of it.
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Tonal.fill())
                        .padding(4.dp)
                ) {
                    Speeds.forEach { value ->
                        val selected = value == speed
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) scheme.onSurface else Color.Transparent)
                                .clickable {
                                    speed = value
                                    player.setPlaybackSpeed(value)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                speedLabel(value),
                                color = if (selected) scheme.surface else scheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    if (commentsOpen) CommentsSheet(vm = vm, onDismiss = { commentsOpen = false })
    if (saveOpen) SaveSheet(vm = vm, onDismiss = { saveOpen = false })
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun Details(
    playing: Playing,
    info: PlayableInfo?,
    vm: AppViewModel,
    onOpen: (VideoItem) -> Unit,
    onRetry: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    var expanded by remember(playing.seed.url) { mutableStateOf(false) }

    // A download kept inside the app: its own page, nothing here needs internet.
    val offline = info
    if (offline != null && offline.localUri != null) {
        OfflineDetails(info = offline, vm = vm)
        return
    }

    // The first comment is shown on the card under the video, so load it as soon as the video is known.
    val metaId = vm.meta?.videoId
    LaunchedEffect(metaId) { if (metaId != null) vm.loadComments() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        when (val state = playing.info) {
            is Load.Loading -> {
                if (playing.seed.title.isNotBlank()) {
                    item {
                        TitleBlock(
                            title = playing.seed.title,
                            channel = playing.seed.channel,
                            views = playing.seed.views,
                            uploaded = playing.seed.uploaded,
                            likes = null,
                            description = "",
                            expanded = false,
                            onToggle = {}
                        )
                    }
                } else {
                    item { SkeletonLines() }
                }
            }

            is Load.Failed -> item { ErrorState(state.message, onRetry = onRetry) }

            is Load.Ready -> {
                val data = state.data
                item {
                    TitleBlock(
                        title = data.title,
                        channel = "",
                        views = data.views,
                        uploaded = data.uploaded,
                        likes = vm.meta?.likeCount,
                        description = data.description,
                        expanded = expanded,
                        onToggle = { expanded = !expanded }
                    )
                }
                item { ChannelRow(vm, data, vm.meta, data.channel) }
                item { ActionRow(vm, vm.meta, onComments = onComments, onShare = onShare, onSave = onSave) }

                item {
                    CommentsCard(
                        count = vm.commentsTotal ?: vm.meta?.commentCount,
                        preview = (vm.comments as? Load.Ready)?.data?.firstOrNull { !it.isReply },
                        state = vm.comments,
                        onClick = onComments
                    )
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
