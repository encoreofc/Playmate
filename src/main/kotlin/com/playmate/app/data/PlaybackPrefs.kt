package com.playmate.app.data

import android.content.Context

/** Settings for playback that the person can switch from the buttons under a video. */
object PlaybackPrefs {

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("playmate_playback", Context.MODE_PRIVATE)

    /** On by default: leaving the app or turning the screen off keeps the sound going. */
    fun background(ctx: Context): Boolean = prefs(ctx).getBoolean("background", true)

    fun setBackground(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("background", on).apply()
    }
}
