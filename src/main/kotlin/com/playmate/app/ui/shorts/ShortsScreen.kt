package com.playmate.app.ui.shorts

import android.content.Intent
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder as HeartOutlined
import androidx.compose.material.icons.outlined.ModeComment as ModeCommentOutlined
import androidx.compose.material.icons.outlined.Share as ShareOutlined
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.ShortsLaunch
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.Reaction
import com.playmate.app.data.videoIdOf
import com.playmate.app.ui.components.findActivity
import com.playmate.app.ui.player.CommentsSheet
import com.playmate.app.ui.player.DownloadSheet
import com.playmate.app.ui.player.PhoneSaveSheet
import com.playmate.app.ui.player.PlayerHolder
import com.playmate.app.ui.player.SaveSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Two taps this close together (in time) are a double tap. */
private const val DOUBLE_TAP_MS = 250L

/** A hold on the outer part of the screen (this share of the width, on each side) is the 2x hold. */
private const val EDGE_SHARE = 0.3f

/** Swiping down this far (in dp) while holding locks 2x on. */
private const val LOCK_SWIPE_DP = 48f

private val HeartRed = Color(0xFFFF2D55)

/**
 * The Shorts screen: one Short per page, a swipe moves exactly one page, never ends.
 *
 * Tap pauses at once, double tap likes; like / dislike / comments / share on the right, channel,
 * subscribe and title at the bottom, a thin seek line under it all that can be dragged.
 *
 * Everything a page shows (stream, like count, channel, subscription) is fetched before the page
 * arrives (see [ShortsController]), and the places where it will appear are always laid out, so
 * nothing jumps in when a swipe ends.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(launch: ShortsLaunch, vm: AppViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val controller = remember(launch) {
        ShortsController(
            context.applicationContext,
            scope,
            vm.viewModelScope,
            vm::preloadMeta,
            launch.items,
            launch.start
        )
    }
    var showComments by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var downloadFor by remember { mutableStateOf<PlayableInfo?>(null) }
    var phoneSaveFor by remember { mutableStateOf<PlayableInfo?>(null) }
    var showSave by remember { mutableStateOf(false) }

    BackHandler { vm.closeShorts() }

    // The normal player must not play underneath.
    LaunchedEffect(controller) { PlayerHolder.get(context).pause() }

    DisposableEffect(controller) { onDispose { controller.release() } }

    // Shorts are for watching in the foreground: pause when the app goes away.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.setForeground(false)
                Lifecycle.Event.ON_RESUME -> controller.setForeground(true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Light icons on the dark picture, screen kept awake; both undone on the way out.
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = bars?.isAppearanceLightStatusBars
        val lightNav = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            if (bars != null) {
                lightStatus?.let { bars.isAppearanceLightStatusBars = it }
                lightNav?.let { bars.isAppearanceLightNavigationBars = it }
            }
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller.tick()
            delay(33)
        }
    }

    val pager = rememberPagerState(initialPage = controller.current, pageCount = { controller.items.size })

    // One page per swipe, snapping early and quickly: the next Short is always right there.
    val fling = PagerDefaults.flingBehavior(
        state = pager,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = spring(stiffness = Spring.StiffnessMedium),
        snapPositionalThreshold = 0.3f
    )

    // A page takes over the moment it is more than half on screen, not when the scroll has stopped:
    // only the picture and sound switch here. Everything else waits for the scroll to come to rest.
    LaunchedEffect(pager, controller) {
        snapshotFlow { pager.currentPage }.collect { page -> controller.onPage(page) }
    }
    LaunchedEffect(pager, controller) {
        snapshotFlow { pager.settledPage }.collect { page ->
            controller.onSettled(page)
            controller.items.getOrNull(page)?.let { vm.focusShort(it) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            flingBehavior = fling,
            key = { controller.items[it].url }
        ) { page ->
            ShortPage(
                page = page,
                controller = controller,
                vm = vm,
                onComments = { showComments = true }
            )
        }

        // Top bar (hidden in clear screen mode)
        if (!controller.clearScreen) Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::closeShorts) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = controller::toggleMute) {
                Icon(
                    if (controller.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = if (controller.muted) "Unmute" else "Mute",
                    tint = Color.White
                )
            }
            IconButton(onClick = {
                vm.closeShorts()
                vm.select(Tab.Search)
                vm.startEditing()
            }) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color.White)
            }
            IconButton(onClick = { showMore = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }

        SeekLine(controller, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showComments) {
        CommentsSheet(vm = vm, onDismiss = { showComments = false })
    }

    if (showMore) {
        val url = controller.items.getOrNull(controller.current)?.url
        val info = url?.let { (controller.infos[it] as? Load.Ready)?.data }
        ShortsMoreSheet(
            info = info,
            selectedQuality = controller.slotFor(controller.current).quality,
            speed = controller.speed,
            onQuality = controller::setQuality,
            onSpeed = controller::setSpeed,
            onDownload = {
                showMore = false
                downloadFor = info
            },
            onSaveToPhone = {
                showMore = false
                phoneSaveFor = info
            },
            onDismiss = { showMore = false },
            captionLang = controller.captionLang,
            onCaption = controller::chooseCaptionLang,
            onClearScreen = {
                showMore = false
                controller.showClearScreen(true)
            },
            onSaveToPlaylist = {
                showMore = false
                vm.loadPlaylists()
                showSave = true
            },
            onNotInterested = {
                showMore = false
                skipWithFeedback(controller, pager, scope, context, channelWide = false)
            },
            onDontRecommend = {
                showMore = false
                skipWithFeedback(controller, pager, scope, context, channelWide = true)
            },
            limitMin = controller.limitMin,
            usedMinutes = controller.usedMinutes(),
            onLimit = controller::setLimit
        )
    }
    if (showSave) SaveSheet(vm = vm, onDismiss = { showSave = false })
    downloadFor?.let { DownloadSheet(info = it, onDismiss = { downloadFor = null }) }
    phoneSaveFor?.let { PhoneSaveSheet(info = it, onDismiss = { phoneSaveFor = null }) }

    // The Shorts timer: the daily limit is reached. One tap lets the viewer carry on, or leave.
    if (controller.breakDue) {
        AlertDialog(
            onDismissRequest = controller::dismissBreak,
            title = { Text("Time for a break") },
            text = { Text("You've watched ${controller.limitMin} minutes of Shorts today, the daily limit you set.") },
            confirmButton = { TextButton(onClick = controller::dismissBreak) { Text("Keep watching") } },
            dismissButton = {
                TextButton(onClick = {
                    controller.dismissBreak()
                    vm.closeShorts()
                }) { Text("Close Shorts") }
            }
        )
    }
}

/**
 * "Not interested" / "Don't recommend this channel": tells YouTube when it gave this Short a feedback option,
 * then moves on to the next Short either way.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun skipWithFeedback(
    controller: ShortsController,
    pager: androidx.compose.foundation.pager.PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    channelWide: Boolean
) {
    val sent = controller.sendFeedback(channelWide)
    val message = when {
        sent && channelWide -> "Okay, we'll recommend this channel less"
        sent -> "Okay, we'll show fewer Shorts like this"
        else -> "Skipped. YouTube gave no feedback option for this Short"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    val next = (pager.currentPage + 1).coerceAtMost(controller.items.lastIndex)
    if (next != pager.currentPage) scope.launch { pager.animateScrollToPage(next) }
}

@Composable
private fun ShortPage(
    page: Int,
    controller: ShortsController,
    vm: AppViewModel,
    onComments: () -> Unit
) {
    val context = LocalContext.current
    val item = controller.items[page]
    val slot = controller.slotFor(page)
    val mine = slot.url == item.url
    val state = controller.infos[item.url]
    val info = (state as? Load.Ready)?.data
    val failure = (state as? Load.Failed)?.message
    val error = failure ?: if (mine) slot.error else null
    val isCurrent = page == controller.current
    val id = videoIdOf(item.url)

    // The Short on screen keeps its live copy (with the viewer's own taps); the others use the copy fetched ahead.
    val meta = (if (isCurrent) vm.meta?.takeIf { it.videoId == id } else null) ?: vm.cachedMeta(id)

    val textureView = remember { TextureView(context) }
    // Only the page that owns this player draws its picture; a page that has lost it lets go.
    DisposableEffect(slot, textureView, mine) {
        if (mine) slot.player.setVideoTextureView(textureView)
        onDispose { if (mine) slot.player.clearVideoTextureView(textureView) }
    }

    // The tap handler below lives across recompositions, so it reads these through updated state.
    val latestError by rememberUpdatedState(error)
    val latestReaction by rememberUpdatedState(meta?.reaction)

    var heartTick by remember { mutableIntStateOf(0) }
    var heartVisible by remember { mutableStateOf(false) }
    var heartAt by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(heartTick) {
        if (heartTick > 0) {
            heartVisible = true
            delay(650)
            heartVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ShortPicture(
            slot = slot,
            mine = mine,
            thumb = item.thumb,
            failed = error != null,
            textureView = textureView
        )

        // Fades so white text stays readable on any picture (gone in clear screen mode).
        if (!controller.clearScreen) Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
        )
        if (!controller.clearScreen) Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
        )

        // Tap: pause / play, at once. Double tap: like (the first tap's pause is taken back).
        // Hold on a screen edge: 2x while held; swipe down while holding to keep 2x on.
        // In clear screen mode a tap brings the icons back instead.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(isCurrent) {
                    var lastTapAt = 0L
                    var lastTapAtPosition = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var timedOut = true
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            val released = waitForUpOrCancellation()
                            timedOut = false
                            released
                        }
                        if (timedOut) {
                            // Still down after the long-press time: on an edge of the screen that is the 2x hold.
                            val edge = down.position.x < size.width * EDGE_SHARE ||
                                down.position.x > size.width * (1f - EDGE_SHARE)
                            if (!isCurrent || latestError != null || controller.paused || controller.clearScreen || !edge) {
                                return@awaitEachGesture
                            }
                            controller.boostStart()
                            var locked = false
                            try {
                                var swiped = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    // The hold owns the finger now: the pager must not take the swipe.
                                    change.consume()
                                    if (!change.pressed) break
                                    swiped += change.position.y - change.previousPosition.y
                                    if (!locked && swiped > LOCK_SWIPE_DP * density) {
                                        locked = true
                                        controller.boostLock()
                                    }
                                }
                            } finally {
                                if (!locked) controller.boostEnd()
                            }
                            return@awaitEachGesture
                        }
                        if (up == null || !isCurrent) return@awaitEachGesture
                        if (controller.clearScreen) {
                            controller.showClearScreen(false)
                            return@awaitEachGesture
                        }
                        val close = (up.position - lastTapAtPosition).getDistance() < viewConfiguration.touchSlop * 6
                        if (up.uptimeMillis - lastTapAt < DOUBLE_TAP_MS && close) {
                            lastTapAt = 0L
                            if (controller.paused) controller.togglePause()
                            heartAt = up.position
                            heartTick++
                            if (latestReaction != Reaction.Liked) vm.react(Reaction.Liked)
                        } else {
                            lastTapAt = up.uptimeMillis
                            lastTapAtPosition = up.position
                            if (latestError == null) controller.togglePause()
                        }
                    }
                }
        )

        if (isCurrent && error == null && (!mine || !slot.firstFrame || slot.buffering)) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(34.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        }

        AnimatedVisibility(
            visible = isCurrent && controller.paused && !controller.clearScreen,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.7f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f)
        ) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }

        // Paused: tap the speaker under the play button to mute or unmute.
        AnimatedVisibility(
            visible = isCurrent && controller.paused && !controller.clearScreen,
            modifier = Modifier.align(Alignment.Center).offset(y = 72.dp),
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { controller.toggleMute() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (controller.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = if (controller.muted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2x indicator: shown while the edge is held, and stays (tap to unlock) once locked.
        AnimatedVisibility(
            visible = isCurrent && (controller.boosting || controller.boostLocked),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp),
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(150))
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = controller.boostLocked) { controller.boostUnlock() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (controller.boostLocked) "2x locked · tap to unlock" else "2x",
                    color = Color.White,
                    style = ShortText.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }

        // Captions, when the viewer turned them on for this language.
        val captionText = slot.caption
        if (!controller.clearScreen && isCurrent && mine && controller.captionLang != null && captionText.isNotBlank()) {
            Text(
                captionText,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = ShortText.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, shadow = null),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 190.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // The like burst appears where the finger tapped.
        AnimatedVisibility(
            visible = heartVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset((heartAt.x - 52.dp.toPx()).roundToInt(), (heartAt.y - 52.dp.toPx()).roundToInt()) },
            enter = fadeIn(tween(80)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.3f),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 1.3f)
        ) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = HeartRed, modifier = Modifier.size(104.dp))
        }

        if (error != null) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 20.sp)
                TextButton(onClick = { controller.retry(page) }) {
                    Text("Try again", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        val channelName = info?.channel?.takeIf { it.isNotBlank() }
            ?: meta?.channelName
            ?: item.channel.takeIf { it.isNotBlank() && it != "Shorts" }
        val title = info?.title?.takeIf { it.isNotBlank() } ?: item.title
        val avatar = meta?.channelAvatar ?: info?.channelAvatar
        val channelId = meta?.channelId ?: info?.channelId

        // Right rail (gone in clear screen mode). A heart replaces like and dislike, as on YouTube.
        if (!controller.clearScreen) Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val liked = meta?.reaction == Reaction.Liked
            RailAction(
                icon = if (liked) Icons.Rounded.Favorite else Icons.Outlined.HeartOutlined,
                label = meta?.likeCount ?: "Like",
                tint = if (liked) HeartRed else Color.White
            ) { if (isCurrent) vm.react(Reaction.Liked) }
            RailAction(icon = Icons.Outlined.ModeCommentOutlined, label = meta?.commentCount ?: "Comments") {
                if (isCurrent) onComments()
            }
            RailAction(icon = Icons.Outlined.ShareOutlined, label = "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.url)
                }
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (avatar != null) {
                val tile = RoundedCornerShape(9.dp)
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(38.dp)
                        .clip(tile)
                        .border(2.dp, Color.White, tile)
                        .clickable(enabled = channelId != null) {
                            vm.closeShorts()
                            vm.openChannel(channelId, channelName.orEmpty(), avatar)
                        }
                ) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Channel, subscribe, title (gone in clear screen mode)
        if (!controller.clearScreen) Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 82.dp, bottom = 32.dp)
        ) {
            if (channelName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable(enabled = channelId != null) {
                                vm.closeShorts()
                                vm.openChannel(channelId, channelName, avatar)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatar != null) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                channelName.trim().firstOrNull()?.uppercase().orEmpty(),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        channelName,
                        color = Color.White,
                        style = ShortText.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Always laid out, so it fades in where it will be rather than pushing things around.
                    val subscribeTarget = meta?.channelId ?: info?.channelId
                    val pillAlpha by animateFloatAsState(
                        targetValue = if (subscribeTarget != null) 1f else 0f,
                        animationSpec = tween(200),
                        label = "pill"
                    )
                    val subscribed = subscribeTarget?.let { vm.subscriptions[it] } ?: meta?.subscribed ?: false
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .alpha(pillAlpha)
                            .clip(RoundedCornerShape(50))
                            .background(if (subscribed) Color.White.copy(alpha = 0.22f) else Color.White)
                            .clickable(enabled = subscribeTarget != null) {
                                vm.toggleSubscribe(subscribeTarget, subscribed)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (subscribed) "Subscribed" else "Subscribe",
                            color = if (subscribed) Color.White else Color.Black,
                            style = ShortText.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else if (error == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)))
                    Spacer(Modifier.width(10.dp))
                    SkeletonBar(width = 120.dp, height = 14.dp)
                }
                Spacer(Modifier.height(12.dp))
            }

            if (title.isNotBlank()) {
                var expanded by remember(item.url) { mutableStateOf(false) }
                Text(
                    title,
                    color = Color.White,
                    style = ShortText.copy(fontSize = 14.sp),
                    maxLines = if (expanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .animateContentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                )
            } else if (error == null) {
                SkeletonBar(width = 230.dp, height = 12.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBar(width = 150.dp, height = 12.dp)
            }
        }
    }
}

/**
 * The picture of one Short: the video, with its thumbnail over it until the first frame is on screen.
 *
 * It lives in its own composable so the state it reads (picture shape, first frame) only redraws this
 * and never the whole page. The shape is known before the player has decoded anything (from the stream
 * list), and until it is, a Short is taken to be vertical and fills the screen, so the thumbnail and the
 * video occupy exactly the same place and nothing jumps when the video arrives.
 */
