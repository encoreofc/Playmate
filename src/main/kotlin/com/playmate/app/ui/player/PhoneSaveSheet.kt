package com.playmate.app.ui.player

import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.SaveCenter
import com.playmate.app.data.SaveFolder
import com.playmate.app.data.SaveTarget
import com.playmate.app.data.formatSize
import com.playmate.app.data.probeSize
import com.playmate.app.data.safeFileName
import com.playmate.app.data.saveTargetsFor
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.theme.Accent
import kotlinx.coroutines.launch

/** "Save to phone": pick video or audio, a quality, a name and a folder, then save it as a normal file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSaveSheet(info: PlayableInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val targets = remember(info) { saveTargetsFor(info) }

    var audioTab by remember { mutableStateOf(targets.video.isEmpty() && targets.audio.isNotEmpty()) }
    var picked by remember { mutableStateOf<SaveTarget?>(null) }
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

    LaunchedEffect(info.url) {
        val urls = (targets.video + targets.audio).flatMap { it.urls }.distinct()
        urls.forEach { url -> launch { sizes[url] = probeSize(url) } }
    }

    val list = if (audioTab) targets.audio else targets.video
    val fallback = if (audioTab) {
        list.firstOrNull()
    } else {
        list.firstOrNull { it.height in 1..720 } ?: list.lastOrNull()
    }
    val shown = picked?.takeIf { it in list } ?: fallback

    fun sizeOf(target: SaveTarget): String {
        val parts = target.urls.map { sizes[it] }
        if (parts.any { it == null }) return "…"
        val sum = parts.sumOf { (it ?: 0L).coerceAtLeast(0L) }
        return if (sum > 0) formatSize(sum) else "—"
    }

    val place = remember(folderTick, audioTab) { SaveFolder.label(context, audioTab) }
    val customFolder = remember(folderTick) { SaveFolder.tree(context) != null }
    val storage = remember { runCatching { StatFs(context.filesDir.path) }.getOrNull() }
    val free = storage?.availableBytes ?: 0L
    val capacity = storage?.totalBytes ?: 0L
    val duration = remember { PlayerHolder.get(context).duration.takeIf { it > 0 } }

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
                // The video and its file name.
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .width(132.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = info.thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        duration?.let {
                            Text(
                                formatClock(it),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { renaming = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Rename",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }

                // Where it goes.
                Column(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Accent)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                "Saving to",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                place,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { pickFolder.launch(null) }) { Text("Change") }
                    }
                    if (capacity > 0) {
                        LinearProgressIndicator(
                            progress = { ((capacity - free).toFloat() / capacity).coerceIn(0f, 1f) },
                            color = Accent,
                            trackColor = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                        )
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${formatSize(free)} free of ${formatSize(capacity)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (customFolder) {
                                Text(
                                    "Use default",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            SaveFolder.reset(context)
                                            folderTick++
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Video or audio.
                Row(
                    Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    Segment("Video", Icons.Rounded.Videocam, !audioTab, Modifier.weight(1f)) {
                        audioTab = false
                        picked = null
                    }
                    Segment("Audio", Icons.Rounded.MusicNote, audioTab, Modifier.weight(1f)) {
                        audioTab = true
                        picked = null
                    }
                }

                when {
                    info.live -> Note("Live streams can't be saved.")
                    list.isEmpty() -> Note(
                        "No ${if (audioTab) "audio" else "video"} files were found for this video."
                    )
                    else -> list.chunked(2).forEach { pair ->
                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { target ->
                                OptionCard(
                                    modifier = Modifier.weight(1f),
                                    target = target,
                                    size = sizeOf(target),
                                    selected = target === shown,
                                    onClick = { picked = target }
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val choice = shown
                    if (choice != null) {
                        SaveCenter.start(context, info, choice, name)
                        Toast.makeText(
                            context,
                            "Saving to your phone. Progress is in the notification.",
                            Toast.LENGTH_LONG
                        ).show()
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
                    if (shown != null) "Save ${shown.label}  ·  ${sizeOf(shown)}" else "Save",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }

    if (renaming) {
        var draft by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    name = safeFileName(draft)
                    renaming = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 18.dp)
    )
}

@Composable
private fun Segment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun OptionCard(
    modifier: Modifier,
    target: SaveTarget,
    size: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, if (selected) Accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                target.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            "${target.format}  ·  $size",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
