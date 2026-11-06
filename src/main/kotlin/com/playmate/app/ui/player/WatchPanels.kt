package com.playmate.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.playmate.app.ui.theme.LocalPmDark

/** YouTube's link blue (hashtags, links): lighter on the dark theme, deeper on the light one. */
@Composable
internal fun ytLinkColor(): Color = if (LocalPmDark.current) Color(0xFF3EA6FF) else Color(0xFF065FD4)

/** YouTube's grey for chips and the description box. Same value as the video page pills. */
@Composable
internal fun ytGrey(): Color = if (LocalPmDark.current) Color(0xFF272727) else Color(0xFFF2F2F2)

private val AgoPattern = Regex("(\\d+)\\s+(second|minute|hour|day|week|month|year)s?\\s+ago")

/** "3 months ago" becomes "3mo ago", the way YouTube prints it in the comments. */
internal fun shortAgo(raw: String): String = raw.replace(AgoPattern) { m ->
    val unit = when (m.groupValues[2]) {
        "second" -> "s"
        "minute" -> "min"
        "hour" -> "h"
        "day" -> "d"
        "week" -> "w"
        "month" -> "mo"
        else -> "y"
    }
    "${m.groupValues[1]}$unit ago"
}

/**
 * The panel YouTube opens right under the video (the player stays visible above it): a drag
 * handle, then the content. Pulling the handle down closes it. Taps never reach the page below.
 */
@Composable
internal fun PanelFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(scheme.surface)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onVerticalDrag = { _, dy ->
                            drag += dy
                            if (drag > 90f) {
                                drag = Float.NEGATIVE_INFINITY
                                onDismiss()
                            }
                        }
                    )
                }
                .padding(top = 8.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.6f))
            )
        }
        content()
    }
}
