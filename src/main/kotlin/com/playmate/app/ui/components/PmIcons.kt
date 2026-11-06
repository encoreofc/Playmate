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

    /** Bell: notifications. */
    val Bell: ImageVector by lazy {
        line("PmBell", "M6.5 16.5V11a5.5 5.5 0 0 1 11 0v5.5l1.6 2H4.9z", "M10 21a2 2 0 0 0 4 0")
    }

    /** The @ sign: mentions. */
    val At: ImageVector by lazy {
        line(
            "PmAt",
            "M12 8.8a3.2 3.2 0 1 0 0 6.4a3.2 3.2 0 0 0 0-6.4z",
            "M15.2 8.8v4.3a2.1 2.1 0 0 0 4.2 0V12a7.4 7.4 0 1 0-2.9 5.9"
        )
    }

    /** Speech bubble: comments. */
    val Comment: ImageVector by lazy {
        line(
            "PmComment",
            "M5 5.5h14a1.5 1.5 0 0 1 1.5 1.5v8.5a1.5 1.5 0 0 1-1.5 1.5h-7l-4.5 3.5V17H5a1.5 1.5 0 0 1-1.5-1.5V7A1.5 1.5 0 0 1 5 5.5z"
        )
    }

    /**
     * Headphones with a sound wave between the cups: listen only, no picture. The button that turns
     * the video into the cassette player.
     */
    val AudioMode: ImageVector by lazy {
        line(
            "PmAudioMode",
            "M4 14.5V12a8 8 0 0 1 16 0v2.5",
            "M4 14.5h2.4a1.1 1.1 0 0 1 1.1 1.1v3.3a1.1 1.1 0 0 1-1.1 1.1h-.8A1.6 1.6 0 0 1 4 18.4z",
            "M20 14.5h-2.4a1.1 1.1 0 0 0-1.1 1.1v3.3a1.1 1.1 0 0 0 1.1 1.1h.8a1.6 1.6 0 0 0 1.6-1.6z",
            "M10.2 15.2v2.6M12 12.8v7M13.8 14.4v3.4"
        )
    }

    /** Arrow turning forward: share. */
    val Share: ImageVector by lazy {
        line("PmShare", "M14.3 4.8l6.2 6.1-6.2 6.1", "M20 10.9h-8.6a6.9 6.9 0 0 0-6.9 6.9v1.4")
    }

    /** Arrow down onto a line: download into the app. */
    val Download: ImageVector by lazy {
        line("PmDownload", "M12 4.2v10.6", "M7.4 10.4l4.6 4.6 4.6-4.6", "M5 19.6h14")
    }

    /** Tick on the same line: already downloaded. */
    val DownloadDone: ImageVector by lazy {
        line("PmDownloadDone", "M7.2 11.6l3.4 3.4 6.2-6.4", "M5 19.6h14")
    }

    /** Headphones: background play. The same cups as [AudioMode], without the wave. */
    val Headphones: ImageVector by lazy {
        line(
            "PmHeadphones",
            "M4 14.5V12a8 8 0 0 1 16 0v2.5",
            "M4 14.5h2.4a1.1 1.1 0 0 1 1.1 1.1v3.3a1.1 1.1 0 0 1-1.1 1.1h-.8A1.6 1.6 0 0 1 4 18.4z",
            "M20 14.5h-2.4a1.1 1.1 0 0 0-1.1 1.1v3.3a1.1 1.1 0 0 0 1.1 1.1h.8a1.6 1.6 0 0 0 1.6-1.6z"
        )
    }

    /** Bookmark: save to a playlist. */
    val Bookmark: ImageVector by lazy {
        line("PmBookmark", "M7.2 3.8h9.6a1.4 1.4 0 0 1 1.4 1.4v14.6l-6.2-4.3-6.2 4.3V5.2a1.4 1.4 0 0 1 1.4-1.4z")
    }

    /** A phone with an arrow going into it: save as a file on the phone. */
    val PhoneSave: ImageVector by lazy {
        line(
            "PmPhoneSave",
            "M8.2 2.8h7.6a1.8 1.8 0 0 1 1.8 1.8v14.8a1.8 1.8 0 0 1-1.8 1.8H8.2a1.8 1.8 0 0 1-1.8-1.8V4.6a1.8 1.8 0 0 1 1.8-1.8z",
            "M12 7.6v6.2",
            "M9.4 11.4l2.6 2.6 2.6-2.6"
        )
    }

    private const val THUMB_CUFF = "M3.7 10.6h3.5v9.8H3.7z"
    private const val THUMB_HAND =
        "M7.2 10.6l3.8-5.7a1.5 1.5 0 0 1 2.7 1l-.6 4h5a2 2 0 0 1 2 2.4l-1.3 6.5a2 2 0 0 1-2 1.6H7.2"

    /**
     * The thumbs, drawn in the same line as everything else: an outline when off and the same
     * shape filled when on. The dislike thumbs are these turned half a circle.
     */
    private fun thumb(name: String, solid: Boolean, down: Boolean): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        if (down) builder.addGroup(rotate = 180f, pivotX = 12f, pivotY = 12f)
        listOf(THUMB_CUFF, if (solid) "${THUMB_HAND}z" else THUMB_HAND).forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (solid) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        if (down) builder.clearGroup()
        return builder.build()
    }

    val ThumbUp: ImageVector by lazy { thumb("PmThumbUp", solid = false, down = false) }
    val ThumbUpSolid: ImageVector by lazy { thumb("PmThumbUpSolid", solid = true, down = false) }
    val ThumbDown: ImageVector by lazy { thumb("PmThumbDown", solid = false, down = true) }
    val ThumbDownSolid: ImageVector by lazy { thumb("PmThumbDownSolid", solid = true, down = true) }
}
