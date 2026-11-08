package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.playmate.app.data.CommentItem
import com.playmate.app.ui.components.agoText
import com.playmate.app.ui.components.formatCount
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.data.OfflineStore
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.PlaybackPrefs
import com.playmate.app.data.Reaction
import com.playmate.app.data.SaveState
import com.playmate.app.data.WatchMeta
import com.playmate.app.data.activeDownload
import com.playmate.app.ui.components.AnimatedThumb
import com.playmate.app.ui.components.RollingCount
import com.playmate.app.ui.components.SubscribeButton
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.playmate.app.ui.components.PmIcons
import com.playmate.app.ui.theme.Tonal
import com.playmate.app.ui.theme.tonalPress

/**
 * Title and the quiet line under it (views and a short age like "7y ago"), with the like /
 * dislike pill at the right end of that line. Tapping the title or the line opens the description
 * panel under the player (see [DescriptionPanel]); [onOpen] is null while the video is loading,
 * and then there is no pill.
 */
@Composable
fun TitleBlock(
    title: String,
    channel: String,
    views: Long,
    uploaded: String?,
    onOpen: (() -> Unit)?,
    description: String = ""
) {
    val scheme = MaterialTheme.colorScheme
    val viewsText = formatCount(views)?.let { "$it views" }
    val ago = agoText(uploaded)?.let { shortAgo(it) }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onOpen != null, onClick = { onOpen?.invoke() })
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 6.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 25.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(px(12)))
        val line = buildAnnotatedString {
            val parts = listOfNotNull(channel.takeIf { it.isNotBlank() }, viewsText, ago)
            parts.forEachIndexed { i, part ->
                if (i > 0) append("  ·  ")
                if (part == viewsText) withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = scheme.onSurface)) { append(part) }
                else append(part)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onOpen != null) {
                val vm: AppViewModel = viewModel()
                Spacer(Modifier.width(px(24)))
                ReactionPill(
                    reaction = vm.meta?.reaction ?: Reaction.None,
                    likeCount = vm.meta?.likeCount,
                    onLike = { vm.react(Reaction.Liked) },
                    onDislike = { vm.react(Reaction.Disliked) }
                )
            }
        }
    }
}

/** Channel avatar, name, subscribers and the subscribe button. Tapping the channel opens it. */
@Composable
fun ChannelRow(vm: AppViewModel, info: PlayableInfo?, meta: WatchMeta?, seedChannel: String) {
    val channelId = meta?.channelId ?: info?.channelId
    val name = meta?.channelName ?: info?.channel?.takeIf { it.isNotBlank() } ?: seedChannel
    val avatar = meta?.channelAvatar ?: info?.channelAvatar
    val subscribed = channelId?.let { vm.subscriptions[it] } ?: meta?.subscribed ?: false

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = channelId != null) { vm.openChannel(channelId, name, avatar) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Tonal.fill())
            )
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                meta?.subscribers?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        SubscribeButton(subscribed = subscribed, onClick = { vm.toggleSubscribe(channelId, subscribed) })
    }
}

/**
 * Every size from here down is in screen pixels, taken straight off the reference screenshot
 * (a phone 720 pixels wide), so the pill and the buttons land at exactly the same size on this
 * phone. To make the whole look a little bigger or smaller, change [PX_SCALE].
 */
private const val PX_SCALE = 1f

@Composable
private fun px(v: Int): Dp = with(LocalDensity.current) { (v * PX_SCALE).toDp() }

@Composable
private fun pxSp(v: Int): TextUnit = with(LocalDensity.current) { (v * PX_SCALE).toSp() }

/**
 * The icons of the row under the video, same shapes as [PmIcons] but drawn with a thick line so
 * they read big and bold.
 */
