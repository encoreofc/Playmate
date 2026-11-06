package com.playmate.app.ui.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.Tab
import com.playmate.app.data.Load
import com.playmate.app.data.VideoItem
import com.playmate.app.ui.components.ErrorState
import com.playmate.app.ui.components.SignInPill
import com.playmate.app.ui.components.ThemeDialog
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.theme.glass

/**
 * The "You" page, laid out like the YouTube app: a small row of actions on top, your picture and
 * name, a History strip you can open in full, and your library. Light/dark is under the gear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(vm.signedIn) {
        if (vm.signedIn) vm.loadHistory()
    }

    if (showTheme) ThemeDialog(onDismiss = { showTheme = false })
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your recommendations and history stay with your YouTube account.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            }
        )
    }

    if (showHistory) {
        HistoryScreen(vm, onBack = { showHistory = false })
        return
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = vm.historyRefreshing,
            onRefresh = { vm.loadHistory(force = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            // Signed out, the sign-in bar floats over the bottom, so the list leaves room under it.
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (vm.signedIn) 24.dp else 80.dp)
            ) {
                item {
                    YouTopBar(
                        signedIn = vm.signedIn,
                        onAccount = { if (vm.signedIn) confirmSignOut = true else vm.openLogin() },
                        onNotifications = {
                            Toast.makeText(context, "Notifications are coming soon", Toast.LENGTH_SHORT).show()
                        },
                        onSearch = { vm.select(Tab.Search); vm.startEditing() },
                        onSettings = { showTheme = true }
                    )
                }
                item { ProfileHeader(vm) }

                if (vm.signedIn) {
                    item { SectionHeader("History", onClick = { showHistory = true }) }
                    val trackingLine = vm.trackingNote?.let { "Last watch report didn't go through: $it" }
                        ?: vm.trackingInfo
                    trackingLine?.let { line ->
                        item {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                        }
                    }
                    item { HistoryStrip(vm) }
                }

                item { SectionHeader("Library", onClick = null) }
                item {
                    LibraryRow(
                        label = "Downloads",
                        onClick = { vm.select(Tab.Downloads) }
                    )
                }
            }
        }

        // Nobody signed in: YouTube's sign-in bar at the bottom of the page, 8dp above the bottom bar.
        if (!vm.signedIn) {
            SignInPill(
                text = "Sign in to manage your library\nvideos.",
                onClick = vm::openLogin,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun YouTopBar(
    signedIn: Boolean,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pill = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .glass(pill)
                .clickable(onClick = onAccount)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(if (signedIn) "Sign out" else "Sign in", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onNotifications) {
            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "Search", modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Appearance", modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ProfileHeader(vm: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatar = vm.account?.avatar
        if (vm.signedIn && avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            Icon(
                Icons.Rounded.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(80.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = if (vm.signedIn) vm.account?.name ?: "Signed in" else "Not signed in",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val handle = vm.account?.handle
            if (vm.signedIn && handle != null) {
                Text(
                    handle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryStrip(vm: AppViewModel) {
    when (val h = vm.history) {
        is Load.Loading -> LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(3) {
                Box(
                    Modifier
                        .width(172.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        is Load.Failed -> ErrorState(h.message, onRetry = { vm.loadHistory() })
        is Load.Ready -> {
            if (h.data.isEmpty()) {
                Text(
                    "Nothing here yet. Videos you watch show up in this list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                vm.historyNote?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(h.data.take(12), key = { it.url }) { video ->
                        HistoryCard(video, onClick = { openFromList(vm, video) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(172.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = video.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            if (video.durationSec > 0 && !video.live) {
                Text(
                    formatClock(video.durationSec * 1000),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            video.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            video.channel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun LibraryRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
    }
}
