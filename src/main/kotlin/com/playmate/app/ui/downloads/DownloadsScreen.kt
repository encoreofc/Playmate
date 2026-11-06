package com.playmate.app.ui.downloads

import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.playmate.app.ui.theme.LocalPmDark
import kotlinx.coroutines.delay

/** Which downloads the list shows. */
private enum class DlFilter(val label: String) {
    All("All"),
    Videos("Videos"),
    Audio("Audio")
}

/** The Downloads tab, laid out like YouTube's: a plain top bar, "Your downloads" with a filter, then the list. */
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
    var filter by remember { mutableStateOf(DlFilter.All) }
    var filterOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val free = remember(saved.size) {
        runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
    }

    val shown = saved.filter { video ->
        val kindOk = when (filter) {
            DlFilter.All -> true
            DlFilter.Videos -> !video.isAudio
            DlFilter.Audio -> video.isAudio
        }
        val text = query.trim()
        kindOk && (text.isEmpty() ||
            video.title.contains(text, ignoreCase = true) ||
            video.channel.contains(text, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize()) {
        DownloadsTopBar(
            searching = searching,
            query = query,
            onQuery = { query = it },
            onSearch = {
                if (searching) query = ""
                searching = !searching
            },
            onSettings = {
                Toast.makeText(context, "Download settings are coming soon", Toast.LENGTH_SHORT).show()
            },
            summary = if (saved.isEmpty()) {
                "No downloads yet"
            } else {
                val noun = if (saved.size == 1) "video" else "videos"
                val freeText = if (free > 0) "  ·  ${formatSize(free)} free" else ""
                "${saved.size} $noun  ·  ${formatSize(OfflineStore.totalBytes())} used$freeText"
            }
        )

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "title") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your downloads",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { filterOpen = true }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Filter", modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (running.isNotEmpty() || failed.isNotEmpty()) {
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
            } else if (saved.isNotEmpty() && shown.isEmpty()) {
                item(key = "nothing") {
                    Text(
                        "Nothing matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
            } else {
                items(shown, key = { "video-${it.key}" }) { video ->
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
    }

    if (filterOpen) {
        FilterSheet(
            current = filter,
            onPick = {
                filter = it
                filterOpen = false
            },
            onDismiss = { filterOpen = false }
        )
    }

    deleting?.let { video ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
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
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { Text("Cancel") }
            }
        )
    }
}

/** "Downloads" on the left; search, settings and more on the right. Search turns the title into a text field. */
@Composable
private fun DownloadsTopBar(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    summary: String
) {
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    val ink = MaterialTheme.colorScheme.onBackground

    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searching) {
            Box(Modifier.weight(1f).padding(start = 16.dp)) {
                if (query.isEmpty()) {
                    Text(
                        "Search downloads",
                        style = TextStyle(fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 18.sp, color = ink),
                    cursorBrush = SolidColor(ink),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
        } else {
            Text(
                "Downloads",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp
                ),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }
        BarIcon(if (searching) Icons.Rounded.Close else Icons.Outlined.Search, "Search", 56.dp, onSearch)
        BarIcon(Icons.Outlined.Settings, "Settings", 40.dp, onSettings)
        Box {
            BarIcon(Icons.Rounded.MoreVert, "More", 44.dp) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(summary, style = MaterialTheme.typography.bodyMedium) },
                    enabled = false,
                    onClick = { menu = false }
                )
            }
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, width: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .width(width)
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp))
    }
}

/** All / Videos / Audio, as one small card floating above the list, like YouTube's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(current: DlFilter, onPick: (DlFilter) -> Unit, onDismiss: () -> Unit) {
    val dark = LocalPmDark.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        shape = RectangleShape,
        dragHandle = {}
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (dark) Color(0xFF212121) else Color(0xFFF2F2F2))
                .padding(bottom = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (dark) Color(0xFF4D4D4D) else Color(0xFFCCCCCC))
            )
            DlFilter.values().forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onPick(option) }
                        .padding(start = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Radio(option == current)
                    Text(
                        option.label,
                        style = TextStyle(fontSize = 16.sp),
                        modifier = Modifier.padding(start = 38.dp)
                    )
                }
            }
        }
    }
}

/** A plain ring, with a solid dot inside when chosen. */
@Composable
private fun Radio(selected: Boolean) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .size(20.dp)
            .border(2.dp, ink, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ink)
            )
        }
    }
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
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(188.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
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
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${job.target.label}  ·  $status",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 14.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (job.state == SaveState.Downloading && total > 0) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    color = MaterialTheme.colorScheme.onBackground,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp)
                )
            } else {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.onBackground,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp)
                )
            }
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel download", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FailedRow(job: SaveJob, onRetry: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(14.dp)
    ) {
        Text(
            job.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
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
            val ink = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
            if (job.source != null) TextButton(onClick = onRetry, colors = ink) { Text("Try again") }
            TextButton(onClick = onRemove, colors = ink) { Text("Remove") }
        }
    }
}

@Composable
private fun SavedRow(video: OfflineVideo, onPlay: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    OfflineRow(video, onClick = onPlay) {
        // Sits level with the first line of the title, like YouTube's.
        Box(Modifier.offset(y = (-7).dp)) {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp))
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

/**
 * A saved video as a row, laid out like YouTube's: a big rounded thumbnail on the left; on the right the
 * title (and the menu), then the channel and what was saved. Also used under the offline player.
 */
@Composable
internal fun OfflineRow(video: OfflineVideo, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val meta = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 14.5.sp)
    val detail = "${video.quality}  ·  ${video.format}  ·  ${formatSize(video.bytes)}"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 0.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OfflineThumb(video, Modifier.width(188.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(top = 1.dp)
                )
                trailing()
            }
            Row(Modifier.padding(top = 5.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DownloadForOffline,
                    contentDescription = null,
                    tint = grey,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    if (video.channel.isNotBlank()) video.channel else detail,
                    style = meta,
                    color = grey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
            if (video.channel.isNotBlank()) {
                Text(
                    detail,
                    style = meta,
                    color = grey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
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
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        if (video.isAudio) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = "Audio only",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(3.dp)
                    .size(14.dp)
            )
        }
    }
}
