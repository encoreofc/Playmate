package com.playmate.app

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.playmate.app.data.NowPlayingStore
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.PlaybackPrefs
import com.playmate.app.data.YouTubeRepo
import com.playmate.app.ui.player.ActivePlayback
import com.playmate.app.ui.player.PlaybackRecovery
import com.playmate.app.ui.player.PlayerHolder
import com.playmate.app.ui.player.audioOnlyItem
import com.playmate.app.ui.player.buildSource
import com.playmate.app.ui.player.defaultQualityIndex
import kotlinx.coroutines.runBlocking

/**
 * The home of playback while the app is in the background, like the music apps do it:
 *
 *  - It is a foreground media service with a notification, so the system keeps it alive.
 *  - Swiping the app away from the recent apps does NOT stop the sound (only a pause does).
 *  - A broken stream is repaired here, without the screen: it retries after network errors,
 *    waits for the network to come back, and asks for fresh links when YouTube's links expire.
 *  - A stalled download (buffering that never ends) is re-opened.
 *  - After the system killed the whole app, the lock screen / Bluetooth play button brings the
 *    sound back from the saved position (MediaSession.Callback.onPlaybackResumption).
 *
 * The player itself is shared with the UI through [PlayerHolder].
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer

    private val handler = Handler(Looper.getMainLooper())

    private var retries = 0
    private var stalls = 0
    private var pendingError: PlaybackException? = null
    private var waitingForNetwork = false

    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        player = PlayerHolder.get(this)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(ResumeCallback())
            .build()

        player.addListener(listener)
        watchNetwork()
        handler.postDelayed(savePositionTask, SAVE_POSITION_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The app was swiped away from the recent apps. If something is playing (and background play is
        // on), the sound simply goes on in this service; the screen comes back when the app is opened.
        val keepGoing = PlaybackPrefs.background(this) &&
            player.playWhenReady &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_ENDED
        if (keepGoing) return

        player.pause()
        player.stop()
        player.clearMediaItems()
        ActivePlayback.clear()
        NowPlayingStore.clear(this)
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        networkCallback?.let { callback ->
            runCatching { connectivity?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        saveNow()
        session?.release()
        session = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ player events

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            onPlayerFailed(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                // Playing fine again: forget all trouble.
                retries = 0
                stalls = 0
                pendingError = null
                waitingForNetwork = false
                PlaybackRecovery.reset()
            }
            updateStallWatch()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateStallWatch()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveNow()
        }
    }

    // ------------------------------------------------------------------ repair after errors

    private val retryTask = Runnable { retryNow() }

    private fun onPlayerFailed(error: PlaybackException) {
        // Errors of the video itself (a codec that cannot decode...) are left to the screen.
        if (!PlaybackRecovery.isRecoverable(error)) return

        pendingError = error
        handler.removeCallbacks(retryTask)

        if (!isOnline()) {
            // No network at all: wait for it; the network callback retries the moment it is back.
            waitingForNetwork = true
            return
        }
        if (retries >= MAX_RETRIES) {
            giveUp(error)
            return
        }
        val wait = (1_000L shl retries).coerceAtMost(8_000L)
        retries++
        handler.postDelayed(retryTask, wait)
    }

    private fun retryNow() {
        val error = pendingError ?: return
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player.seekToDefaultPosition()
                player.prepare()
            }
            // A refused request, or the same error again and again: the links are probably dead.
            PlaybackRecovery.isExpiredLink(error) || retries >= 2 -> refreshStreams()
            else -> player.prepare()
        }
    }

    private fun giveUp(error: PlaybackException) {
        retries = 0
        pendingError = null
        // The screen shows the error (or lowers the quality). With no screen, the notification's
        // play button tries again from scratch.
        PlaybackRecovery.gaveUp = error
    }

    /** Asks YouTube for new stream links and continues from the same second. */
    private fun refreshStreams() {
        val known = ActivePlayback.info
        val url: String? = known?.url ?: player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
        if (url == null || known?.localUri != null) {
            player.prepare()
            return
        }
        val height: Int? = known?.qualities?.getOrNull(ActivePlayback.quality)?.height
        val position = player.currentPosition
        val live = player.isCurrentMediaItemLive
        val withPicture = known != null

        Thread {
            val fresh: PlayableInfo? = runCatching {
                YouTubeRepo.invalidate(url)
                runBlocking { YouTubeRepo.load(url) }
            }.getOrNull()
            handler.post { applyFresh(url, fresh, height, position, live, withPicture) }
        }.start()
    }

    private fun applyFresh(
        url: String,
        fresh: PlayableInfo?,
        height: Int?,
        position: Long,
        live: Boolean,
        withPicture: Boolean
    ) {
        // Something else started in the meantime: leave it alone.
        if (player.currentMediaItem?.mediaId != url) return
        if (fresh == null) {
            // The lookup failed too (no network?): try the old links once more; the error loop continues.
            player.prepare()
            return
        }
        val goOn = player.playWhenReady
        val start = if (live) C.TIME_UNSET else position

        if (withPicture) {
            val index = fresh.qualities.indexOfFirst { it.height == height }
                .takeIf { it >= 0 } ?: defaultQualityIndex(fresh)
            val source = buildSource(fresh, index)
            if (source != null) {
                player.setMediaSource(source, start)
                ActivePlayback.set(fresh, index)
                player.prepare()
                player.playWhenReady = goOn
                return
            }
        }
        // No picture (started again from the lock screen), or no source for it: sound only.
        val item = audioOnlyItem(fresh)
        if (item != null) {
            player.setMediaItem(item, start)
            player.prepare()
            player.playWhenReady = goOn
        } else {
            player.prepare()
        }
    }

    // ------------------------------------------------------------------ stalled buffering

    private val stallTask = object : Runnable {
        override fun run() {
            if (!player.playWhenReady || player.playbackState != Player.STATE_BUFFERING) return
            stalls++
            when {
                !isOnline() -> waitingForNetwork = true
                // A connection that died silently: ask for the same position again, which opens a new one.
                stalls <= 2 -> reopen()
                else -> {
                    stalls = 0
                    pendingError = null
                    refreshStreams()
                }
            }
            updateStallWatch()
        }
    }

    private fun updateStallWatch() {
        handler.removeCallbacks(stallTask)
        if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
            handler.postDelayed(stallTask, STALL_MS)
        }
    }

    private fun reopen() {
        if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
        else player.seekTo(player.currentPosition)
    }

    // ------------------------------------------------------------------ network

    private fun watchNetwork() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        connectivity = manager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post { onNetworkBack() }
            }
        }
        networkCallback = callback
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    private fun onNetworkBack() {
        handler.removeCallbacks(retryTask)
        when {
            pendingError != null -> {
                waitingForNetwork = false
                retryNow()
            }
            waitingForNetwork || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) -> {
                waitingForNetwork = false
                reopen()
            }
        }
    }

    private fun isOnline(): Boolean {
        val manager = connectivity ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ------------------------------------------------------------------ remember the position

    private val savePositionTask = object : Runnable {
        override fun run() {
            saveNow()
            handler.postDelayed(this, SAVE_POSITION_MS)
        }
    }

    private fun saveNow() {
        if (player.mediaItemCount > 0 && !player.isCurrentMediaItemLive) {
            NowPlayingStore.savePosition(this, player.currentPosition)
        }
    }

    // ------------------------------------------------------------------ resume after the app was killed

    private inner class ResumeCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val snapshot = NowPlayingStore.read(this@PlaybackService)
            if (snapshot == null) {
                future.setException(IllegalStateException("Nothing to resume"))
                return future
            }
            Thread {
                try {
                    val item = resumeItem(snapshot)
                    val start = if (snapshot.live) C.TIME_UNSET else snapshot.positionMs
                    future.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, start))
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }.start()
            return future
        }
    }

    /** Sound-only item for the saved video (a download plays from the phone, no network needed). */
    private fun resumeItem(snapshot: NowPlayingStore.Snapshot): MediaItem {
        val local = snapshot.localUri
        if (local != null) {
            val meta = MediaMetadata.Builder()
                .setTitle(snapshot.title)
                .setArtist(snapshot.channel)
                .setArtworkUri(snapshot.thumb?.let { Uri.parse(it) })
                .build()
            return MediaItem.Builder()
                .setMediaId(snapshot.url)
                .setUri(local)
                .setMediaMetadata(meta)
                .build()
        }
        val info = runBlocking { YouTubeRepo.load(snapshot.url) }
        return audioOnlyItem(info) ?: error("No playable stream")
    }

    private companion object {
        const val MAX_RETRIES = 5
        const val STALL_MS = 20_000L
        const val SAVE_POSITION_MS = 5_000L
    }
}
