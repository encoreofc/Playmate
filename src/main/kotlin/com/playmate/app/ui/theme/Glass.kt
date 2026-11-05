package com.playmate.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The two layers every glass surface is made of: a soft body and a thin lit rim.
 * On dark they are white light; on light they are a soft grey shade.
 */
object Glass {
    private val DarkFill = Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x0FFFFFFF)))
    private val DarkRim = Brush.verticalGradient(listOf(Color(0x70FFFFFF), Color(0x14FFFFFF)))
    private val LightFill = Brush.verticalGradient(listOf(Color(0x14000000), Color(0x07000000)))
    private val LightRim = Brush.verticalGradient(listOf(Color(0x2E000000), Color(0x0F000000)))

    fun fill(dark: Boolean): Brush = if (dark) DarkFill else LightFill
    fun rim(dark: Boolean): Brush = if (dark) DarkRim else LightRim
}

/** Frosted-glass look: translucent body with a thin rim, clipped to [shape]. Follows light/dark. */
fun Modifier.glass(shape: Shape): Modifier = composed {
    val dark = LocalPmDark.current
    this
        .clip(shape)
        .background(Glass.fill(dark), shape)
        .border(0.75.dp, Glass.rim(dark), shape)
}
