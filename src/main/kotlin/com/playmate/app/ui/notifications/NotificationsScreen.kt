package com.playmate.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playmate.app.AppViewModel
import com.playmate.app.data.Load
import com.playmate.app.data.NotifKind
import com.playmate.app.data.NotificationItem
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.PmIcons
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.flow.distinctUntilChanged

/** Keeps Bengali joined and uncut: no letter spacing, and a roomy line with nothing trimmed. */
private fun TextStyle.bangla() = copy(
    letterSpacing = 0.sp,
    lineHeight = fontSize * 1.5f,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

private val Filters = listOf("All", "Comments", "Mentions")

/** One line of the list: a day heading, or a notification. */
private sealed interface Entry {
    data class Heading(val title: String) : Entry
    data class Row(val item: NotificationItem) : Entry
}

@Composable
fun NotificationsScreen(vm: AppViewModel) {
    var filter by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::closeNotifications) {
                Icon(PmIcons.Back, contentDescription = "Back", modifier = Modifier.size(24.dp))
            }
            Text("Notifications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }

        if (!vm.signedIn) {
            SignInState(onSignIn = vm::openLogin)
            return@Column
        }

        FilterRow(selected = filter, onSelect = { filter = it })

        when (val state = vm.notifications) {
            is Load.Loading -> SkeletonList()
            is Load.Failed -> ErrorState(state.message, onRetry = { vm.loadNotifications(force = true) })
            is Load.Ready -> {
                val shown = state.data.filter {
                    when (filter) {
                        1 -> it.kind == NotifKind.Comment
                        2 -> it.kind == NotifKind.Mention
                        else -> true
                    }
                }
                if (shown.isEmpty()) {
                    EmptyState(
                        icon = when (filter) {
                            1 -> PmIcons.Comment
                            2 -> PmIcons.At
                            else -> PmIcons.Bell
                        },
                        title = when (filter) {
                            1 -> "No comment notifications"
                            2 -> "No mentions yet"
                            else -> "No notifications yet"
                        }
                    )
                } else {
                    NotificationList(vm, shown)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Filters.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun NotificationList(vm: AppViewModel, items: List<NotificationItem>) {
    val entries = remember(items) {
        buildList<Entry> {
            var last: String? = null
            items.forEach { n ->
                val group = groupOf(n.ageHours)
                if (group != last) {
                    add(Entry.Heading(group))
                    last = group
                }
                add(Entry.Row(n))
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, entries.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= entries.size - 4) vm.loadMoreNotifications() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(entries, key = { i, e -> if (e is Entry.Row) e.item.id else "h$i" }) { _, entry ->
            when (entry) {
                is Entry.Heading -> Text(
                    entry.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
                )
                is Entry.Row -> NotificationRow(entry.item, onClick = { openItem(vm, entry.item) })
            }
        }
        if (vm.loadingMoreNotifications) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun openItem(vm: AppViewModel, item: NotificationItem) {
    when {
        item.videoUrl != null -> vm.openUrl(item.videoUrl)
        item.channelId != null -> {
            vm.closeNotifications()
            vm.openChannel(item.channelId)
        }
    }
}

private fun groupOf(hours: Long): String = when {
    hours < 24 -> "Today"
    hours < 24 * 7 -> "This week"
    hours < 24 * 30 -> "This month"
    else -> "Earlier"
}

@Composable
private fun NotificationRow(item: NotificationItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // A small dot marks what has not been opened yet.
        Box(Modifier.width(12.dp).height(44.dp), contentAlignment = Alignment.Center) {
            if (item.unread) Box(Modifier.size(7.dp).clip(CircleShape).background(Accent))
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (item.avatar != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(item.avatar).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    PmIcons.Bell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.message,
                style = MaterialTheme.typography.bodyMedium.bangla(),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (item.time.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    item.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.thumb != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.thumb).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 104.dp, height = 58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun SkeletonList() {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                }
                Box(Modifier.size(width = 104.dp, height = 58.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String) {
    Column(
        Modifier.fillMaxSize().padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SignInState(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            PmIcons.Bell,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Sign in to see your notifications", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface)
                .clickable(onClick = onSignIn)
                .padding(horizontal = 28.dp, vertical = 11.dp)
        ) {
            Text("Sign in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.background)
        }
    }
}
