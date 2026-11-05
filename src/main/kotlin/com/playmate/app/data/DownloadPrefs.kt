package com.playmate.app.data

import android.content.Context

/** Everything the person can set about downloading. Read fresh each time, so a change applies at once. */
object DownloadPrefs {

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("playmate_download_prefs", Context.MODE_PRIVATE)

    /** Downloads wait for Wi-Fi (or any unmetered network) instead of using mobile data. */
    fun wifiOnly(ctx: Context): Boolean = prefs(ctx).getBoolean("wifi_only", false)

    fun setWifiOnly(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("wifi_only", on).apply()
    }

    /** How many downloads run at the same time (each one already uses several connections). */
    fun parallel(ctx: Context): Int = prefs(ctx).getInt("parallel", 2).coerceIn(1, 4)

    fun setParallel(ctx: Context, count: Int) {
        prefs(ctx).edit().putInt("parallel", count.coerceIn(1, 4)).apply()
    }

    /** The video quality picked first in the sheet: 0 = the best there is, otherwise a height like 1080. */
    fun videoHeight(ctx: Context): Int = prefs(ctx).getInt("video_height", 0)

    fun setVideoHeight(ctx: Context, height: Int) {
        prefs(ctx).edit().putInt("video_height", height).apply()
    }

    /** The audio format picked first in the sheet. */
    fun audioFormat(ctx: Context): AudioFormat =
        AudioFormat.of(prefs(ctx).getString("audio_format", null)) ?: AudioFormat.MP3

    fun setAudioFormat(ctx: Context, format: AudioFormat) {
        prefs(ctx).edit().putString("audio_format", format.id).apply()
    }

    fun mp3Kbps(ctx: Context): Int = prefs(ctx).getInt("mp3_kbps", 320)

    fun setMp3Kbps(ctx: Context, kbps: Int) {
        prefs(ctx).edit().putInt("mp3_kbps", kbps).apply()
    }

    /** Whether the sheet opens on Audio (it remembers what was used last). */
    fun lastWasAudio(ctx: Context): Boolean = prefs(ctx).getBoolean("last_audio", false)

    fun setLastWasAudio(ctx: Context, audio: Boolean) {
        prefs(ctx).edit().putBoolean("last_audio", audio).apply()
    }

    /** Write title, artist and cover art into audio files. */
    fun embedMeta(ctx: Context): Boolean = prefs(ctx).getBoolean("embed_meta", true)

    fun setEmbedMeta(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("embed_meta", on).apply()
    }

    /** Where playlists and pasted links go by default: true = inside Playmate, false = the phone's folders. */
    fun toApp(ctx: Context): Boolean = prefs(ctx).getBoolean("to_app", true)

    fun setToApp(ctx: Context, inApp: Boolean) {
        prefs(ctx).edit().putBoolean("to_app", inApp).apply()
    }

    // ---------------------------------------------------------------- job numbers

    /** A number no earlier download ever had, even after the app restarted. */
    @Synchronized
    fun nextJobId(ctx: Context): Int {
        val p = prefs(ctx)
        val next = p.getInt("next_job_id", 1)
        p.edit().putInt("next_job_id", next + 1).apply()
        return next
    }
}
