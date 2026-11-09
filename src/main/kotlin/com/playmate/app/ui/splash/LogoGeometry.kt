package com.playmate.app.ui.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The Playmate mark as code: a yellow rounded tile with a black "P" made of three separate parts
 * (the bowl, the bar inside it and the stem). Nothing here is an image. Every edge is a straight
 * line or a true circular arc, measured from the approved artwork, in a 1500 x 1448 space whose
 * origin is the top-left corner of the tile. Colours are flat: no gradient, no glow.
 */
internal object LogoGeometry {

    /** Size of the tile, which is the whole mark. */
    const val WIDTH = 1500f
    const val HEIGHT = 1448f
    const val TILE_RADIUS = 328f

    val Yellow = Color(0xFFFEC80B)
    val Black = Color(0xFF050504)

    // Centre line shared by the bowl, the counter inside it and the bar.
    private const val CY = 655.4f

    // Bowl: the outer "D", with a hollow counter and a leg that runs down to the baseline.
    private const val BOWL_R = 343.5f
    private const val BOWL_CX = 954.65f
    private const val ARM_R = 250.9f          // rounded top-left of the bowl, tangent to its top edge
    private const val ARM_CX = 579.6f
    private const val COUNTER_R = 161f
    private const val COUNTER_CX = 939f
    private const val LEG_X = 649.9f

    // Bar: a pill with a full round right end and a round top-left corner.
    private const val BAR_R = 89.5f
    private const val BAR_LEFT = 322.9f
    private const val BAR_CX = 939f

    // Stem: round top-left, large round bottom-right, small round bottom-left.
    private const val STEM_LEFT = 322.9f
    private const val STEM_RIGHT = 573.2f
    private const val STEM_BOTTOM = 1248.9f
    private const val STEM_TOP_R = 79f
    private const val STEM_RIGHT_R = 191.5f
    private const val STEM_LEFT_R = 50f

    /** Where each part comes from when the mark is assembled, as an offset from its final place. */
    val stemFrom = Offset(0f, 100f)
    val barFrom = Offset(-110f, 0f)
    val bowlFrom = Offset(110f, 0f)

    val bowl: Path = Path().apply {
        val top = CY - BOWL_R
        val bottom = CY + BOWL_R
        val counterTop = CY - COUNTER_R
        val counterBottom = CY + COUNTER_R
        val armCy = top + ARM_R
        val tipX = ARM_CX - sqrt(ARM_R * ARM_R - (counterTop - armCy) * (counterTop - armCy))
        val tipAngle = Math.toDegrees(atan2(counterTop - armCy, tipX - ARM_CX).toDouble()).toFloat()

        moveTo(tipX, counterTop)
        arc(ARM_CX, armCy, ARM_R, tipAngle, -90f - tipAngle)
        lineTo(BOWL_CX, top)
        arc(BOWL_CX, CY, BOWL_R, -90f, 180f)
        lineTo(LEG_X, bottom)
        lineTo(LEG_X, counterBottom)
        lineTo(COUNTER_CX, counterBottom)
        arc(COUNTER_CX, CY, COUNTER_R, 90f, -180f)
        close()
    }

    val bar: Path = Path().apply {
        moveTo(BAR_LEFT, CY)
        arc(BAR_LEFT + BAR_R, CY, BAR_R, 180f, 90f)
        lineTo(BAR_CX, CY - BAR_R)
        arc(BAR_CX, CY, BAR_R, -90f, 180f)
        lineTo(BAR_LEFT, CY + BAR_R)
        close()
    }

    val stem: Path = Path().apply {
        val top = CY + COUNTER_R
        moveTo(STEM_LEFT, top + STEM_TOP_R)
        arc(STEM_LEFT + STEM_TOP_R, top + STEM_TOP_R, STEM_TOP_R, 180f, 90f)
        lineTo(STEM_RIGHT, top)
        lineTo(STEM_RIGHT, STEM_BOTTOM - STEM_RIGHT_R)
        arc(STEM_RIGHT - STEM_RIGHT_R, STEM_BOTTOM - STEM_RIGHT_R, STEM_RIGHT_R, 0f, 90f)
        lineTo(STEM_LEFT + STEM_LEFT_R, STEM_BOTTOM)
        arc(STEM_LEFT + STEM_LEFT_R, STEM_BOTTOM - STEM_LEFT_R, STEM_LEFT_R, 90f, 90f)
        close()
    }

    /** An arc of the circle ([cx], [cy], [r]); angles in degrees, 0 at 3 o'clock, clockwise positive. */
    private fun Path.arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) {
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), startDeg, sweepDeg, false)
    }
}
