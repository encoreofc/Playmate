package com.playmate.app.ui.downloads

import android.os.StatFs
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.data.Load
import com.playmate.app.data.OfflineStore
import com.playmate.app.data.OfflineVideo
import com.playmate.app.data.SaveCenter
import com.playmate.app.data.SaveJob
import com.playmate.app.data.SaveState
import com.playmate.app.data.formatSize
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.delay

/** The Downloads tab: what is downloading now, and everything saved inside the app. */
@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val running = SaveCenter.jobs.filter { it.offline && it.running }
    val failed = SaveCenter.jobs.filter { it.offline && it.state == SaveState.Failed }
    val saved = OfflineStore.items.toList()

    var tick by remember { mutableIntStateOf(0) }
    val anyRunning = running.isNotEmpty()
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            delay(400)
            tick++
        }
    }

    var deleting by remember { mutableStateOf<OfflineVideo?>(null) }
    val free = remember(saved.size) {
        runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            DownloadsHeader(count = saved.size, used = OfflineStore.totalBytes(), free = free)
        }

        if (running.isNotEmpty() || failed.isNotEmpty()) {
            item(key = "active-title") { SectionTitle("In progress") }
            items(running, key = { "job-${it.id}" }) { job ->
                ActiveRow(job, tick, onCancel = { SaveCenter.cancel(job) })
            }
            items(failed, key = { "job-${it.id}" }) { job ->
                FailedRow(
                    job,
                    onRetry = {
                        val source = job.source
                        if (source != null) {
                            SaveCenter.jobs.remove(job)
                            SaveCenter.start(context, source, job.target, job.name, offline = true)
                        }
                    },
                    onRemove = { SaveCenter.jobs.remove(job) }
                )
            }
        }

        if (saved.isEmpty() && running.isEmpty() && failed.isEmpty()) {
            item(key = "empty") { EmptyDownloads() }
        } else if (saved.isNotEmpty()) {
            item(key = "saved-title") { SectionTitle("Saved on this phone") }
            items(saved, key = { "video-${it.key}" }) { video ->
                SavedRow(
                    video,
                    onPlay = {
                        if (OfflineStore.fileOf(context, video).exists()) {
                            vm.openOffline(video)
                        } else {
                            OfflineStore.remove(context, video)
                            Toast.makeText(context, "That file is gone from your phone.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { deleting = video }
                )
            }
        }
    }

    deleting?.let { video ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete download?") },
            text = { Text("\"${video.title}\" will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    val playingLocal = ((vm.playing?.info as? Load.Ready)?.data)?.localUri
                    if (playingLocal != null && playingLocal.endsWith("/" + video.fileName)) vm.closePlayer()
                    OfflineStore.remove(context, video)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DownloadsHeader(count: Int, used: Long, free: Long) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium)
        val summary = if (count == 0) {
            "Videos you download are kept here and play without internet."
        } else {
            val noun = if (count == 1) "video" else "videos"
            val freeText = if (free > 0) "  ·  ${formatSize(free)} free" else ""
            "$count $noun  ·  ${formatSize(used)} used$freeText"
        }
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun EmptyDownloads() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.DownloadForOffline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Open a video and tap Download. It will show up here and play even without internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActiveRow(job: SaveJob, tick: Int, onCancel: () -> Unit) {
    val done = remember(tick, job.state) { job.bytes.get() }
    val total = job.total
    val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
    val status = when (job.state) {
        SaveState.Queued -> "Waiting to start"
        SaveState.Downloading ->
            if (total > 0) "$percent%  ·  ${formatSize(done)} of ${formatSize(total)}" else "Downloading…"
        SaveState.Joining -> "Joining video and audio…"
        else -> "Finishing up…"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = job.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                job.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${job.target.label}  ·  $status",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (job.state == SaveState.Downloading && total > 0) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    color = Accent,
                    trackColor = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(
                    color = Accent,
                    trackColor = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                )
            }
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel download")
        }
    }
}

@Composable
private fun FailedRow(job: SaveJob, onRetry: () -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Text(
            job.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            job.message ?: "This download didn't finish.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(Modifier.padding(top = 4.dp)) {
            if (job.source != null) TextButton(onClick = onRetry) { Text("Try again") }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun SavedRow(video: OfflineVideo, onPlay: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    OfflineRow(video, onClick = onPlay) {
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Delete download") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                    onClick = {
                        menu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/** A saved video as a row: thumbnail, title, channel and what was saved. Also used under the offline player. */
@Composable
internal fun OfflineRow(video: OfflineVideo, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OfflineThumb(video, Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (video.channel.isNotBlank()) {
                Text(
                    video.channel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Text(
                "${video.quality}  ·  ${video.format}  ·  ${formatSize(video.bytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        trailing()
    }
}

@Composable
private fun OfflineThumb(video: OfflineVideo, modifier: Modifier) {
    val context = LocalContext.current
    val thumb = remember(video.key, video.thumbName) { OfflineStore.thumbUriOf(context, video) }
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                if (video.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        }
        if (video.durationMs > 0) {
            Text(
                formatClock(video.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (video.isAudio) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = "Audio only",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(3.dp)
                    .size(14.dp)
            )
        }
    }
}
