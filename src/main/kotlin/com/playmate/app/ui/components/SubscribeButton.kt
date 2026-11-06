package com.playmate.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.playmate.app.ui.theme.Tonal
import com.playmate.app.ui.theme.tonalPress

/**
 * Subscribe: a solid pill in the text colour (white on dark, black on light). Once subscribed
 * it settles into a quiet tonal pill with a tick. The colours change smoothly, and the pill
 * gives way a little under the finger.
 */
@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (subscribed) Tonal.fill() else scheme.onSurface,
        animationSpec = tween(180),
        label = "subscribeContainer"
    )
    val content by animateColorAsState(
        targetValue = if (subscribed) scheme.onSurface else scheme.surface,
        animationSpec = tween(180),
        label = "subscribeContent"
    )
    Row(
        modifier
            .heightIn(min = 36.dp)
            .tonalPress(RoundedCornerShape(50), container, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subscribed) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(start = if (subscribed) 6.dp else 0.dp)
        )
    }
}
