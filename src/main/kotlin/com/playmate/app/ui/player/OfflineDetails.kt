package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.playmate.app.AppViewModel
import com.playmate.app.data.OfflineStore
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.formatSize
import com.playmate.app.ui.downloads.OfflineRow
import com.playmate.app.ui.theme.Accent

/** What sits under the player while a downloaded video plays: no internet needed for any of it. */
@Composable
internal fun OfflineDetails(info: PlayableInfo, vm: AppViewModel) {
    val context = LocalContext.current
    val current = OfflineStore.items.firstOrNull { info.localUri?.endsWith("/" + it.fileName) == true }
    val others = OfflineStore.items.filter { it !== current }
    var confirm by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleLarge)
                if (info.channel.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        info.channel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            val line = listOfNotNull(
                current?.quality,
                current?.format,
                current?.bytes?.takeIf { it > 0 }?.let { formatSize(it) }
            ).joinToString("  ·  ")
            Row(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = Accent)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Playing from your downloads", style = MaterialTheme.typography.titleSmall)
                    if (line.isNotBlank()) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OfflineAction(Icons.Rounded.Public, "Watch online") { vm.openUrl(info.url) }
                if (current != null) {
                    OfflineAction(Icons.Rounded.DeleteOutline, "Delete") { confirm = true }
                }
            }
        }

        if (others.isNotEmpty()) {
            item {
                Text(
                    "More downloads",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                )
            }
            items(others, key = { "more-${it.key}" }) { video ->
                OfflineRow(video, onClick = { vm.openOffline(video) })
            }
        }
    }

    if (confirm && current != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete download?") },
            text = { Text("This video will be removed from your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.closePlayer()
                    OfflineStore.remove(context, current)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun OfflineAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
