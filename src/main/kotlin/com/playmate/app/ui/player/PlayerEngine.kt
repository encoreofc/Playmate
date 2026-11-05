package com.playmate.app.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.PmDownloader
import com.playmate.app.data.SubtitleTrack
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** One player for the whole app, shared by the UI and the background PlaybackService. */
object PlayerHolder {
    @Volatile
    private var instance: ExoPlayer? = null

    fun get(context: Context): ExoPlayer =
        instance ?: synchronized(this) {
            instance ?: createPlayer(context.applicationContext).also { instance = it }
        }

    /** The running player, or null if none was created yet. Unlike [get] it never creates one. */
    fun peek(): ExoPlayer? = instance
}

/**
 * What the one shared player is playing right now: the stream list and the picked quality.
 * The background service reads it to get fresh links when YouTube's links expire.
 */
object ActivePlayback {
    var info: PlayableInfo? = null
        private set
    var quality: Int = -1
        private set

    fun set(info: PlayableInfo, quality: Int) {
        this.info = info
        this.quality = quality
    }

    fun setQuality(quality: Int) {
        this.quality = quality
    }

    fun clear() {
        info = null
        quality = -1
    }
}

/**
 * The network client used for playback. Shorter timeouts than the extractor's client: a dead
 * connection is noticed in seconds, so the recovery in PlaybackService can reopen it quickly.
 */
val playbackClient: OkHttpClient by lazy {
    PmDownloader.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

/** Turns the video renderer off in the background (audio keeps playing, saves data). */
fun ExoPlayer.setVideoEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
        .build()
}

private fun mediaItemFor(info: PlayableInfo, url: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(info.url)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info.title)
                .setArtist(info.channel)
                .setArtworkUri(info.thumb?.let { Uri.parse(it) })
                .build()
        )
        .build()

/**
 * How much is read ahead before playback may start. Playback begins after a quarter of a second of
 * video for Shorts and under a second for normal videos (the default waits 2.5 s), and keeps a modest
 * buffer so a swipe through many Shorts does not download far more than is watched.
 */
private fun loadControlFor(forShorts: Boolean): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            // Shorts: three players sit side by side, so each keeps only a few seconds. A neighbour must
            // not hog the connection that the Short on screen needs.
            // Normal videos: a longer cushion (30 s to 60 s) so a weak or switching network does not stop
            // the sound; in the background only the audio is loaded, so the cushion costs very little.
            if (forShorts) 2_000 else 30_000,
            if (forShorts) 8_000 else 60_000,
            if (forShorts) 250 else 800,
            if (forShorts) 800 else 3_000
        )
        // Caps the memory a long cushion of a high-quality picture may take.
        .setTargetBufferBytes(if (forShorts) C.LENGTH_UNSET else 40 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

fun createPlayer(context: Context, forShorts: Boolean = false): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            // Used for plain-URL items (playback resumed after the app was killed): same client and
            // user agent as the rest, and local files work too.
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(playbackClient).setUserAgent(PmDownloader.USER_AGENT)
                )
            )
        )
        .setLoadControl(loadControlFor(forShorts))
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .build()

/**
 * The tallest picture worth fetching on the current connection: more on Wi-Fi, less on mobile data
 * (a smaller picture starts sooner and costs less). The quality picker can still go higher.
 */
fun preferredMaxHeight(context: Context, shorts: Boolean): Int {
    val metered = runCatching {
        val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.isActiveNetworkMetered
    }.getOrDefault(false)
    return when {
        // A Short starts sooner at 480p, a little sooner still on mobile data; the picker can go higher.
        shorts && metered -> 360
        shorts -> 480
        metered -> 720
        else -> 1080
    }
}

fun defaultQualityIndex(info: PlayableInfo, maxHeight: Int = 720): Int {
    if (info.qualities.isEmpty()) return -1
    val comfortable = info.qualities.indexOfFirst { it.height <= maxHeight }
    return if (comfortable >= 0) comfortable else info.qualities.lastIndex
}

fun buildSource(
    info: PlayableInfo,
    qualityIndex: Int,
    cache: androidx.media3.datasource.DataSource.Factory? = null,
    subtitle: SubtitleTrack? = null
): MediaSource? {
    // A download kept inside the app: play the file from the phone.
    info.localUri?.let { local ->
        return ProgressiveMediaSource.Factory(FileDataSource.Factory())
            .createMediaSource(mediaItemFor(info, local))
    }

    val http: androidx.media3.datasource.DataSource.Factory = cache
        ?: OkHttpDataSource.Factory(playbackClient).setUserAgent(PmDownloader.USER_AGENT)

    if (info.live && info.hlsUrl != null) {
        return HlsMediaSource.Factory(http).createMediaSource(mediaItemFor(info, info.hlsUrl))
    }

    val quality = info.qualities.getOrNull(qualityIndex) ?: return null
    val progressive = ProgressiveMediaSource.Factory(http)
    fun plain(url: String) = progressive.createMediaSource(MediaItem.fromUri(url))

    // The first source carries the title/channel/artwork shown in the notification.
    val video = progressive.createMediaSource(mediaItemFor(info, quality.video.content))
    val audio = quality.audio
    val picture: MediaSource = if (audio != null) MergingMediaSource(video, plain(audio.content)) else video

    // Captions are a third, side-loaded source. A caption file that can't be fetched must never stop the
    // video, so its load errors are treated as "no more captions".
    if (subtitle == null) return picture
    val captions = SingleSampleMediaSource.Factory(http)
        .setTreatLoadErrorsAsEndOfStream(true)
        .createMediaSource(
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                .setMimeType(subtitle.mime)
                .setLanguage(subtitle.language.ifBlank { null })
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build(),
            C.TIME_UNSET
        )
    return MergingMediaSource(picture, captions)
}

/**
 * A plain audio item for [info], used to bring playback back after the app was killed
 * (there is no picture then, only the sound). Null when the video has no usable stream.
 */
fun audioOnlyItem(info: PlayableInfo): MediaItem? {
    val uri: String? = when {
        info.localUri != null -> info.localUri
        info.live && info.hlsUrl != null -> info.hlsUrl
        else -> info.audioOnly?.content ?: info.qualities.lastOrNull()?.video?.content
    }
    if (uri == null) return null
    return mediaItemFor(info, uri)
}
