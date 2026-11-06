package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.playmate.app.data.CommentItem
import com.playmate.app.ui.components.agoText
import com.playmate.app.ui.components.formatCount
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private val Hashtag = Regex("#[\\p{L}\\p{N}_]+")

/**
 * Title and the quiet line under it (views, age, hashtags). Tapping it opens the description
 * panel under the player (see [DescriptionPanel]); [onOpen] is null while the video is loading.
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
    val tags = remember(description) { Hashtag.findAll(description).map { it.value }.distinct().take(2).toList() }
    val viewsText = formatCount(views)?.let { "$it views" }
    val ago = agoText(uploaded)

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
        Spacer(Modifier.height(6.dp))
        val line = buildAnnotatedString {
            val parts = listOfNotNull(channel.takeIf { it.isNotBlank() }, viewsText, ago)
            parts.forEachIndexed { i, part ->
                if (i > 0) append("  ·  ")
                if (part == viewsText) withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = scheme.onSurface)) { append(part) }
                else append(part)
            }
            tags.forEach {
                append("  ")
                withStyle(SpanStyle(color = scheme.onSurfaceVariant.copy(alpha = 0.8f))) { append(it) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onOpen != null) {
                Text(
                    "  …more",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface
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

private val BarShape = RoundedCornerShape(18.dp)
private val SlotShape = RoundedCornerShape(14.dp)
private val BarHeight = 52.dp
private val GlyphSize = 24.dp

/**
 * One place in the action bar. It has no label, so it carries its name another way: TalkBack reads
 * [label], and a long press shows it as a toast, the way Android's own toolbars name their icons.
 * [lit] is the switched-on look (the text colour as the fill, the page colour as the glyph);
 * [state] is what a screen reader says about a toggle.
 */
@Composable
private fun RowScope.BarSlot(
    label: String,
    weight: Float = 1f,
    lit: Boolean = false,
    state: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (lit) scheme.onSurface else scheme.onSurface.copy(alpha = 0f),
        animationSpec = tween(180),
        label = "slotContainer"
    )
    val tint = if (lit) scheme.surface else scheme.onSurface
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(horizontal = 1.dp, vertical = 4.dp)
            .tonalPress(
                shape = SlotShape,
                color = container,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                },
                onClick = onClick
            )
            .semantics {
                contentDescription = label
                if (state != null) stateDescription = state
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) { content() }
    }
}

@Composable
private fun SlotGlyph(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(GlyphSize))
}

/**
 * Like and dislike side by side with a small hairline between them. Liked / disliked shows the
 * YouTube way: the thumb is an outline when off and a solid shape when on, and the like thumb
 * throws a few rays and the count rolls when it is turned on.
 */
@Composable
private fun RowScope.ReactionSlots(
    reaction: Reaction,
    likeCount: String?,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    val liked = reaction == Reaction.Liked
    val disliked = reaction == Reaction.Disliked
    val haptic = LocalHapticFeedback.current

    BarSlot(
        label = "Like",
        weight = 1.9f,
        state = if (liked) "Liked" else null,
        onClick = {
            if (!liked) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onLike()
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedThumb(
                on = liked,
                outline = PmIcons.ThumbUp,
                solid = PmIcons.ThumbUpSolid,
                size = GlyphSize,
                reach = 7.dp
            )
            likeCount?.let {
                RollingCount(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
    Box(Modifier.width(1.dp).height(20.dp).background(Tonal.hairline()))
    BarSlot(
        label = "Dislike",
        state = if (disliked) "Disliked" else null,
        onClick = onDislike
    ) {
        AnimatedThumb(
            on = disliked,
            outline = PmIcons.ThumbDown,
            solid = PmIcons.ThumbDownSolid,
            size = GlyphSize,
            rays = false
        )
    }
}

/** Download: a ring with the percent while it downloads, a tick once it is saved in the app. */
@Composable
private fun RowScope.DownloadSlot(info: PlayableInfo?, onClick: () -> Unit) {
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
    BarSlot(label = label, onClick = onClick) {
        when {
            active != null -> CircularProgressIndicator(
                progress = { percent / 100f },
                color = LocalContentColor.current,
                trackColor = LocalContentColor.current.copy(alpha = 0.25f),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(20.dp)
            )
            saved -> SlotGlyph(PmIcons.DownloadDone)
            else -> SlotGlyph(PmIcons.Download)
        }
    }
}

/**
 * The bar under a video, in the order people reach for it: Like and dislike, Share, Download,
 * Background play, Save, Save to phone. Comments are not here: they sit right below.
 *
 * All six live in one soft tonal bar that fits the width, so nothing scrolls away and nothing is
 * cut off. There are no labels: each glyph is drawn to be told apart on its own (Download is an
 * arrow onto a line, Save to phone is an arrow into a phone), and each says its name to a screen
 * reader and on a long press. The one toggle, Background play, fills in when it is on.
 */
@Composable
fun ActionRow(
    vm: AppViewModel,
    meta: WatchMeta?,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    val reaction = meta?.reaction ?: Reaction.None
    val info = (vm.playing?.info as? Load.Ready)?.data
    var phoneOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var background by remember { mutableStateOf(PlaybackPrefs.background(context)) }

    // 0 = closed, 1 = pick a quality to download, 2 = this video's download status
    var downloadSheet by remember { mutableIntStateOf(0) }

    Column {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(BarHeight)
                .clip(BarShape)
                .background(Tonal.fill())
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionSlots(
                reaction = reaction,
                likeCount = meta?.likeCount,
                onLike = { vm.react(Reaction.Liked) },
                onDislike = { vm.react(Reaction.Disliked) }
            )
            BarSlot(label = "Share", onClick = onShare) { SlotGlyph(PmIcons.Share) }
            DownloadSlot(info) {
                if (info == null) {
                    Toast.makeText(context, "This video is still loading.", Toast.LENGTH_SHORT).show()
                } else if (info.live) {
                    Toast.makeText(context, "Live streams can't be downloaded.", Toast.LENGTH_SHORT).show()
                } else {
                    val busy = activeDownload(info.url) != null || OfflineStore.forUrl(info.url).isNotEmpty()
                    downloadSheet = if (busy) 2 else 1
                }
            }
            BarSlot(
                label = "Background play",
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
            ) { SlotGlyph(PmIcons.Headphones) }
            BarSlot(label = "Save", onClick = onSave) { SlotGlyph(PmIcons.Bookmark) }
            BarSlot(label = "Save to phone", onClick = { if (info != null) phoneOpen = true }) {
                SlotGlyph(PmIcons.PhoneSave)
            }
        }

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
