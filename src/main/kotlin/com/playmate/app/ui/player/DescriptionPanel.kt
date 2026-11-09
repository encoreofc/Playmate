package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.playmate.app.AppViewModel
import com.playmate.app.data.PlayableInfo
import com.playmate.app.ui.components.formatCount
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Linkish = Regex("(https?://\\S+)|(#[\\p{L}\\p{N}_]+)")

/** Hashtags and links in the description are blue, like on YouTube. */
private fun styledDescription(text: String, link: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in Linkish.findAll(text)) {
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(color = link)) { append(match.value) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

/** "2026-01-26T…" becomes "Jan 26" over "2026"; text that is not a date is shown as it is. */
private fun uploadedParts(raw: String?): Pair<String, String>? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val date = runCatching { OffsetDateTime.parse(text).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
        ?: return text to "Uploaded"
    return date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) to date.year.toString()
}

/**
 * The description, opened under the player like on YouTube: the title with a close button, the
 * channel, three figures (likes, views, date) and the text in a grey box with "See more".
 */
@Composable
fun DescriptionPanel(
    vm: AppViewModel,
    info: PlayableInfo?,
    title: String,
    seedChannel: String,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val meta = vm.meta
    val channelId = meta?.channelId ?: info?.channelId
    val channelName = meta?.channelName ?: info?.channel?.takeIf { it.isNotBlank() } ?: seedChannel
    val avatar = meta?.channelAvatar ?: info?.channelAvatar
    val description = info?.description.orEmpty()
    val link = ytLinkColor()
    val boxColor = ytGrey()

    var expanded by remember { mutableStateOf(false) }
    var overflowing by remember { mutableStateOf(false) }

    PanelFrame(onDismiss) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
                    modifier = Modifier.weight(1f).padding(top = 6.dp)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = channelId != null) {
                        vm.openChannel(channelId, channelName, avatar)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(boxColor)
                )
                Text(
                    channelName,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            val date = uploadedParts(info?.uploaded)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Figure(meta?.likeCount ?: "N/A", "Likes", Modifier.weight(1f))
                Figure(formatCount(info?.views ?: -1L) ?: "N/A", "Views", Modifier.weight(1f))
                Figure(date?.first ?: "N/A", date?.second ?: "Uploaded", Modifier.weight(1f))
            }

            if (description.isNotBlank()) {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(boxColor)
                        .padding(16.dp)
                ) {
                    Box {
                        Text(
                            styledDescription(description, link),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                            maxLines = if (expanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Clip,
                            onTextLayout = { if (!expanded && it.hasVisualOverflow) overflowing = true }
                        )
                        if (!expanded && overflowing) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, boxColor)))
                            )
                        }
                    }
                    if (overflowing || expanded) {
                        Box(
                            Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(1.dp, scheme.onSurface.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                                .clickable { expanded = !expanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (expanded) "Show less" else "See more",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One of the three big figures: the number (or date) over its small grey label. */
@Composable
private fun Figure(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