@Composable
private fun ShortPicture(
    slot: ShortsController.Slot,
    mine: Boolean,
    thumb: String?,
    failed: Boolean,
    textureView: TextureView
) {
    val thumbAlpha by animateFloatAsState(
        targetValue = if (mine && slot.firstFrame && !failed) 0f else 1f,
        animationSpec = tween(120),
        label = "thumb"
    )
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val boxAspect = maxWidth / maxHeight
        val measured = if (mine) slot.aspect.takeIf { it > 0f } ?: slot.knownAspect else 0f
        val aspect = if (measured > 0f) measured else 9f / 16f

        // Fills the screen when the video is close to 9:16 (a little is cropped), otherwise fits inside it.
        val crop = if (aspect >= boxAspect) 1f - boxAspect / aspect else 1f - aspect / boxAspect
        val zoom = aspect < 1f && crop <= 0.25f
        val wide = aspect >= boxAspect
        val w: Dp
        val h: Dp
        if (zoom == wide) {
            h = maxHeight
            w = maxHeight * aspect
        } else {
            w = maxWidth
            h = maxWidth / aspect
        }

        AndroidView(
            factory = { textureView },
            modifier = Modifier.align(Alignment.Center).requiredSize(w, h)
        )
        AsyncImage(
            model = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(w, h)
                .graphicsLayer { alpha = thumbAlpha }
        )
    }
}

