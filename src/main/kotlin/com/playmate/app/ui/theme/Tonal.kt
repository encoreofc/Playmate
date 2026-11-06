package com.playmate.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Tonal surfaces: a quiet tint of the text colour laid over the page, instead of a hard grey
 * and an outline. It adapts to light and dark by itself and keeps every pill, card and chip
 * of the video page in one family.
 */
object Tonal {
    /** Resting chips and cards. */
    @Composable
    fun fill(): Color {
        val base = MaterialTheme.colorScheme.onSurface
        return base.copy(alpha = if (LocalPmDark.current) 0.10f else 0.055f)
    }

    /** One step stronger: a pressed or switched-on neutral surface. */
    @Composable
    fun fillStrong(): Color {
        val base = MaterialTheme.colorScheme.onSurface
        return base.copy(alpha = if (LocalPmDark.current) 0.16f else 0.09f)
    }

    /** Hairlines and dividers inside a tonal surface. */
    @Composable
    fun hairline(): Color {
        val base = MaterialTheme.colorScheme.onSurface
        return base.copy(alpha = if (LocalPmDark.current) 0.16f else 0.12f)
    }

    /** The accent laid softly over a surface: a switched-on chip. */
    fun accentFill(dark: Boolean): Color = Accent.copy(alpha = if (dark) 0.20f else 0.12f)
}

/**
 * A tappable surface that gives way a little under the finger (scale 0.96) and ripples inside
 * its own shape. The order matters: scale, clip, colour, then the click, so the ripple is clipped.
 */
@Composable
fun Modifier.tonalPress(
    shape: Shape,
    color: Color,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "press"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(color)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}
