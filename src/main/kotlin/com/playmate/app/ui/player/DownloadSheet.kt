package com.playmate.app.ui.player

import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playmate.app.data.OfflineStore
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.SaveCenter
import com.playmate.app.data.SaveState
import com.playmate.app.data.SaveTarget
import com.playmate.app.data.activeDownload
import com.playmate.app.data.formatSize
import com.playmate.app.data.probeSize
import com.playmate.app.data.safeFileName
import com.playmate.app.data.saveTargetsFor
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Download": pick a quality, and the video is kept inside the app (Downloads tab), playable offline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(info: PlayableInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val targets = remember(info) { saveTargetsFor(info) }
    val sizes = remember(info) { mutableStateMapOf<String, Long>() }
    var picked by remember { mutableStateOf<SaveTarget?>(null) }

    LaunchedEffect(info.url) {
        val urls = (targets.video + targets.audio).flatMap { it.urls }.distinct()
        urls.forEach { url -> launch { sizes[url] = probeSize(url) } }
    }

    val fallback = targets.video.firstOrNull { it.height in 1..720 }
        ?: targets.video.lastOrNull()
        ?: targets.audio.firstOrNull()
    val shown = picked ?: fallback

    fun sizeOf(target: SaveTarget): String {
        val parts = target.urls.map { sizes[it] }
        if (parts.any { it == null }) return "…"
        val sum = parts.sumOf { (it ?: 0L).coerceAtLeast(0L) }
        return if (sum > 0) formatSize(sum) else "—"
    }

    val free = remember { runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L) }
    val nothing = targets.video.isEmpty() && targets.audio.isEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text("Download", style = MaterialTheme.typography.titleLarge)
                Text(
                    info.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )

                when {
                    info.live -> DlNote("Live streams can't be downloaded.")
                    nothing -> DlNote("No downloadable files were found for this video.")
                    else -> {
                        if (targets.video.isNotEmpty()) {
                            DlSection("Video")
                            targets.video.forEach { target ->
                                DlOption(target, sizeOf(target), target === shown) { picked = target }
                            }
                        }
                        if (targets.audio.isNotEmpty()) {
                            DlSection("Audio only")
                            targets.audio.forEach { target ->
                                DlOption(target, sizeOf(target), target === shown) { picked = target }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (free > 0) {
                Text(
                    "Kept inside Playmate  ·  ${formatSize(free)} free on your phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Button(
                onClick = {
                    val choice = shown
                    if (choice != null) {
                        SaveCenter.start(context, info, choice, safeFileName(info.title), offline = true)
                        Toast.makeText(context, "Downloading. Progress is in the Downloads tab.", Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                },
                enabled = shown != null && !info.live,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (shown != null) "Download ${shown.label}  ·  ${sizeOf(shown)}" else "Download",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

/**
 * Shown when the video already has a download (or one is running): progress, what is saved,
 * delete, and a way to get another quality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadStateSheet(
    info: PlayableInfo,
    onAnother: () -> Unit,
    onGoTo: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val active = activeDownload(info.url)
    val saved = OfflineStore.forUrl(info.url)
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(active) {
        while (active != null) {
            delay(500)
            tick++
        }
    }
    val percent = remember(tick, active) { active?.percent ?: 0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                info.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (active != null) {
                Text(
                    if (active.state == SaveState.Queued) "Waiting to start" else "Downloading  $percent%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    color = Accent,
                    trackColor = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        SaveCenter.cancel(active)
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text("Cancel download") }
            }

            saved.forEach { video ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = Accent)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            if (video.isAudio) "Audio  ·  ${video.quality}" else video.quality,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${video.format}  ·  ${formatSize(video.bytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        OfflineStore.remove(context, video)
                        if (OfflineStore.forUrl(info.url).isEmpty() && activeDownload(info.url) == null) onDismiss()
                    }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete download")
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                TextButton(onClick = onGoTo) { Text("Go to Downloads") }
                TextButton(onClick = onAnother) { Text("Download another version") }
            }
        }
    }
}

@Composable
private fun DlNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 18.dp)
    )
}

@Composable
private fun DlSection(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp)
    )
}

@Composable
private fun DlOption(target: SaveTarget, size: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Accent),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(target.label, style = MaterialTheme.typography.titleSmall)
            Text(
                "${target.format}  ·  $size",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
