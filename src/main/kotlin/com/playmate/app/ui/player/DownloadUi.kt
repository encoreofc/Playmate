package com.playmate.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The look of Playmate's download sheets, in one place.
 *
 * Rules: black and white only (the ink is onBackground, so it is white on the dark theme and
 * black on the light one); thin lines instead of coloured fills; one corner radius everywhere;
 * small spaced capitals for labels. The accent colour stays out of these sheets on purpose.
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

/** Two or more choices side by side in one thin frame; the chosen one is solid ink. */
@Composable
internal fun PmSegmented(
    labels: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val frame = RoundedCornerShape(PmRadius)
    Row(
        modifier
            .fillMaxWidth()
            .clip(frame)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, frame)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selectedIndex
            val tint = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(PmRadius - 3.dp))
                    .background(if (on) ink else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icons[index], contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = tint,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
