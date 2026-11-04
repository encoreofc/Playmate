package com.playmate.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.playmate.app.data.VideoItem
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

fun metaLine(channel: String, views: Long, uploaded: String?): String =
    listOfNotNull(
        channel.takeIf { it.isNotBlank() },
        formatCount(views)?.let { "$it views" },
        uploaded?.takeIf { it.isNotBlank() }
    ).joinToString("  ·  ")

fun VideoItem.meta() = metaLine(channel, views, uploaded)

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