private object BoldIcons {
    private fun bold(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.3f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    val Share: ImageVector by lazy {
        bold("BoldShare", "M14.3 4.8l6.2 6.1-6.2 6.1", "M20 10.9h-8.6a6.9 6.9 0 0 0-6.9 6.9v1.4")
    }
    val Download: ImageVector by lazy {
        bold("BoldDownload", "M12 4.2v10.6", "M7.4 10.4l4.6 4.6 4.6-4.6", "M5 19.6h14")
    }
    val DownloadDone: ImageVector by lazy {
        bold("BoldDownloadDone", "M7.2 11.6l3.4 3.4 6.2-6.4", "M5 19.6h14")
    }
    val Headphones: ImageVector by lazy {
        bold(
            "BoldHeadphones",
            "M4 14.5V12a8 8 0 0 1 16 0v2.5",
            "M4 14.5h2.4a1.1 1.1 0 0 1 1.1 1.1v3.3a1.1 1.1 0 0 1-1.1 1.1h-.8A1.6 1.6 0 0 1 4 18.4z",
            "M20 14.5h-2.4a1.1 1.1 0 0 0-1.1 1.1v3.3a1.1 1.1 0 0 0 1.1 1.1h.8a1.6 1.6 0 0 0 1.6-1.6z"
        )
    }
    val Bookmark: ImageVector by lazy {
        bold("BoldBookmark", "M7.2 3.8h9.6a1.4 1.4 0 0 1 1.4 1.4v14.6l-6.2-4.3-6.2 4.3V5.2a1.4 1.4 0 0 1 1.4-1.4z")
    }
    val PhoneSave: ImageVector by lazy {
        bold(
            "BoldPhoneSave",
            "M8.2 2.8h7.6a1.8 1.8 0 0 1 1.8 1.8v14.8a1.8 1.8 0 0 1-1.8 1.8H8.2a1.8 1.8 0 0 1-1.8-1.8V4.6a1.8 1.8 0 0 1 1.8-1.8z",
            "M12 7.6v6.2",
            "M9.4 11.4l2.6 2.6 2.6-2.6"
        )
    }
}

/**
 * One button of the row under the video: a big bold glyph with its name printed under it, no tile
 * behind it. Pressed, a round grey patch fades in behind the glyph and the glyph shrinks a
 * little. [label] is what a screen reader says; [text] is what is printed. [lit] is the
 * switched-on look (a disc of the text colour behind the glyph, the page colour as the glyph);
 * [state] is what a screen reader says about a toggle.
 */
@Composable
private fun RowScope.ActionTile(
    label: String,
    text: String,
    lit: Boolean = false,
    state: String? = null,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val patch by animateColorAsState(
        targetValue = if (pressed) Tonal.fillStrong() else Tonal.fillStrong().copy(alpha = 0f),
        animationSpec = tween(120),
        label = "tilePatch"
    )
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(120),
        label = "tileSqueeze"
    )
    val disc by animateColorAsState(
        targetValue = if (lit) scheme.onSurface else scheme.onSurface.copy(alpha = 0f),
        animationSpec = tween(180),
        label = "tileDisc"
    )
    val tint = if (lit) scheme.surface else scheme.onSurface

    Column(
        Modifier
            .weight(1f)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                if (state != null) stateDescription = state
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(px(72)), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(px(108))
                    .clip(CircleShape)
                    .background(patch)
            )
            Box(
                Modifier
                    .size(px(72))
                    .graphicsLayer {
                        scaleX = squeeze
                        scaleY = squeeze
                    }
                    .clip(CircleShape)
                    .background(disc),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides tint) { glyph() }
            }
        }
        Spacer(Modifier.height(px(4)))
        Text(
            text,
            style = TextStyle(
                fontSize = pxSp(22),
                lineHeight = pxSp(26),
                fontWeight = FontWeight.Normal
            ),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SlotGlyph(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(px(72)))
}

/**
 * Like and dislike as one rounded pill with a thin line between them, at the right end of the
 * line under the title. Liked / disliked shows the YouTube way: the thumb is an outline when off
 * and a solid shape when on, and the like thumb throws a few rays and the count rolls when it is
 * turned on.
 */
@Composable
private fun ReactionPill(
    reaction: Reaction,
    likeCount: String?,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    val liked = reaction == Reaction.Liked
    val disliked = reaction == Reaction.Disliked
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val glyph = px(36)

    Row(
        Modifier
            .height(px(56))
            .clip(CircleShape)
            .background(Tonal.fill()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .tonalPress(
                    shape = RectangleShape,
                    color = Color.Transparent,
                    onClick = {
                        if (!liked) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLike()
                    }
                )
                .padding(horizontal = px(22))
                .semantics {
                    contentDescription = "Like"
                    if (liked) stateDescription = "Liked"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                AnimatedThumb(
                    on = liked,
                    outline = PmIcons.ThumbUp,
                    solid = PmIcons.ThumbUpSolid,
                    size = glyph,
                    reach = px(14)
                )
                likeCount?.let {
                    RollingCount(
                        it,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = pxSp(21),
                            lineHeight = pxSp(26)
                        ),
                        color = LocalContentColor.current,
                        modifier = Modifier.padding(start = px(12))
                    )
                }
            }
        }
        Box(
            Modifier
                .width(px(2))
                .height(px(34))
                .background(Tonal.hairline())
        )
        Box(
            Modifier
                .fillMaxHeight()
                .tonalPress(
                    shape = RectangleShape,
                    color = Color.Transparent,
                    onClick = onDislike
                )
                .padding(horizontal = px(22))
                .semantics {
                    contentDescription = "Dislike"
                    if (disliked) stateDescription = "Disliked"
                },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                AnimatedThumb(
                    on = disliked,
                    outline = PmIcons.ThumbDown,
                    solid = PmIcons.ThumbDownSolid,
                    size = glyph,
                    rays = false
                )
            }
        }
    }
}

