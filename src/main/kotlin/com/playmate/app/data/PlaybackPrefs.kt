package com.playmate.app.data

import android.content.Context
import java.time.LocalDate

/** Settings for playback that the person can switch from the buttons under a video. */
object PlaybackPrefs {

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("playmate_playback", Context.MODE_PRIVATE)

    /** On by default: leaving the app or turning the screen off keeps the sound going. */
    fun background(ctx: Context): Boolean = prefs(ctx).getBoolean("background", true)

    fun setBackground(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("background", on).apply()
    }

    // ---------------------------------------------------------------- Shorts timer

    private fun today(): String = LocalDate.now().toString()

    /** The daily Shorts limit in minutes; 0 means no limit (the default). */
    fun shortsLimitMin(ctx: Context): Int = prefs(ctx).getInt("shorts_limit_min", 0)

    fun setShortsLimitMin(ctx: Context, minutes: Int) {
        prefs(ctx).edit()
            .putInt("shorts_limit_min", minutes.coerceAtLeast(0))
            // A new limit starts a fresh reminder: it may fire again today.
            .putString("shorts_reminded_day", "")
            .apply()
    }

    /** How long Shorts have played today, in milliseconds. Starts from zero again every day. */
    fun shortsUsedMs(ctx: Context): Long {
        val p = prefs(ctx)
        return if (p.getString("shorts_used_day", "") == today()) p.getLong("shorts_used_ms", 0L) else 0L
    }

    fun addShortsUsedMs(ctx: Context, ms: Long) {
        if (ms <= 0L) return
        val p = prefs(ctx)
        val day = today()
        val base = if (p.getString("shorts_used_day", "") == day) p.getLong("shorts_used_ms", 0L) else 0L
        p.edit().putString("shorts_used_day", day).putLong("shorts_used_ms", base + ms).apply()
    }

    /** Whether today's "time for a break" reminder was already shown (and dismissed). */
    fun shortsReminded(ctx: Context): Boolean = prefs(ctx).getString("shorts_reminded_day", "") == today()

    fun setShortsReminded(ctx: Context, reminded: Boolean) {
        prefs(ctx).edit().putString("shorts_reminded_day", if (reminded) today() else "").apply()
    }

    // ---------------------------------------------------------------- Shorts captions

    /** The caption language chosen for Shorts, or null while captions are off (the default). */
    fun shortsCaptionLang(ctx: Context): String? =
        prefs(ctx).getString("shorts_caption_lang", null)?.takeIf { it.isNotBlank() }

    fun setShortsCaptionLang(ctx: Context, language: String?) {
        prefs(ctx).edit().putString("shorts_caption_lang", language.orEmpty()).apply()
    }
}
