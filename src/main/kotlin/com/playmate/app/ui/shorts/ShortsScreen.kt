package com.playmate.app.ui.shorts

import android.content.Intent
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ModeComment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.playmate.app.data.Reaction
import com.playmate.app.data.videoIdOf
import com.playmate.app.ui.components.findActivity
import com.playmate.app.ui.player.CommentsSheet
import com.playmate.app.ui.player.PlayerHolder
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.delay

/**
 * The Shorts screen: one Short per page, swipe up for the next, never ends.
 * Tap pauses, double tap likes; like / dislike / comments / share on the right,
 * channel + subscribe + title at the bottom, a thin progress line under it all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(launch: ShortsLaunch, vm: AppViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val controller = remember(launch) {
        ShortsController(context.applicationContext, scope, vm.viewModelScope, launch.items, launch.start)
    }
    var showComments by remember { mutableStateOf(false) }

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
            delay(100)
        }
    }

    val pager = rememberPagerState(initialPage = controller.current, pageCount = { controller.items.size })
    LaunchedEffect(pager, controller) {
        snapshotFlow { pager.settledPage }.collect { page ->
            controller.onPage(page)
            controller.items.getOrNull(page)?.let { vm.focusShort(it) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { controller.items[it].url }
        ) { page ->
            ShortPage(
                page = page,
                controller = controller,
                vm = vm,
                onComments = { showComments = true }
            )
        }

        // Top bar
        Row(
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
        }

        // Progress line
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.28f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(controller.progress)
                    .height(2.dp)
                    .background(Color.White)
            )
        }
    }

    if (showComments) {
        CommentsSheet(vm = vm, onDismiss = { showComments = false })
    }
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
    val meta = vm.meta?.takeIf { it.videoId == id }

    val textureView = remember { TextureView(context) }
    DisposableEffect(slot, textureView) {
        slot.player.setVideoTextureView(textureView)
        onDispose { slot.player.clearVideoTextureView(textureView) }
    }

    // The tap handler below lives across recompositions, so it reads these through updated state.
    val latestError by rememberUpdatedState(error)
    val latestReaction by rememberUpdatedState(meta?.reaction)

    var heartTick by remember { mutableIntStateOf(0) }
    var heartVisible by remember { mutableStateOf(false) }
    LaunchedEffect(heartTick) {
        if (heartTick > 0) {
            heartVisible = true
            delay(650)
            heartVisible = false
        }
    }

    val thumbAlpha by animateFloatAsState(
        targetValue = if (mine && slot.firstFrame && error == null) 0f else 1f,
        animationSpec = tween(160),
        label = "thumb"
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The picture: fills the screen when the video is close to 9:16, otherwise fits inside it.
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val boxAspect = maxWidth / maxHeight
            val aspect = if (mine) slot.aspect else 0f
            var w = maxWidth
            var h = maxHeight
            if (aspect > 0f) {
                val crop = if (aspect >= boxAspect) 1f - boxAspect / aspect else 1f - aspect / boxAspect
                val zoom = aspect < 1f && crop <= 0.25f
                val wide = aspect >= boxAspect
                if (zoom == wide) {
                    h = maxHeight
                    w = maxHeight * aspect
                } else {
                    w = maxWidth
                    h = maxWidth / aspect
                }
            }
            AndroidView(
                factory = { textureView },
                modifier = Modifier.align(Alignment.Center).requiredSize(w, h)
            )
        }

        AsyncImage(
            model = item.thumb,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = thumbAlpha }
        )

        // Fades so white text stays readable on any picture.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )

        // Tap: pause / play. Double tap: like.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(isCurrent) {
                    detectTapGestures(
                        onTap = { if (isCurrent && latestError == null) controller.togglePause() },
                        onDoubleTap = {
                            if (isCurrent) {
                                heartTick++
                                if (latestReaction != Reaction.Liked) vm.react(Reaction.Liked)
                            }
                        }
                    )
                }
        )

        if (error == null && (!mine || !slot.firstFrame || slot.buffering)) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(34.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        }

        AnimatedVisibility(
            visible = isCurrent && controller.paused,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }

        AnimatedVisibility(
            visible = heartVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.4f),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 1.25f)
        ) {
            Icon(Icons.Rounded.ThumbUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(96.dp))
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

        // Right rail
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 6.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RailButton(
                icon = Icons.Rounded.ThumbUp,
                label = meta?.likeCount ?: "Like",
                tint = if (meta?.reaction == Reaction.Liked) Accent else Color.White
            ) { if (isCurrent) vm.react(Reaction.Liked) }
            RailButton(
                icon = Icons.Rounded.ThumbDown,
                label = "Dislike",
                tint = if (meta?.reaction == Reaction.Disliked) Accent else Color.White
            ) { if (isCurrent) vm.react(Reaction.Disliked) }
            RailButton(icon = Icons.Rounded.ModeComment, label = meta?.commentCount ?: "Comments") {
                if (isCurrent) onComments()
            }
            RailButton(icon = Icons.Rounded.Share, label = "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.url)
                }
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        // Channel, subscribe, title
        val channelName = info?.channel?.takeIf { it.isNotBlank() }
            ?: meta?.channelName
            ?: item.channel.takeIf { it.isNotBlank() && it != "Shorts" }
        val title = info?.title?.takeIf { it.isNotBlank() } ?: item.title
        val avatar = meta?.channelAvatar ?: info?.channelAvatar
        val channelId = meta?.channelId ?: info?.channelId
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 84.dp, bottom = 22.dp)
        ) {
            if (channelName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(34.dp)
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
                    val subscribeTarget = meta?.channelId
                    if (isCurrent && subscribeTarget != null) {
                        val subscribed = vm.subscriptions[subscribeTarget] ?: meta?.subscribed ?: false
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (subscribed) Color.White.copy(alpha = 0.22f) else Color.White)
                                .clickable { vm.toggleSubscribe(subscribeTarget, subscribed) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                if (subscribed) "Subscribed" else "Subscribe",
                                color = if (subscribed) Color.White else Color.Black,
                                style = ShortText.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (title.isNotBlank()) {
                Text(
                    title,
                    color = Color.White,
                    style = ShortText.copy(fontSize = 14.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Bengali-safe text: no letter spacing, a roomy line with nothing trimmed so vowel marks are not cut. */
private val ShortText = TextStyle(
    fontSize = 14.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)
