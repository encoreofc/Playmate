package com.playmate.app.ui.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.hypot

/**
 * The PlayMate mark, in its own 1024 x 1024 coordinate space. The three paths and their gradients
 * are the exact artwork of the logo, so the in-app intro matches the launch screen the system
 * draws from `ic_splash_icon.xml` pixel for pixel.
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
     * Outer outline of the whole mark: one closed, rounded triangle, drawn clockwise on screen.
     * The line of the intro follows an offset copy of this outline.
     */
    val silhouette: Path = parse(
        "M270.63,69.03L828.98,435.24C858.83,454.81 870.43,483 870.43,512" +
            "C870.43,541.32 856.62,570.64 828.98,588.76L270.63,954.97" +
            "C220.35,987.95 153.57,951.88 153.57,891.76L153.57,132.24" +
            "C153.57,72.12 220.35,36.05 270.63,69.03Z"
    )

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
 * A closed line that runs around the mark at a fixed distance from its outline, starting at the
 * top-left corner and going clockwise: top edge, right corner, bottom edge, bottom-left corner,
 * left edge, and back to the top-left corner.
 *
 * It is sampled from the outline and pushed outwards along the normal, so the gap to the logo
 * stays the same on straight edges and on the rounded corners alike.
 */
internal class LogoRing private constructor(
    val path: Path,
    val length: Float,
    val corners: List<Corner>
) {

    /** A corner of the mark: where it is on the line, and how far along the line the head is when it gets there (0..1). */
    class Corner(val point: Offset, val at: Float)

    companion object {
        private const val SAMPLES = 960

        // Outermost point of each rounded corner of the outline, in logo units.
        private val TopLeftApex = Offset(193.2f, 65.7f)
        private val RightApex = Offset(870.43f, 512f)
        private val BottomLeftApex = Offset(193.2f, 958.3f)

        /** [gap] is the distance between the logo outline and the line, in logo units. */
        fun build(gap: Float): LogoRing {
            val measure = PathMeasure()
            measure.setPath(LogoGeometry.silhouette, false)
            val total = measure.length

            val sx = FloatArray(SAMPLES)
            val sy = FloatArray(SAMPLES)
            val tx = FloatArray(SAMPLES)
            val ty = FloatArray(SAMPLES)
            for (i in 0 until SAMPLES) {
                val distance = total * i / SAMPLES
                val position = measure.getPosition(distance)
                val tangent = measure.getTangent(distance)
                val size = hypot(tangent.x, tangent.y).coerceAtLeast(1e-6f)
                sx[i] = position.x
                sy[i] = position.y
                tx[i] = tangent.x / size
                ty[i] = tangent.y / size
            }

            // Shoelace sign tells which way the outline runs; the outward normal flips with it.
            var area = 0f
            for (i in 0 until SAMPLES) {
                val j = (i + 1) % SAMPLES
                area += sx[i] * sy[j] - sx[j] * sy[i]
            }
            val outward = if (area > 0f) 1f else -1f

            fun nearest(target: Offset): Int {
                var best = 0
                var bestDistance = Float.MAX_VALUE
                for (i in 0 until SAMPLES) {
                    val d = hypot(sx[i] - target.x, sy[i] - target.y)
                    if (d < bestDistance) {
                        bestDistance = d
                        best = i
                    }
                }
                return best
            }

            val start = nearest(TopLeftApex)
            val rightIndex = (nearest(RightApex) - start + SAMPLES) % SAMPLES
            val bottomIndex = (nearest(BottomLeftApex) - start + SAMPLES) % SAMPLES

            val rx = FloatArray(SAMPLES)
            val ry = FloatArray(SAMPLES)
            for (i in 0 until SAMPLES) {
                val s = (start + i) % SAMPLES
                rx[i] = sx[s] + ty[s] * outward * gap
                ry[i] = sy[s] - tx[s] * outward * gap
            }

            val cumulative = FloatArray(SAMPLES + 1)
            for (i in 1..SAMPLES) {
                val a = i - 1
                val b = i % SAMPLES
                cumulative[i] = cumulative[i - 1] + hypot(rx[b] - rx[a], ry[b] - ry[a])
            }
            val length = cumulative[SAMPLES]

            val path = Path()
            path.moveTo(rx[0], ry[0])
            for (i in 1 until SAMPLES) path.lineTo(rx[i], ry[i])
            path.lineTo(rx[0], ry[0])

            return LogoRing(
                path = path,
                length = length,
                corners = listOf(
                    Corner(Offset(rx[rightIndex], ry[rightIndex]), cumulative[rightIndex] / length),
                    Corner(Offset(rx[bottomIndex], ry[bottomIndex]), cumulative[bottomIndex] / length),
                    Corner(Offset(rx[0], ry[0]), 1f)
                )
            )
        }
    }
}
