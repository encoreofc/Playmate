package com.playmate.app.ui.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.hypot

/**
 * The PlayMate mark as code, in its own 1024 x 1024 coordinate space: three paths, each with its
 * own gradient. Nothing here is an image; the intro draws these shapes directly.
 */
internal object LogoGeometry {

    /** Side of the square the artwork is drawn in. */
    const val VIEW = 1024f

    val upper: Path = parse(
        "M274.02,952.75L270.63,954.97C220.35,987.95 153.57,951.88 153.57,891.76L153.57,132.24" +
            "C153.57,72.12 220.35,36.05 270.63,69.03L828.98,435.24C858.83,454.81 872.56,487.44 870.17,519.03" +
            "L713.62,516.21C715.07,497.3 706.72,477.75 688.57,466.31L334.55,243.23" +
            "C305.78,225.11 268.32,245.78 268.32,279.78L268.32,744.22" +
            "C268.32,746.26 268.45,748.25 268.71,750.19Z"
    )

    val lower: Path = parse(
        "M870.43,512C870.43,541.32 856.62,570.64 828.98,588.76L270.63,954.97L268.32,744.22" +
            "C268.32,778.22 305.78,798.89 334.55,780.77L688.57,557.69" +
            "C705.37,547.09 713.78,529.55 713.78,512Z"
    )

    val small: Path = parse(
        "M372.04,386.59L549.91,495.9C561.92,503.28 561.92,520.72 549.91,528.1L372.04,637.41" +
            "C359.44,645.14 343.24,636.08 343.24,621.3L343.24,402.7" +
            "C343.24,387.92 359.44,378.86 372.04,386.59Z"
    )

    /**
     * The outer edge of the whole mark: one closed, rounded triangle, drawn clockwise on screen.
     * The light of the intro runs along exactly this edge, so it belongs to the logo itself.
     */
    val silhouette: Path = parse(
        "M270.63,69.03L828.98,435.24C858.83,454.81 870.43,483 870.43,512" +
            "C870.43,541.32 856.62,570.64 828.98,588.76L270.63,954.97" +
            "C220.35,987.95 153.57,951.88 153.57,891.76L153.57,132.24" +
            "C153.57,72.12 220.35,36.05 270.63,69.03Z"
    )

    /** Centre of the small play mark inside the hollow. */
    val markCenter = Offset(446f, 512f)

    val rim: LogoRim by lazy { LogoRim.build() }

    val upperBrush: Brush = Brush.linearGradient(
        0f to Color(0xFF8AC78E),
        0.45f to Color(0xFF9ED4A0),
        0.8f to Color(0xFFCFEBD1),
        1f to Color(0xFFEEF8EF),
        start = Offset(153.57f, 929.15f),
        end = Offset(916.32f, 503.9f)
    )

    val lowerBrush: Brush = Brush.linearGradient(
        0f to Color(0xFFF7FCF7),
        1f to Color(0xFFD5EDD7),
        start = Offset(308.82f, 908.9f),
        end = Offset(902.82f, 517.4f)
    )

    val smallBrush: Brush = Brush.linearGradient(
        0f to Color(0xFFFFFFFF),
        1f to Color(0xFFE3F2E4),
        start = Offset(342.57f, 368.9f),
        end = Offset(578.82f, 659.15f)
    )

    private fun parse(data: String): Path = PathParser().parsePathString(data).toPath()
}

/**
 * Distances along the logo's outer edge, counted clockwise from the top-left corner: the lap
 * starts in that corner, goes through the top edge to the right corner, along the bottom edge to
 * the bottom-left corner, up the left edge, and closes where it began.
 */
internal class LogoRim private constructor(
    /** Length of the whole edge, in logo units. */
    val length: Float,
    private val origin: Float,
    /** The corners the light passes, with how far along the lap (0..1) it is when it gets there. */
    val corners: List<Corner>
) {

    class Corner(val point: Offset, val at: Float)

    /** The stretch of edge between two distances (counted from the top-left corner) into [dest]. */
    fun segment(measure: PathMeasure, from: Float, to: Float, dest: Path): Boolean {
        dest.reset()
        if (to - from < 0.5f) return false
        val a = origin + from
        val b = origin + to
        return when {
            b <= length -> measure.getSegment(a, b, dest, true)
            a >= length -> measure.getSegment(a - length, b - length, dest, true)
            else -> {
                measure.getSegment(a, length, dest, true)
                measure.getSegment(0f, b - length, dest, false)
                true
            }
        }
    }

    fun pointAt(measure: PathMeasure, distance: Float): Offset =
        measure.getPosition((origin + distance) % length)

    companion object {
        private const val PROBES = 1200

        // Outermost point of each rounded corner of the edge, in logo units.
        private val TopLeftApex = Offset(193.2f, 65.7f)
        private val RightApex = Offset(870.43f, 512f)
        private val BottomLeftApex = Offset(193.2f, 958.3f)

        fun build(): LogoRim {
            val measure = PathMeasure()
            measure.setPath(LogoGeometry.silhouette, false)
            val total = measure.length

            fun nearest(target: Offset): Float {
                var best = 0f
                var bestDistance = Float.MAX_VALUE
                for (i in 0..PROBES) {
                    val distance = total * i / PROBES
                    val p = measure.getPosition(distance)
                    val d = hypot(p.x - target.x, p.y - target.y)
                    if (d < bestDistance) {
                        bestDistance = d
                        best = distance
                    }
                }
                return best
            }

            val origin = nearest(TopLeftApex)
            fun corner(target: Offset): Corner {
                val d = nearest(target)
                return Corner(measure.getPosition(d), ((d - origin + total) % total) / total)
            }

            val start = measure.getPosition(origin)
            return LogoRim(
                length = total,
                origin = origin,
                corners = listOf(
                    Corner(start, 0f),
                    corner(RightApex),
                    corner(BottomLeftApex),
                    Corner(start, 1f)
                )
            )
        }
    }
}
