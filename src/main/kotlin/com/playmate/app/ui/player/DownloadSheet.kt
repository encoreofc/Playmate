package com.playmate.app.ui.player

import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.playmate.app.data.Ffmpeg
import com.playmate.app.data.OfflineStore
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.SaveCenter
import com.playmate.app.data.SaveState
import com.playmate.app.data.SaveTarget
import com.playmate.app.data.activeDownload
import com.playmate.app.data.estimatedBytes
import com.playmate.app.data.formatSize
import com.playmate.app.data.probeSize
import com.playmate.app.data.safeFileName
import com.playmate.app.data.saveTargetsFor
import com.playmate.app.data.sizeIsApproximate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * "Download": pick a format and quality, and the file is kept inside the app (Downloads tab),
 * playable offline. Built from the same pieces as "Save to phone" (see DownloadUi.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(info: PlayableInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // FFmpeg is checked in the background when the app starts; its formats appear once it is ready.
    val ffmpegReady = Ffmpeg.available
    val targets = remember(info, ffmpegReady) { saveTargetsFor(info) }
    val sizes = remember(info) { mutableStateMapOf<String, Long>() }
    var audioTab by remember { mutableStateOf(targets.video.isEmpty() && targets.audio.isNotEmpty()) }
    var picked by remember { mutableStateOf<SaveTarget?>(null) }
    var more by remember { mutableStateOf(false) }

    // Sizes are measured a few at a time, the ones people usually want first.
    LaunchedEffect(info.url, ffmpegReady) {
        val gate = Semaphore(6)
        val everything = targets.video + targets.audio
        val urls = (everything.filter { !it.isExtra() } + everything.filter { it.isExtra() })
            .flatMap { it.urls }
            .distinct()
            .filter { it !in sizes }
        urls.forEach { url -> launch { gate.withPermit { sizes[url] = probeSize(url) } } }
    }

    val list = if (audioTab) targets.audio else targets.video
    val recommended = remember(targets, audioTab) { recommendedFor(targets, audioTab) }
    val shown = picked?.takeIf { it in list } ?: recommended ?: list.firstOrNull()
    // The extras stay folded, unless one of them is what is chosen.
    val visible = list.filter { more || !it.isExtra() || it === shown }
    val extra = list.count { it.isExtra() }

    val durationMs = PlayerHolder.peek()?.duration?.takeIf { it > 0 }
    val durationSec = (durationMs ?: 0L) / 1000

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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = PmSheetShape,
        dragHandle = { PmDragHandle() }
    ) {
        Column(Modifier.navigationBarsPadding().pmSheetMaxHeight()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                DlHeader(
                    label = "Download",
                    title = info.title,
                    thumb = info.thumb,
                    durationMs = durationMs
                )

                if (!info.live && targets.video.isNotEmpty() && targets.audio.isNotEmpty()) {
                    PmSegmented(
                        labels = listOf("Video", "Audio"),
                        icons = listOf(Icons.Rounded.Videocam, Icons.Rounded.MusicNote),
                        selectedIndex = if (audioTab) 1 else 0,
                        onSelect = { index ->
                            audioTab = index == 1
                            picked = null
                        },
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
                    )
                }

                when {
                    info.live -> PmNote("Live streams can't be downloaded.")
                    nothing -> PmNote("No downloadable files were found for this video.")
                    list.isEmpty() -> PmNote("No ${if (audioTab) "audio" else "video"} files were found for this video.")
                    else -> {
                        if (audioTab) {
                            visible.groupBy { audioGroupTitle(it) }.forEach { (title, items) ->
                                PmSectionLabel(title)
                                items.forEach { target ->
                                    DlRow(
                                        label = target.label,
                                        note = target.rowNote(),
                                        recommended = target === recommended,
                                        size = sizeOf(target),
                                        selected = target === shown,
                                        onClick = { picked = target }
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            visible.forEach { target ->
                                DlRow(
                                    label = target.label,
                                    note = target.rowNote(),
                                    recommended = target === recommended,
                                    size = sizeOf(target),
                                    selected = target === shown,
                                    onClick = { picked = target }
                                )
                            }
                        }
                        if (extra > 0) DlMore(open = more, count = extra) { more = !more }
                        if (audioTab && !ffmpegReady) {
                            PmNote("MP3, FLAC and WAV aren't available on this phone right now.")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            PmHairline()
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 16.dp)) {
                DlWhere(
                    title = "Saved inside Playmate",
                    note = when {
                        tight -> "May not fit: needs about ${formatSize((needed ?: 0L) * 2)} free while it downloads."
                        free > 0 -> "${formatSize(free)} free"
                        else -> null
                    },
                    warning = tight
                )
                PmPrimaryButton(
                    text = if (shown != null) "Download ${shown.label}  ·  ${sizeOf(shown)}" else "Download",
                    icon = Icons.Rounded.Download,
                    enabled = shown != null && !info.live,
                    modifier = Modifier.padding(top = 14.dp),
                    onClick = {
                        val choice = shown
                        if (choice != null) {
                            SaveCenter.start(context, info, choice, safeFileName(info.title), offline = true)
                            Toast.makeText(context, "Downloading. Progress is in the Downloads tab.", Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    }
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = PmSheetShape,
        dragHandle = { PmDragHandle() }
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            PmSheetHeader(if (active != null) "Downloading" else "Download", info.title)

            if (active != null) {
                Row(
                    Modifier.padding(top = 20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    color = MaterialTheme.colorScheme.onBackground,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(3.dp)
                )
                PmOutlineButton(
                    text = "Cancel download",
                    onClick = {
                        SaveCenter.cancel(active)
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (saved.isNotEmpty()) {
                PmSectionLabel("Saved in Playmate")
            }
            saved.forEach { video ->
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PmRadius))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(PmRadius))
                        .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp)
                    )
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
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            PmPrimaryButton(
                text = "Go to Downloads",
                onClick = onGoTo,
                modifier = Modifier.padding(top = 20.dp)
            )
            PmOutlineButton(
                text = "Download another version",
                onClick = onAnother,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
