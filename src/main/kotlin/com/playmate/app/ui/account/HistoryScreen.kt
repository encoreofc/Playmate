package com.playmate.app.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playmate.app.AppViewModel
import com.playmate.app.data.Load
import com.playmate.app.data.VideoItem
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.VideoRow
import com.playmate.app.ui.theme.glass

/** Opens a video the right way: Shorts in the Shorts player, everything else in the normal player. */
internal fun openFromList(vm: AppViewModel, video: VideoItem) {
    if (video.isShort) vm.openShorts(listOf(video), 0) else vm.open(video)
}

private val FILTERS = listOf("All", "Videos", "Shorts")

/**
 * Everything the account watched, newest first, under its day headings (Today, Yesterday, ...)
 * like the History page of the YouTube app. The chips narrow the list to videos or Shorts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var filter by remember { mutableIntStateOf(0) }

    val all = (vm.history as? Load.Ready)?.data.orEmpty()
    val entries = remember(all, filter) {
        val shown = when (filter) {
            1 -> all.filter { !it.isShort }
            2 -> all.filter { it.isShort }
            else -> all
        }
        grouped(shown)
    }

    PullToRefreshBox(
        isRefreshing = vm.historyRefreshing,
        onRefresh = { vm.loadHistory(force = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text("History", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(FILTERS) { index, label ->
                        val selected = index == filter
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .then(
                                    if (selected) Modifier.background(MaterialTheme.colorScheme.onBackground, shape)
                                    else Modifier.glass(shape)
                                )
                                .clip(shape)
                                .clickable { filter = index }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            when (val h = vm.history) {
                is Load.Loading -> items(4) { SkeletonCard() }
                is Load.Failed -> item { ErrorState(h.message, onRetry = { vm.loadHistory() }) }
                is Load.Ready -> {
                    if (entries.isEmpty()) {
                        item {
                            Text(
                                if (all.isEmpty()) "Nothing here yet. Videos you watch show up in this list."
                                else "Nothing here for this filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                            )
                        }
                    } else {
                        items(entries, key = { if (it is VideoItem) it.url else "day:$it" }) { entry ->
                            if (entry is VideoItem) {
                                VideoRow(entry, onClick = { openFromList(vm, entry) })
                            } else {
                                Text(
                                    entry.toString(),
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp)
                                )
                            }
                        }
                    }
                    if (vm.historyToken != null) {
                        item(key = "more") {
                            LaunchedEffect(vm.historyToken) { vm.loadMoreHistory() }
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

/**
 * The videos with a day heading (a plain String) in front of each new day. A video without
 * a heading of its own stays under the day above it, which is how later pages continue a day.
 */
private fun grouped(videos: List<VideoItem>): List<Any> {
    val out = ArrayList<Any>()
    var shownDay: String? = null
    var lastDay: String? = null
    for (video in videos) {
        val day = video.section ?: lastDay
        if (day != null && day != shownDay) {
            out += day
            shownDay = day
        }
        lastDay = day
        out += video
    }
    return out
}
