package com.playmate.app.ui.player

import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playmate.app.data.AudioFormat
import com.playmate.app.data.DownloadPrefs
import com.playmate.app.data.Ffmpeg
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.SaveCenter
import com.playmate.app.data.SaveFolder
import com.playmate.app.data.SaveTarget
import com.playmate.app.data.estimatedBytes
import com.playmate.app.data.findTarget
import com.playmate.app.data.formatSize
import com.playmate.app.data.probeSize
import com.playmate.app.data.safeFileName
import com.playmate.app.data.saveTargetsFor
import com.playmate.app.data.sizeIsApproximate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * "Save to phone": pick video or audio, a format and quality, a name and a folder, then save it as
 * a normal file. Built from the same pieces as "Download" (see DownloadUi.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSaveSheet(info: PlayableInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // FFmpeg is checked in the background when the app starts; its formats appear once it is ready.
    val ffmpegReady = Ffmpeg.available
    val targets = remember(info, ffmpegReady) { saveTargetsFor(info) }

    var audioTab by remember {
        mutableStateOf(
            targets.audio.isNotEmpty() && (targets.video.isEmpty() || DownloadPrefs.lastWasAudio(context))
        )
    }
    var picked by remember { mutableStateOf<SaveTarget?>(null) }
    var more by remember { mutableStateOf(false) }
    var name by remember(info.url) { mutableStateOf(safeFileName(info.title)) }
    var renaming by remember { mutableStateOf(false) }
    var folderTick by remember { mutableIntStateOf(0) }
    val sizes = remember(info) { mutableStateMapOf<String, Long>() }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            SaveFolder.choose(context, uri)
            folderTick++
        }
    }

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

    // What the sheet starts on: what was saved last time (the best there is on a first visit).
    val defaultVideo = remember(targets) {
        findTarget(targets, "v|${DownloadPrefs.videoHeight(context)}|0|*")?.takeIf { !it.isAudio }
    }
    val defaultAudio = remember(targets) {
        val format = DownloadPrefs.audioFormat(context)
        val kbps = DownloadPrefs.mp3Kbps(context)
        targets.audio.firstOrNull {
            it.audioFormat == format && (format != AudioFormat.MP3 || it.kbps == kbps)
        } ?: targets.audio.firstOrNull { it.audioFormat == format }
            ?: targets.audio.firstOrNull()
    }

    val list = if (audioTab) targets.audio else targets.video
    val shown = picked?.takeIf { it in list } ?: (if (audioTab) defaultAudio else defaultVideo) ?: list.firstOrNull()
    val recommended = remember(targets, audioTab) { recommendedFor(targets, audioTab) }
    // The extras stay folded, unless one of them is what is chosen.
    val visible = list.filter { more || !it.isExtra() || it === shown }
    val extra = list.count { it.isExtra() }

    val place = remember(folderTick, audioTab) { SaveFolder.label(context, audioTab) }
    val customFolder = remember(folderTick) { SaveFolder.tree(context) != null }
    val free = remember { runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L) }
    val durationMs = remember { PlayerHolder.get(context).duration.takeIf { it > 0 } }
    val durationSec = (durationMs ?: 0L) / 1000

    /** "…" while measuring, "—" when unknown, "~" in front of a calculated size. */
    fun sizeOf(target: SaveTarget): String {
        val bytes = target.estimatedBytes(sizes, durationSec) ?: return "…"
        if (bytes <= 0) return "—"
        return (if (target.sizeIsApproximate()) "~" else "") + formatSize(bytes)
    }

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
                    label = "Save to phone",
                    title = name,
                    thumb = info.thumb,
                    durationMs = durationMs
                ) {
                    Row(
                        Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { renaming = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Rename",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                // Video or audio.
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

                when {
                    info.live -> PmNote("Live streams can't be saved.")
                    list.isEmpty() -> PmNote(
                        "No ${if (audioTab) "audio" else "video"} files were found for this video."
                    )
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
                    title = place,
                    note = if (free > 0) "${formatSize(free)} free" else null
                ) {
                    if (customFolder) {
                        DlTextAction("Default") {
                            SaveFolder.reset(context)
                            folderTick++
                        }
                    }
                    DlTextAction("Change") { pickFolder.launch(null) }
                }
                PmPrimaryButton(
                    text = if (shown != null) "Save ${shown.label}  ·  ${sizeOf(shown)}" else "Save",
                    icon = Icons.Rounded.Download,
                    enabled = shown != null && !info.live,
                    modifier = Modifier.padding(top = 14.dp),
                    onClick = {
                        val choice = shown
                        if (choice != null) {
                            // The next visit starts on what was just used.
                            DownloadPrefs.setLastWasAudio(context, choice.isAudio)
                            if (choice.isAudio) {
                                choice.audioFormat?.let { DownloadPrefs.setAudioFormat(context, it) }
                                if (choice.audioFormat == AudioFormat.MP3 && choice.kbps > 0) {
                                    DownloadPrefs.setMp3Kbps(context, choice.kbps)
                                }
                            } else {
                                DownloadPrefs.setVideoHeight(context, choice.height)
                            }
                            SaveCenter.start(context, info, choice, name)
                            Toast.makeText(
                                context,
                                "Saving to your phone. Progress is in the notification.",
                                Toast.LENGTH_LONG
                            ).show()
                            onDismiss()
                        }
                    }
                )
            }
        }
    }

    if (renaming) {
        var draft by remember { mutableStateOf(name) }
        val ink = MaterialTheme.colorScheme.onBackground
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    shape = RoundedCornerShape(PmRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ink,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = ink
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        name = safeFileName(draft)
                        renaming = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ink)
                ) { Text("Done", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { renaming = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { Text("Cancel") }
            }
        )
    }
}
