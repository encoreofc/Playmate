package com.playmate.app.ui.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.SubtitleTrack

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Daily limits offered by the Shorts timer, in minutes; 0 is "off". */
private val LIMITS = listOf(0, 15, 30, 45, 60, 90)

/**
 * The "more" sheet of the Shorts screen: playback speed, picture quality and captions of the Short on
 * screen, clear screen, saving to a playlist, the two ways to keep it (inside the app, or in the phone's
 * own storage), "Not interested" feedback and the daily Shorts timer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsMoreSheet(
    info: PlayableInfo?,
    selectedQuality: Int,
    speed: Float,
    onQuality: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onDownload: () -> Unit,
    onSaveToPhone: () -> Unit,
    onDismiss: () -> Unit,
    captionLang: String? = null,
    onCaption: (String?) -> Unit = {},
    onClearScreen: () -> Unit = {},
    onSaveToPlaylist: () -> Unit = {},
    onNotInterested: () -> Unit = {},
    onDontRecommend: () -> Unit = {},
    limitMin: Int = 0,
    usedMinutes: Int = 0,
    onLimit: (Int) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            SectionLabel("Playback speed")
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SPEEDS.forEach { value ->
                    SpeedChip(speedLabel(value), selected = value == speed) { onSpeed(value) }
                }
            }

            SectionLabel("Quality")
            val qualities = info?.qualities.orEmpty()
            if (qualities.isEmpty()) {
                Text(
                    if (info == null) "Loading…" else "Quality can't be changed for this Short.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            } else {
                qualities.forEachIndexed { index, quality ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onQuality(index) }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            quality.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (index == selectedQuality) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            SectionLabel("Captions")
            val tracks = info?.subtitles.orEmpty()
            if (tracks.isEmpty()) {
                Text(
                    if (info == null) "Loading…" else "No captions for this Short.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            } else {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedChip("Off", selected = captionLang == null) { onCaption(null) }
                    tracks.distinctBy { it.language }.forEach { track ->
                        SpeedChip(captionLabel(track, tracks), selected = captionLang == track.language) {
                            onCaption(track.language)
                        }
                    }
                }
            }

            SectionLabel("View")
            ActionRow(
                icon = Icons.Rounded.VisibilityOff,
                title = "Clear screen",
                subtitle = "Hide every icon and line of text. Tap the screen to bring them back",
                enabled = true,
                onClick = onClearScreen
            )
            ActionRow(
                icon = Icons.Rounded.Add,
                title = "Save to playlist",
                subtitle = "Watch later or one of your playlists",
                enabled = info != null,
                onClick = onSaveToPlaylist
            )

            SectionLabel("Keep this Short")
            val canKeep = info != null && !info.live
            ActionRow(
                icon = Icons.Rounded.Download,
                title = "Download",
                subtitle = "Kept inside the app, plays without internet",
                enabled = canKeep,
                onClick = onDownload
            )
            ActionRow(
                icon = Icons.Rounded.SaveAlt,
                title = "Save to phone",
                subtitle = "A file in the phone's own storage",
                enabled = canKeep,
                onClick = onSaveToPhone
            )

            SectionLabel("Your feed")
            ActionRow(
                icon = Icons.Rounded.NotInterested,
                title = "Not interested",
                subtitle = "Show fewer Shorts like this",
                enabled = true,
                onClick = onNotInterested
            )
            ActionRow(
                icon = Icons.Rounded.Block,
                title = "Don't recommend this channel",
                subtitle = "Show fewer Shorts from this channel",
                enabled = true,
                onClick = onDontRecommend
            )

            SectionLabel("Shorts timer")
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LIMITS.forEach { minutes ->
                    SpeedChip(if (minutes == 0) "Off" else "$minutes min", selected = minutes == limitMin) {
                        onLimit(minutes)
                    }
                }
            }
            Text(
                if (limitMin > 0) "Daily limit. Watched today: $usedMinutes min" else "A reminder to take a break once you reach a daily limit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

/** The language name, with "(auto-generated)" when every track in that language is made by YouTube. */
private fun captionLabel(track: SubtitleTrack, all: List<SubtitleTrack>): String {
    val sameLanguage = all.filter { it.language == track.language }
    val autoOnly = sameLanguage.all { it.auto }
    return if (autoOnly) "${track.label} (auto)" else track.label
}

private fun speedLabel(value: Float): String =
    if (value == value.toInt().toFloat()) "${value.toInt()}x" else "${value}x"

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SpeedChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
