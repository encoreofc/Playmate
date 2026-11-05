package com.playmate.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.playmate.app.data.VideoItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.floor

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatCount(n: Long): String? {
    fun tidy(v: Double): String =
        if (v >= 100 || v == floor(v)) v.toLong().toString()
        else String.format(Locale.US, "%.1f", v).removeSuffix(".0")

    return when {
        n < 0 -> null
        n < 1_000 -> n.toString()
        n < 1_000_000 -> tidy(n / 1_000.0) + "K"
        n < 1_000_000_000 -> tidy(n / 1_000_000.0) + "M"
        else -> tidy(n / 1_000_000_000.0) + "B"
    }
}

private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}.*")

/**
 * "2 days ago" for a machine date like 2026-11-04T04:00:07-08:00; text that is already
 * readable ("2 days ago", "Streamed 1 hour ago") is returned as it is.
 */
fun agoText(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!ISO_DATE.matches(text)) return text
    val moment = runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(text.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull()
        ?: return text
    val seconds = Duration.between(moment, Instant.now()).seconds
    if (seconds < 0) return text.take(10)
    fun ago(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        seconds < 60 -> "Just now"
        seconds < 3_600 -> ago(seconds / 60, "minute")
        seconds < 86_400 -> ago(seconds / 3_600, "hour")
        seconds < 7 * 86_400 -> ago(seconds / 86_400, "day")
        seconds < 30 * 86_400 -> ago(seconds / (7 * 86_400), "week")
        seconds < 365 * 86_400 -> ago(seconds / (30 * 86_400), "month")
        else -> ago(seconds / (365 * 86_400), "year")
    }
}

fun metaLine(channel: String, views: Long, uploaded: String?): String =
    listOfNotNull(
        channel.takeIf { it.isNotBlank() },
        formatCount(views)?.let { "$it views" },
        agoText(uploaded)
    ).joinToString(" · ")

fun VideoItem.meta() = metaLine(channel, views, uploaded)

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
