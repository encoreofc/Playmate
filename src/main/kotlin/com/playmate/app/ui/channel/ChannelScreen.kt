package com.playmate.app.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.ChannelState
import com.playmate.app.data.ChannelInfo
import com.playmate.app.data.Load
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.SkeletonCard
import com.playmate.app.ui.components.SubscribeButton
import com.playmate.app.ui.components.VideoCard

/** A channel's page: header with subscribe, the tabs YouTube offers (Home, Videos, Shorts, Live) and their videos. */
@Composable
fun ChannelScreen(vm: AppViewModel) {
    val state = vm.channel ?: return
    val info = (state.info as? Load.Ready)?.data

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::closeChannel) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                info?.name ?: state.seedName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            val loaded = state.info
            if (loaded is Load.Failed) {
                item { ErrorState(loaded.message, onRetry = vm::retryChannel) }
                return@LazyColumn
            }

            item { ChannelHeader(vm, state, info) }

            if (info != null && info.tabs.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(info.tabs) { index, tab ->
                            val selected = index == state.tab
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { vm.selectChannelTab(index) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    tab.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            when (val videos = state.videos) {
                is Load.Loading -> items(3) { SkeletonCard() }
                is Load.Failed -> item { ErrorState(videos.message, onRetry = vm::retryChannel) }
                is Load.Ready -> {
                    if (videos.data.isEmpty()) {
                        item {
                            Text(
                                "No videos here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(videos.data, key = { it.url }) { video ->
                        VideoCard(video, onClick = { vm.open(video) })
                    }
                    if (state.token != null) {
                        item(key = "more") {
                            LaunchedEffect(state.token) { vm.loadMoreChannel() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(vm: AppViewModel, state: ChannelState, info: ChannelInfo?) {
    val subscribed = vm.subscriptions[state.id] ?: info?.subscribed ?: false

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = info?.avatar ?: state.seedAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    info?.name ?: state.seedName.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val line = listOfNotNull(info?.handle, info?.subscribers, info?.videoCount).joinToString("  ·  ")
                if (line.isNotBlank()) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        info?.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (info != null) {
            SubscribeButton(
                subscribed = subscribed,
                onClick = { vm.toggleSubscribe(state.id, subscribed) },
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
