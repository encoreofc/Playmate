package com.playmate.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playmate.app.data.VideoItem
import com.playmate.app.ui.theme.Accent

/**
 * Text style for card text that may be Bengali: no letter spacing (it splits joined letters),
 * and a roomy line height with nothing trimmed, so vowel marks above and below are not cut off.
 */
private fun TextStyle.bangla(weight: FontWeight? = null) = copy(
    fontWeight = weight ?: fontWeight,
    letterSpacing = 0.sp,
    lineHeight = fontSize * 1.55f,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

@Composable
private fun Thumb(item: VideoItem, modifier: Modifier, badgePadding: Dp) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.thumb)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        val label = when {
            item.live -> "LIVE"
            item.durationSec > 0 -> formatClock(item.durationSec * 1000)
            else -> null
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(badgePadding)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (item.live) Accent else Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/** Round channel picture; the first letter of the channel when the feed has no picture. */
@Composable
private fun ChannelDot(item: VideoItem, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (item.avatar != null) {
            AsyncImage(
                model = item.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                item.channel.trim().firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.labelLarge.bangla(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The three-dot menu of a card: real actions only. */
@Composable
private fun MoreMenu(item: VideoItem, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    open = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, item.url)
                    }
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            )
            DropdownMenuItem(
                text = { Text("Copy link") },
                onClick = {
                    open = false
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("link", item.url))
                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

/** A normal video, laid out like YouTube's own feed: full-width picture, then picture + title + details. */
@Composable
fun VideoCard(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 14.dp)
    ) {
        Thumb(item, Modifier.fillMaxWidth(), badgePadding = 8.dp)
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChannelDot(item)
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall.bangla(FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.meta(),
                    style = MaterialTheme.typography.bodySmall.bangla(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MoreMenu(item)
        }
    }
}

/** YouTube's "Shorts" shelf: a heading and a sideways row of tall cards. */
@Composable
fun ShortsShelf(
    shorts: List<VideoItem>,
    onOpen: (VideoItem) -> Unit,
    onShown: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text("Shorts", style = MaterialTheme.typography.titleLarge)
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shorts, key = { it.url }) { short ->
                LaunchedEffect(short.url) { onShown(short) }
                Column(
                    Modifier
                        .width(168.dp)
                        .clickable { onOpen(short) }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(short.thumb)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        MoreMenu(short, Modifier.align(Alignment.TopEnd).padding(2.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        short.title,
                        style = MaterialTheme.typography.titleSmall.bangla(FontWeight.Medium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    formatCount(short.views)?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$it views",
                            style = MaterialTheme.typography.bodySmall.bangla(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoRow(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Thumb(item, Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)), badgePadding = 5.dp)
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                item.meta(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun shimmer(): Color {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "alpha"
    )
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val color = shimmer()
    Column(modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(color))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(color))
            }
        }
    }
}

@Composable
fun SkeletonLines(modifier: Modifier = Modifier) {
    val color = shimmer()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Box(Modifier.fillMaxWidth(0.85f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.55f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("Try again", color = MaterialTheme.colorScheme.primary)
        }
    }
}
