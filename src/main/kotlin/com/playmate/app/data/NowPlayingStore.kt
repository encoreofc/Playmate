package com.playmate.app.data

import android.content.Context

/**
 * Remembers what is playing, so that
 *  - the app can show the mini player again when it is opened while the sound is still going, and
 *  - playback can be resumed (lock screen, Bluetooth button) after the system killed the app.
 */
object NowPlayingStore {

    class Snapshot(
        val url: String,
        val title: String,
        val channel: String,
        val thumb: String?,
        val durationSec: Long,
        val live: Boolean,
        val localUri: String?,
        val positionMs: Long
    )

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("playmate_now_playing", Context.MODE_PRIVATE)

    /** Called when a video starts. The position starts again from zero. */
    fun save(ctx: Context, info: PlayableInfo, durationSec: Long = 0L) {
        prefs(ctx).edit()
            .putString("url", info.url)
            .putString("title", info.title)
            .putString("channel", info.channel)
            .putString("thumb", info.thumb)
            .putLong("duration", durationSec)
            .putBoolean("live", info.live)
            .putString("local", info.localUri)
            .putLong("position", 0L)
            .apply()
    }

    fun savePosition(ctx: Context, positionMs: Long) {
        val p = prefs(ctx)
        if (p.getString("url", null) == null) return
        p.edit().putLong("position", positionMs.coerceAtLeast(0L)).apply()
    }

    fun read(ctx: Context): Snapshot? {
        val p = prefs(ctx)
        val url = p.getString("url", null)?.takeIf { it.isNotBlank() } ?: return null
        return Snapshot(
            url = url,
            title = p.getString("title", "").orEmpty(),
            channel = p.getString("channel", "").orEmpty(),
            thumb = p.getString("thumb", null),
            durationSec = p.getLong("duration", 0L),
            live = p.getBoolean("live", false),
            localUri = p.getString("local", null),
            positionMs = p.getLong("position", 0L)
        )
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
