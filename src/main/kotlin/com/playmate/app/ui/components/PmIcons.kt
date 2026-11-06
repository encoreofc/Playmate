package com.playmate.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Playmate's own icon set, drawn from SVG path data on a 24x24 grid.
 * Every icon is one even 1.8-wide round-capped line, so they all look like one family.
 * Use them with Icon(PmIcons.Search, ...): the tint colours the line, so light and dark both work.
 */
object PmIcons {

    private fun line(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    /** Arrow pointing left: back. */
    val Back: ImageVector by lazy {
        line("PmBack", "M19.5 12H5M11 5.5L4.5 12l6.5 6.5")
    }

    /** Magnifier: search. */
    val Search: ImageVector by lazy {
        line("PmSearch", "M10.5 3.8a6.7 6.7 0 1 0 0 13.4a6.7 6.7 0 0 0 0-13.4z", "M15.4 15.4L20.2 20.2")
    }

    /** Clock: something searched before. */
    val Clock: ImageVector by lazy {
        line("PmClock", "M12 3.6a8.4 8.4 0 1 0 0 16.8a8.4 8.4 0 0 0 0-16.8z", "M12 7.8V12.3l3.1 1.9")
    }

    /** Cross: clear / remove. */
    val Close: ImageVector by lazy {
        line("PmClose", "M6.2 6.2l11.6 11.6M17.8 6.2L6.2 17.8")
    }

    /** Arrow pointing up-left: put this text into the search box. */
    val Insert: ImageVector by lazy {
        line("PmInsert", "M17.2 17.2L6.8 6.8M6.8 15V6.8H15")
    }
}
