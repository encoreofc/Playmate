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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import com.playmate.app.data.AudioFormat
import com.playmate.app.data.Ffmpeg
import com.playmate.app.data.OfflineStore
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.SaveCenter
import com.playmate.app.data.SaveState
import com.playmate.app.data.SaveTarget
import com.playmate.app.data.activeDownload
import com.playmate.app.data.estimatedBytes
import com.playmate.app.data.findTarget
import com.playmate.app.data.formatSize
import com.playmate.app.data.probeSize
import com.playmate.app.data.safeFileName
import com.playmate.app.data.saveTargetsFor
import com.playmate.app.data.sizeIsApproximate
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Options that are rarely what a person wants to keep inside the app: extra codecs, and the very big audio files. */
private fun isHeavy(target: SaveTarget): Boolean =
    !target.primary || target.audioFormat == AudioFormat.FLAC || target.audioFormat == AudioFormat.WAV

/** The heading an audio option sits under. */
private fun dlAudioGroup(target: SaveTarget): String = when (target.audioFormat) {
    AudioFormat.MP3 -> "Audio  ·  MP3"
    AudioFormat.M4A, AudioFormat.OPUS -> "Audio  ·  original sound"
    else -> "Audio  ·  lossless"
}

/** "Download": pick a format and quality, and the file is kept inside the app (Downloads tab), playable offline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(info: PlayableInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // FFmpeg is checked in the background when the app starts; its formats appear once it is ready.
    val ffmpegReady = Ffmpeg.available
    val targets = remember(info, ffmpegReady) { saveTargetsFor(info) }
    val sizes = remember(info) { mutableStateMapOf<String, Long>() }
    var picked by remember { mutableStateOf<SaveTarget?>(null) }
    var more by remember { mutableStateOf(false) }

    // Sizes are measured a few at a time, the ones on screen first.
    LaunchedEffect(info.url, ffmpegReady) {
        val gate = Semaphore(6)
        val everything = targets.video + targets.audio
        val urls = (everything.filter { !isHeavy(it) } + everything.filter { isHeavy(it) })
            .flatMap { it.urls }
            .distinct()
            .filter { it !in sizes }
        urls.forEach { url -> launch { gate.withPermit { sizes[url] = probeSize(url) } } }
    }

    val everything = targets.video + targets.audio
    val videoList = if (more) targets.video else targets.video.filter { !isHeavy(it) }
    val audioList = if (more) targets.audio else targets.audio.filter { !heavyAudio(it) }
    val extra = everything.count { isHeavy(it) }

    // Inside the app the first choice is the best video up to 720p, which keeps files modest; one tap picks more.
    val fallback = remember(targets) {
        findTarget(targets, "v|720|0|*")?.takeIf { !it.isAudio } ?: targets.audio.firstOrNull()
    }
    val shown = picked?.takeIf { it in everything } ?: fallback

    val durationSec = (PlayerHolder.peek()?.duration?.takeIf { it > 0 } ?: 0L) / 1000

    /** "…" while measuring, "—" when unknown, "~" in front of a calculated size. */
    fun sizeOf(target: SaveTarget): String {
        val bytes = target.estimatedBytes(sizes, durationSec) ?: return "…"
        if (bytes <= 0) return "—"
        return (if (target.sizeIsApproximate()) "~" else "") + formatSize(bytes)
    }

    val free = remember { runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L) }
    val nothing = targets.video.isEmpty() && targets.audio.isEmpty()

    // A download needs room for the pieces and the finished file at the same time.
    val needed = shown?.estimatedBytes(sizes, durationSec)?.takeIf { it > 0 }
    val tight = needed != null && free > 0 && needed * 2 + 64L * 1024 * 1024 > free

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
                        if (videoList.isNotEmpty()) {
                            DlSection("Video")
                            videoList.forEach { target ->
                                DlOption(target, sizeOf(target), target === shown) { picked = target }
                            }
                        }
                        audioList.groupBy { dlAudioGroup(it) }.forEach { (title, items) ->
                            DlSection(title)
                            items.forEach { target ->
                                DlOption(target, sizeOf(target), target === shown) { picked = target }
                            }
                        }
                        if (extra > 0) {
                            Row(
                                Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        more = !more
                                        // Folding the extras away must not leave one of them quietly selected.
                                        if (!more) picked = picked?.takeIf { !isHeavy(it) }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (more) "Fewer formats" else "More formats  ·  $extra",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (!ffmpegReady) {
                            DlNote("MP3, FLAC and WAV aren't available on this phone right now.")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (tight) {
                Text(
                    "This may not fit: it needs about ${formatSize((needed ?: 0L) * 2)} of free space while it downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            } else if (free > 0) {
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

/** Audio rows that stay folded away by default: only the very big files. (Video extras are the other codecs.) */
private fun heavyAudio(target: SaveTarget): Boolean =
    target.audioFormat == AudioFormat.FLAC || target.audioFormat == AudioFormat.WAV

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
    val stateText = remember(tick, active) {
        when (active?.state) {
            SaveState.Queued -> "Waiting to start"
            SaveState.Joining -> "Getting the file ready…"
            SaveState.Saving -> "Saving…"
            else -> "Downloading  ${active?.percent ?: 0}%"
        }
    }

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
                    stateText,
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
                "${target.detail.ifBlank { target.format }}  ·  $size",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