/** Download: a ring with the percent while it downloads, a tick once it is saved in the app. */
@Composable
private fun RowScope.DownloadTile(info: PlayableInfo?, onClick: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val active = info?.let { activeDownload(it.url) }
    LaunchedEffect(active) {
        while (active != null) {
            delay(500)
            tick++
        }
    }
    val percent = remember(tick, active) { active?.percent ?: 0 }
    val saved = info?.let { OfflineStore.forUrl(it.url).isNotEmpty() } == true
    val label = when {
        active != null -> if (active.state == SaveState.Queued) "Download waiting" else "Downloading $percent%"
        saved -> "Downloaded"
        else -> "Download"
    }
    val text = when {
        active != null -> if (active.state == SaveState.Queued) "Waiting" else "$percent%"
        saved -> "Downloaded"
        else -> "Download"
    }
    ActionTile(label = label, text = text, onClick = onClick) {
        when {
            active != null -> CircularProgressIndicator(
                progress = { percent / 100f },
                color = LocalContentColor.current,
                trackColor = LocalContentColor.current.copy(alpha = 0.25f),
                strokeWidth = px(6),
                modifier = Modifier.size(px(46))
            )
            saved -> SlotGlyph(BoldIcons.DownloadDone)
            else -> SlotGlyph(BoldIcons.Download)
        }
    }
}

/**
 * The row under a video, in the order people reach for it: Share, Download, Background play,
 * Save, Save to phone. (Like and dislike sit in the pill at the end of the line under the title;
 * comments sit right below this row.)
 *
 * Five equal columns spread over the width, each a big glyph with its name under it, and a thin
 * line under the whole row. The one toggle, Background play, puts a disc behind its glyph when on.
 */
@Composable
fun ActionRow(
    vm: AppViewModel,
    meta: WatchMeta?,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    val info = (vm.playing?.info as? Load.Ready)?.data
    var phoneOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var background by remember { mutableStateOf(PlaybackPrefs.background(context)) }

    // 0 = closed, 1 = pick a quality to download, 2 = this video's download status
    var downloadSheet by remember { mutableIntStateOf(0) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = px(32))
                .padding(top = px(14), bottom = px(32)),
            verticalAlignment = Alignment.Top
        ) {
            ActionTile(label = "Share", text = "Share", onClick = onShare) { SlotGlyph(BoldIcons.Share) }
            DownloadTile(info) {
                if (info == null) {
                    Toast.makeText(context, "This video is still loading.", Toast.LENGTH_SHORT).show()
                } else if (info.live) {
                    Toast.makeText(context, "Live streams can't be downloaded.", Toast.LENGTH_SHORT).show()
                } else {
                    val busy = activeDownload(info.url) != null || OfflineStore.forUrl(info.url).isNotEmpty()
                    downloadSheet = if (busy) 2 else 1
                }
            }
            ActionTile(
                label = "Background play",
                text = "Background",
                lit = background,
                state = if (background) "On" else "Off",
                onClick = {
                    background = !background
                    PlaybackPrefs.setBackground(context, background)
                    Toast.makeText(
                        context,
                        if (background) "Background play on" else "Background play off",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) { SlotGlyph(BoldIcons.Headphones) }
            ActionTile(label = "Save", text = "Save", onClick = onSave) { SlotGlyph(BoldIcons.Bookmark) }
            ActionTile(
                label = "Save to phone",
                text = "To phone",
                onClick = { if (info != null) phoneOpen = true }
            ) { SlotGlyph(BoldIcons.PhoneSave) }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(px(2))
                .background(Tonal.fill())
        )

        if (phoneOpen && info != null) {
            PhoneSaveSheet(info = info, onDismiss = { phoneOpen = false })
        }

        if (info != null) {
            if (downloadSheet == 1) {
                DownloadSheet(info = info, onDismiss = { downloadSheet = 0 })
            } else if (downloadSheet == 2) {
                DownloadStateSheet(
                    info = info,
                    onAnother = { downloadSheet = 1 },
                    onGoTo = {
                        downloadSheet = 0
                        vm.select(Tab.Downloads)
                        vm.minimizePlayer()
                    },
                    onDismiss = { downloadSheet = 0 }
                )
            }
        }
    }
}

/** The "Comments" card: count, and a preview of the first comment. Tapping it opens all of them. */
@Composable
fun CommentsCard(count: String?, preview: CommentItem?, state: Load<*>?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Tonal.fill())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Comments", style = MaterialTheme.typography.titleSmall)
            count?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Box(Modifier.weight(1f))
            Icon(
                Icons.Rounded.UnfoldMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        when {
            preview != null -> Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = preview.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Tonal.fillStrong())
                )
                Text(
                    preview.text,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            state is Load.Failed -> Text(
                "Comments are turned off or unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            state == null || state is Load.Loading -> Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Tonal.fillStrong())
            )
        }
    }
}

/** "Save to…": the account's playlists (Watch later included), each a checkbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Save to",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            when (val state = vm.playlists) {
                null, Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }

                is Load.Failed -> Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        "Couldn't load your playlists",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(
                        onClick = vm::loadPlaylists,
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("Try again") }
                }

                is Load.Ready -> Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                ) {
                    state.data.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.togglePlaylist(option) }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = option.contains, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(
                                option.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
