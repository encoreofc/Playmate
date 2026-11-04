package com.playmate.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
    val hasMore = vm.feedToken != null
    val entries = remember(vm.feed, hasMore) {
        (vm.feed as? Load.Ready)?.data?.let { buildEntries(it, hasMore) }.orEmpty()
    }
    PullToRefreshBox(
        isRefreshing = vm.refreshing,
        onRefresh = { vm.loadFeed(force = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("Play")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("mate") }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.select(Tab.Search); vm.startEditing() }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                    if (vm.signedIn) {
                        AsyncImage(
                            model = vm.account?.avatar,
                            contentDescription = "Your account",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { vm.select(Tab.You) }
                        )
                    } else {
                        TextButton(onClick = vm::openLogin) { Text("Sign in") }
                    }
                }
            }

            if (vm.feedChips.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(vm.feedChips) { index, chip ->
                            val selected = index == vm.selectedChip
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.onBackground
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { vm.selectChip(index) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    chip.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }

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
                                onOpen = { vm.open(it) },
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