/** The thin line under the Short: shows where it is, and can be dragged to move around in it. */
@Composable
private fun SeekLine(controller: ShortsController, modifier: Modifier = Modifier) {
    val thickness by animateDpAsState(
        targetValue = if (controller.scrubbing) 6.dp else 2.dp,
        animationSpec = tween(120),
        label = "seek"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(controller) {
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        controller.scrubStart()
                        controller.scrubTo(start.x / size.width.coerceAtLeast(1))
                    },
                    onDragEnd = { controller.scrubEnd() },
                    onDragCancel = { controller.scrubEnd() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        controller.scrubTo(change.position.x / size.width.coerceAtLeast(1))
                    }
                )
            }
    ) {
        // Read while drawing, so the moving line never recomposes the screen.
        Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(thickness)) {
            drawRect(Color.White.copy(alpha = 0.28f))
            drawRect(Color.White, size = Size(size.width * controller.progress, size.height))
        }
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.16f))
    )
}

@Composable
private fun RailAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = tween(90),
        label = "rail"
    )
    Column(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = RailText, maxLines = 1)
    }
}

private val Shade = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1.5f), blurRadius = 6f)

private val RailText = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.sp,
    shadow = Shade
)

/** Bengali-safe text: no letter spacing, a roomy line with nothing trimmed so vowel marks are not cut. */
private val ShortText = TextStyle(
    fontSize = 14.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp,
    shadow = Shade,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)
