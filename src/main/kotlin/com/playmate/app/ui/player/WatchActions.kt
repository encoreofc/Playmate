package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
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
import com.playmate.app.ui.components.SubscribeButton
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.playmate.app.ui.theme.LocalPmDark
import com.playmate.app.ui.theme.Tonal
import com.playmate.app.ui.theme.tonalPress

private val Hashtag = Regex("#[\\p{L}\\p{N}_]+")

/**
 * Title and the quiet line under it (views, age, hashtags). Tapping opens the full description
 * right here: three big numbers (likes, views, uploaded) and the text itself.
 */
@Composable
fun TitleBlock(
    title: String,
    channel: String,
    views: Long,
    uploaded: String?,
    likes: String?,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tags = remember(description) { Hashtag.findAll(description).map { it.value }.distinct().take(2).toList() }
    val viewsText = formatCount(views)?.let { "$it views" }
    val ago = agoText(uploaded)
    val hasMore = description.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = hasMore, onClick = onToggle)
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 6.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 25.sp),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
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
            if (!expanded) {
                tags.forEach {
                    append("  ")
                    withStyle(SpanStyle(color = scheme.onSurfaceVariant.copy(alpha = 0.8f))) { append(it) }
                }
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
            if (hasMore && !expanded) {
                Text(
                    "  …more",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface
                )
            }
        }
    }

    if (expanded && hasMore) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Tonal.fill())
                .clickable(onClick = onToggle)
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                val stats = listOfNotNull(
                    likes?.let { it to "Likes" },
                    formatCount(views)?.let { it to "Views" },
                    ago?.let { it to "Uploaded" }
                )
                stats.forEachIndexed { i, (value, label) ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Tonal.hairline()))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            value,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(Tonal.hairline()))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Show less",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
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

private val PillShape = RoundedCornerShape(50)
private val PillHeight = 36.dp
private val PillIcon = 20.dp

/**
 * The one shape every chip under the video is made from: a 36dp soft pill, an icon, a label.
 * Switched on (liked, downloaded, background play) it takes a light wash of the accent colour.
 */
@Composable
private fun PillBase(
    active: Boolean,
    label: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    val dark = LocalPmDark.current
    val container = if (active) Tonal.accentFill(dark) else Tonal.fill()
    val content = if (active) Accent else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .height(PillHeight)
            .tonalPress(PillShape, container, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    description: String? = null,
    onClick: () -> Unit
) {
    val content = if (active) Accent else MaterialTheme.colorScheme.onSurface
    PillBase(active = active, label = label, onClick = onClick) {
        Icon(icon, contentDescription = description, tint = content, modifier = Modifier.size(PillIcon))
    }
}

/** Like and dislike in one joined pill, with a small hairline between them. */
@Composable
private fun ReactionPill(
    reaction: Reaction,
    likeCount: String?,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    val plain = MaterialTheme.colorScheme.onSurface
    val liked = reaction == Reaction.Liked
    val disliked = reaction == Reaction.Disliked
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(1f) }

    Row(
        Modifier
            .height(PillHeight)
            .clip(PillShape)
            .background(Tonal.fill()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clickable(role = Role.Button, onClickLabel = "Like") {
                    if (!liked) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            pop.snapTo(0.7f)
                            pop.animateTo(
                                1f,
                                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    }
                    onLike()
                }
                .padding(start = 14.dp, end = if (likeCount != null) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                contentDescription = "Like",
                tint = if (liked) Accent else plain,
                modifier = Modifier
                    .size(PillIcon)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
            )
            likeCount?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (liked) Accent else plain,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Box(Modifier.width(1.dp).height(20.dp).background(Tonal.hairline()))
        Box(
            Modifier
                .fillMaxHeight()
                .clickable(role = Role.Button, onClickLabel = "Dislike") { onDislike() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
                contentDescription = "Dislike",
                tint = if (disliked) Accent else plain,
                modifier = Modifier.size(PillIcon)
            )
        }
    }
}

/** Download: a ring with the percent while it downloads, a tick once it is saved in the app. */
@Composable
private fun DownloadChip(info: PlayableInfo?, onClick: () -> Unit) {
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
        active != null -> if (active.state == SaveState.Queued) "Waiting" else "$percent%"
        saved -> "Downloaded"
        else -> "Download"
    }
    PillBase(active = active != null || saved, label = label, onClick = onClick) {
        when {
            active != null -> CircularProgressIndicator(
                progress = { percent / 100f },
                color = Accent,
                trackColor = Accent.copy(alpha = 0.25f),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(18.dp)
            )
            saved -> Icon(
                Icons.Rounded.DownloadDone,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(PillIcon)
            )
            else -> Icon(
                Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(PillIcon)
            )
        }
    }
}

/**
 * The row under a video, in the order people reach for it: Like and dislike, Share, Download,
 * Background play, Save, Save to phone, Comments. It slides sideways; every chip is the same
 * soft 36dp pill, so nothing shouts and nothing is left out.
 */
@Composable
fun ActionRow(
    vm: AppViewModel,
    meta: WatchMeta?,
    onComments: () -> Unit,
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
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ReactionPill(
                    reaction = reaction,
                    likeCount = meta?.likeCount,
                    onLike = { vm.react(Reaction.Liked) },
                    onDislike = { vm.react(Reaction.Disliked) }
                )
            }
            item { ActionChip(Icons.Rounded.Share, "Share", onClick = onShare) }
            item {
                DownloadChip(info) {
                    if (info == null) {
                        Toast.makeText(context, "This video is still loading.", Toast.LENGTH_SHORT).show()
                    } else if (info.live) {
                        Toast.makeText(context, "Live streams can't be downloaded.", Toast.LENGTH_SHORT).show()
                    } else {
                        val busy = activeDownload(info.url) != null || OfflineStore.forUrl(info.url).isNotEmpty()
                        downloadSheet = if (busy) 2 else 1
                    }
                }
            }
            item {
                ActionChip(Icons.Rounded.Headphones, "Background play", active = background) {
                    background = !background
                    PlaybackPrefs.setBackground(context, background)
                    Toast.makeText(
                        context,
                        if (background) "Background play on" else "Background play off",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            item { ActionChip(Icons.Rounded.BookmarkBorder, "Save", onClick = onSave) }
            item { ActionChip(Icons.Rounded.SaveAlt, "Save to phone") { if (info != null) phoneOpen = true } }
            item {
                ActionChip(
                    Icons.Rounded.ChatBubbleOutline,
                    vm.commentsTotal ?: meta?.commentCount ?: "Comments",
                    description = "Comments",
                    onClick = onComments
                )
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
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = vm::loadPlaylists) { Text("Try again") }
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
