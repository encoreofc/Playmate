package com.playmate.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.playmate.app.ui.theme.LocalPmDark
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.playmate.app.data.VideoItem
import com.playmate.app.ui.components.ShortsShelf
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.HomeTopBar
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.VideoCard

private sealed interface Entry {
    val key: String
}

private class LongEntry(val video: VideoItem) : Entry {
    override val key get() = video.url
}

private class ShelfEntry(override val key: String, val shorts: List<VideoItem>) : Entry

private const val SHELF_SIZE = 10

/**
 * Splits the feed the way YouTube does: normal videos as cards, and the short videos
 * together in "Shorts" shelves between them (the first after the third video, then one
 * after every eight more).
 */
private fun buildEntries(items: List<VideoItem>, hasMore: Boolean): List<Entry> {
    val longs = items.filter { !it.isShort }
    val shorts = items.filter { it.isShort }
    val out = ArrayList<Entry>()
    var next = 0
    var shelf = 0
    fun addShelf() {
        if (next >= shorts.size) return
        val chunk = shorts.subList(next, minOf(next + SHELF_SIZE, shorts.size))
        out += ShelfEntry("shorts-$shelf", chunk)
        next += chunk.size
        shelf++
    }
    longs.forEachIndexed { i, video ->
        out += LongEntry(video)
        if (i == 2 || (i > 2 && (i - 2) % 8 == 0)) addShelf()
    }
    if (longs.size <= 2) addShelf()
    if (!hasMore) while (next < shorts.size) addShelf()
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val hasMore = vm.feedToken != null
    // Chip colours measured from YouTube's own screenshots, one set per theme.
    val dark = LocalPmDark.current
    val chipIdle = if (dark) Color(0xFF3D3E40) else Color(0xFFEBECEE)
    val chipIdleText = if (dark) MaterialTheme.colorScheme.onBackground else Color(0xFF595959)
    val chipSelected = if (dark) MaterialTheme.colorScheme.onBackground else Color(0xFF606060)
    val chipSelectedText = if (dark) MaterialTheme.colorScheme.background else Color.White
    val entries = remember(vm.feed, hasMore) {
        (vm.feed as? Load.Ready)?.data?.let { buildEntries(it, hasMore) }.orEmpty()
    }
    // The logo bar and the chip row sit OUTSIDE the scrolling list, so they stay pinned at the top like YouTube.
    Column(Modifier.fillMaxSize()) {
        HomeTopBar(
            onNotifications = {
                Toast.makeText(context, "Notifications are coming soon", Toast.LENGTH_SHORT).show()
            },
            onSearch = { vm.select(Tab.Search); vm.startEditing() }
        )

        // The chip row, as on YouTube: the topic chips (24dp tall, 5dp corners).
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(vm.feedChips) { index, chip ->
                val selected = index == vm.selectedChip
                Box(
                    Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (selected) chipSelected else chipIdle)
                        .clickable { vm.selectChip(index) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        chip.label,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        color = if (selected) chipSelectedText else chipIdleText
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.loadFeed(force = true) },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            vm.feedNote?.let { note ->
                item {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            when (val feed = vm.feed) {
                is Load.Loading -> items(4) { SkeletonCard() }
                is Load.Failed -> item { ErrorState(feed.message, onRetry = { vm.loadFeed(force = true) }) }
                is Load.Ready -> {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is LongEntry -> {
                                LaunchedEffect(entry.video.url) { vm.onImpression(entry.video.url) }
                                VideoCard(entry.video, onClick = { vm.open(entry.video) })
                            }
                            is ShelfEntry -> ShortsShelf(
                                shorts = entry.shorts,
                                onOpen = { vm.openShorts(entry.shorts, entry.shorts.indexOf(it)) },
                                onShown = { vm.onImpression(it.url) }
                            )
                        }
                    }
                    if (vm.feedToken != null) {
                        item(key = "more") {
                            LaunchedEffect(vm.feedToken) { vm.loadMoreFeed() }
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
