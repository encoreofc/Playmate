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
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
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
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainer)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
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
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(scheme.outlineVariant))
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
            Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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
        if (subscribed) {
            Icon(
                Icons.Rounded.NotificationsNone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp).size(22.dp)
            )
        }
        SubscribeButton(subscribed = subscribed, onClick = { vm.toggleSubscribe(channelId, subscribed) })
    }
}

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Pill {
        Row(
            Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) Accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
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
    val highlight = active != null || saved
    Pill {
        Row(
            Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                active != null -> CircularProgressIndicator(
                    progress = { percent / 100f },
                    color = Accent,
                    trackColor = MaterialTheme.colorScheme.outline,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(18.dp)
                )
                saved -> Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = Accent,
                    modifier = Modifier.size(18.dp)
                )
                else -> Icon(
                    Icons.Rounded.Download,
                    contentDescription = "Download",
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (highlight) Accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** Like / dislike, Comments, Share, Background play, Download, Save to phone, Save: the row under a video. */
@Composable
fun ActionRow(
    vm: AppViewModel,
    meta: WatchMeta?,
    onComments: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    val reaction = meta?.reaction ?: Reaction.None
    val plain = MaterialTheme.colorScheme.onSurface
    val info = (vm.playing?.info as? Load.Ready)?.data
    var phoneOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var background by remember { mutableStateOf(PlaybackPrefs.background(context)) }

    // 0 = closed, 1 = pick a quality to download, 2 = this video's download status
    var downloadSheet by remember { mutableIntStateOf(0) }

    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Pill {
                    Row(
                        Modifier
                            .fillMaxHeight()
                            .clickable { vm.react(Reaction.Liked) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (reaction == Reaction.Liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                            contentDescription = "Like",
                            tint = if (reaction == Reaction.Liked) Accent else plain,
                            modifier = Modifier.size(18.dp)
                        )
                        meta?.likeCount?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Box(Modifier.width(1.dp).height(18.dp).background(MaterialTheme.colorScheme.outline))
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .clickable { vm.react(Reaction.Disliked) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (reaction == Reaction.Disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
                            contentDescription = "Dislike",
                            tint = if (reaction == Reaction.Disliked) Accent else plain,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            item {
                ActionChip(Icons.Rounded.ChatBubbleOutline, vm.commentsTotal ?: meta?.commentCount ?: "Comments", onClick = onComments)
            }
            item { ActionChip(Icons.Rounded.Share, "Share", onClick = onShare) }
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
            item { ActionChip(Icons.Rounded.SaveAlt, "Save to phone") { if (info != null) phoneOpen = true } }
            item { ActionChip(Icons.Rounded.BookmarkBorder, "Save", onClick = onSave) }
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
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
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
                        .background(scheme.surfaceVariant)
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
                    .background(scheme.surfaceVariant)
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
