package com.playmate.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import coil.compose.AsyncImage
import com.playmate.app.data.AudioFormat
import com.playmate.app.data.SaveTarget
import com.playmate.app.data.SaveTargets
import com.playmate.app.data.findTarget
import com.playmate.app.ui.components.formatClock
import com.playmate.app.ui.theme.LocalPmDark
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The look of Playmate's download sheets, in one place.
 *
 * Rules: black and white only (the ink is onBackground, so it is white on the dark theme and
 * black on the light one); no boxes inside boxes: options are flat rows and only the chosen one
 * is lifted with a faint fill; one weight for the title, one quiet grey for everything else;
 * small spaced capitals for labels. The accent colour stays out of these sheets on purpose.
 * Both save sheets ("Download" and "Save to phone") are built from the same pieces below, so
 * they read as one design.
 */

/** The one corner radius of every control in the download sheets. */
internal val PmRadius = 10.dp

/** Top corners of the sheets themselves. */
internal val PmSheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

/** The thin grab bar at the top of a sheet. */
@Composable
internal fun PmDragHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .width(32.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline)
    )
}

/** A thin dividing line. */
@Composable
internal fun PmHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Small spaced capitals, used for the name of a sheet and for each group inside it. */
@Composable
internal fun PmCaps(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** The top of a sheet: what this is, and which video it is about. */
@Composable
internal fun PmSheetHeader(label: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        PmCaps(label)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** A group heading inside a list of options. */
@Composable
internal fun PmSectionLabel(text: String) {
    PmCaps(text, Modifier.padding(top = 22.dp, bottom = 2.dp))
}

/** A plain line of explanation. */
@Composable
internal fun PmNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 18.dp)
    )
}

/** The round tick that marks the chosen option: filled ink when chosen, an empty ring when not. */
@Composable
internal fun PmCheck(selected: Boolean) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(ink)
                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** The frame of one option: a thin line, and a stronger line with a faint fill when it is the chosen one. */
@Composable
internal fun Modifier.pmChoice(selected: Boolean, onClick: () -> Unit): Modifier {
    val shape = RoundedCornerShape(PmRadius)
    val ink = MaterialTheme.colorScheme.onBackground
    return this
        .clip(shape)
        .background(if (selected) ink.copy(alpha = 0.07f) else Color.Transparent, shape)
        .border(
            if (selected) 1.5.dp else 1.dp,
            if (selected) ink else MaterialTheme.colorScheme.outlineVariant,
            shape
        )
        .clickable(onClick = onClick)
}

/** The main action of a sheet: solid ink with background-coloured text. */
@Composable
internal fun PmPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(PmRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = ink,
            contentColor = MaterialTheme.colorScheme.background,
            disabledContainerColor = ink.copy(alpha = 0.12f),
            disabledContentColor = ink.copy(alpha = 0.38f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The second action: a thin outline, ink-coloured text. */
@Composable
internal fun PmOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(PmRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A small outlined button that sits inside a row ("Change"). */
@Composable
internal fun PmSmallButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Two or more choices side by side on one soft track; the chosen one is lifted out of it. */
@Composable
internal fun PmSegmented(
    labels: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val dark = LocalPmDark.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ink.copy(alpha = 0.07f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selectedIndex
            val tint = if (on) ink else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        when {
                            !on -> Color.Transparent
                            dark -> ink.copy(alpha = 0.16f)
                            else -> Color.White
                        }
                    )
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icons[index], contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = tint,
                    modifier = Modifier.padding(start = 7.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ the pieces both save sheets share

/**
 * Keeps a sheet below the status bar. A sheet that may fill the whole screen slides under the clock
 * and the battery icons; this stops it a little short of the top.
 */
@Composable
internal fun Modifier.pmSheetMaxHeight(): Modifier =
    this.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.88f).dp)

/** The top of a save sheet: the picture with its length, what the sheet does, and the title on two lines. */
@Composable
internal fun DlHeader(
    label: String,
    title: String,
    thumb: String?,
    durationMs: Long?,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .width(116.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (durationMs != null && durationMs > 0) {
                Text(
                    formatClock(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            PmCaps(label)
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            below()
        }
    }
}

/**
 * One quality or format: its name, what the person needs to know about it (rarely anything),
 * its size, and a tick. No frame; the chosen row is only lifted with a faint fill.
 */
@Composable
internal fun DlRow(
    label: String,
    note: String?,
    recommended: Boolean,
    size: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        Modifier
            .padding(top = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ink.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                )
                if (recommended) {
                    Text(
                        "Recommended",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ink.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        Text(
            size,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 14.dp)
        )
        PmCheck(selected)
    }
}

/** "More formats · 6" / "Fewer formats": the quiet fold at the end of a list. */
@Composable
internal fun DlMore(open: Boolean, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (open) "Fewer formats" else "More formats  ·  $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Where the file goes and how much room is left: one line of text, not a card. */
@Composable
internal fun DlWhere(
    title: String,
    note: String?,
    warning: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        actions()
    }
}

/** A plain text action at the end of a line ("Change"). */
@Composable
internal fun DlTextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

// ------------------------------------------------------------------ what to show, and in which order

/** Options folded under "More formats": the other video codecs and the very big audio files. */
internal fun SaveTarget.isExtra(): Boolean =
    !primary || audioFormat == AudioFormat.FLAC || audioFormat == AudioFormat.WAV

/** The one thing worth saying under a name. Codecs and containers are noise unless they are the unusual ones. */
internal fun SaveTarget.rowNote(): String? = when {
    !isAudio -> if (primary) null else detail
    key.endsWith("|low") -> "Smaller file"
    else -> null
}

/** The heading an audio option sits under. */
internal fun audioGroupTitle(target: SaveTarget): String = when (target.audioFormat) {
    AudioFormat.MP3 -> "MP3  ·  plays everywhere"
    AudioFormat.M4A, AudioFormat.OPUS -> "Original  ·  no quality loss"
    else -> "Lossless  ·  large files"
}

/** The pick that suits most people: video up to 720p (a modest file), or the original audio untouched. */
internal fun recommendedFor(targets: SaveTargets, audio: Boolean): SaveTarget? =
    if (audio) {
        targets.audio.firstOrNull { it.audioFormat == AudioFormat.M4A } ?: targets.audio.firstOrNull()
    } else {
        findTarget(targets, "v|720|0|*")?.takeIf { !it.isAudio } ?: targets.video.firstOrNull()
    }
