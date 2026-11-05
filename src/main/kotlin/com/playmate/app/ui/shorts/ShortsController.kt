package com.playmate.app.ui.shorts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.playmate.app.data.InnerTube
import com.playmate.app.data.Load
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.SearchPage
import com.playmate.app.data.Session
import com.playmate.app.data.ShortsFeed
import com.playmate.app.data.VideoItem
import com.playmate.app.data.YouTubeRepo
import com.playmate.app.data.attempt
import com.playmate.app.data.friendly
import com.playmate.app.data.videoIdOf
import com.playmate.app.ui.player.buildSource
import com.playmate.app.ui.player.createPlayer
import com.playmate.app.ui.player.defaultQualityIndex
import com.playmate.app.ui.player.preferredMaxHeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Everything behind the Shorts screen except drawing it.
 *
 * What makes a swipe feel instant is that the next Short is ready before the finger moves:
 *
 *  - the streams of the Short on screen and the next three are found ahead of time (shared through
 *    the stream cache), and the page data of the next two (like count, channel, subscription) is
 *    fetched with them;
 *  - three players sit in a ring: the Short on screen, the next one and the previous one. The next
 *    one is already prepared and holds its first picture, so a swipe shows video at once;
 *  - playback switches the moment a page is more than half on screen, not when the scroll ends;
 *  - the endless list: the platform's own Shorts sequence first, then related Shorts, then search;
 *  - watch-history reports for the signed-in account.
 *
 * Must be created and used on the main thread.
 */
class ShortsController(
    context: Context,
    private val scope: CoroutineScope,
    private val reportScope: CoroutineScope,
    private val preloadMeta: (String) -> Unit,
    initial: List<VideoItem>,
    startPage: Int
) {
    /** One player and what the page that uses it needs to know. */
    class Slot(val player: ExoPlayer) {
        var url by mutableStateOf<String?>(null)
        var firstFrame by mutableStateOf(false)
        var buffering by mutableStateOf(false)
        var aspect by mutableFloatStateOf(0f)
        var error by mutableStateOf<String?>(null)
        var quality by mutableIntStateOf(-1)
        var retries = 0
    }

    private val appContext = context.applicationContext

    val items = mutableStateListOf<VideoItem>().apply { addAll(initial) }
    val infos = mutableStateMapOf<String, Load<PlayableInfo>>()

    /** The page that is more than half on screen. */
    var current by mutableIntStateOf(startPage.coerceIn(0, (initial.size - 1).coerceAtLeast(0)))
        private set
    var paused by mutableStateOf(false)
        private set
    var muted by mutableStateOf(false)
        private set
    private var speedState by mutableFloatStateOf(1f)

    /** Playback speed. Read-only here; change it with [setSpeed]. */
    val speed: Float get() = speedState

    /** Where the Short on screen is, 0..1. Read it where things are drawn, not where they are composed. */
    var progress by mutableFloatStateOf(0f)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var scrubbing by mutableStateOf(false)
        private set

    private val ids = HashSet<String>().apply { initial.forEach { idOf(it)?.let(::add) } }

    val slots: List<Slot> = List(SLOTS) {
        val player = createPlayer(appContext, forShorts = true)
        player.repeatMode = Player.REPEAT_MODE_ONE
        Slot(player).also { slot -> player.addListener(listenerFor(slot)) }
    }

    private var foreground = true
    private var loadingMore = false
    private var reelToken: String? = null
    private var searchPage: SearchPage? = null
    private var queryIndex = 0
    private var watchJob: Job? = null
    private var watchSession: InnerTube.WatchSession? = null
    private var watchUrl: String? = null

    /** Links whose streams are being looked up right now (main thread only). */
    private val resolving = HashSet<String>()

    fun slotFor(page: Int): Slot = slots[page.mod(SLOTS)]

    private fun currentSlot(): Slot? {
        val url = items.getOrNull(current)?.url ?: return null
        return slots.firstOrNull { it.url == url }
    }

    // ------------------------------------------------------------------ paging and playback

    /** [page] is now the one that is more than half on screen: it plays, the others wait. */
    fun onPage(page: Int) {
        if (page !in items.indices) return
        val previous = current
        current = page
        paused = false
        progress = 0f
        scrubbing = false
        if (previous != page) rewind(previous)
        sync()
        loadMore()
    }

    /** The scroll has come to rest on [page]: now it counts as watched. */
    fun onSettled(page: Int) {
        val item = items.getOrNull(page) ?: return
        if (page == current) restartWatch(item)
    }

    fun togglePause() {
        paused = !paused
        applyPlayState()
    }

    fun toggleMute() {
        muted = !muted
        slots.forEach { it.player.volume = if (muted) 0f else 1f }
    }

    fun setSpeed(value: Float) {
        speedState = value
        slots.forEach { it.player.setPlaybackSpeed(value) }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        applyPlayState()
    }

    fun retry(page: Int) {
        val item = items.getOrNull(page) ?: return
        // A stored link may be the thing that stopped working: look the streams up again.
        YouTubeRepo.invalidate(item.url)
        infos.remove(item.url)
        resolving.remove(item.url)
        slotFor(page).url = null
        sync()
    }

    /** Plays the Short on screen again at another quality, from where it is now. */
    fun setQuality(index: Int) {
        val item = items.getOrNull(current) ?: return
        val info = (infos[item.url] as? Load.Ready)?.data ?: return
        val slot = currentSlot() ?: return
        start(slot, info, index, slot.player.currentPosition)
    }

    /** Called every frame by the screen; keeps the thin progress line moving. */
    fun tick() {
        if (scrubbing || paused) return
        val slot = currentSlot() ?: return
        val duration = slot.player.duration
        val value = if (duration > 0) (slot.player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
        if (value != progress) progress = value
        val length = if (duration > 0) duration else 0L
        if (length != durationMs) durationMs = length
    }

    fun scrubStart() {
        scrubbing = true
    }

    fun scrubTo(fraction: Float) {
        progress = fraction.coerceIn(0f, 1f)
        val slot = currentSlot() ?: return
        val duration = slot.player.duration
        if (duration > 0) {
            durationMs = duration
            slot.player.seekTo((duration * progress).toLong())
        }
    }

    fun scrubEnd() {
        scrubbing = false
    }

    fun release() {
        finishWatch()
        slots.forEach { runCatching { it.player.release() } }
    }

    /** A Short that was left starts from its beginning again if the viewer comes back to it. */
    private fun rewind(page: Int) {
        val url = items.getOrNull(page)?.url ?: return
        slots.firstOrNull { it.url == url }?.player?.seekTo(0)
    }

    /** Gives the Short on screen and its two neighbours a player each, and gets the ones further on ready. */
    private fun sync() {
        for (page in current - 1..current + 1) assign(page)
        prefetchWindow()
        val from = (current - 6).coerceAtLeast(0)
        val to = (current + 10).coerceAtMost(items.size)
        if (from < to) {
            val keep = items.subList(from, to).map { it.url }.toHashSet()
            infos.keys.retainAll(keep)
        }
        applyPlayState()
    }

    private fun assign(page: Int) {
        val item = items.getOrNull(page) ?: return
        val slot = slotFor(page)
        if (slot.url == item.url) return
        slot.url = item.url
        slot.firstFrame = false
        slot.buffering = true
        slot.aspect = 0f
        slot.error = null
        slot.retries = 0
        slot.quality = -1
        slot.player.stop()
        slot.player.clearMediaItems()
        when (val known = infos[item.url]) {
            is Load.Ready -> begin(slot, known)
            is Load.Failed -> slot.error = known.message
            else -> resolve(item, 0L)
        }
    }

    // ------------------------------------------------------------------ getting ahead

    /** The Short on screen and the next ones: streams and page data are looked up before they are needed. */
    private fun prefetchWindow() {
        for (offset in 0..INFO_AHEAD) {
            val item = items.getOrNull(current + offset) ?: break
            // The next Short is needed within a second; the ones after it only if the viewer stays.
            val delayMs = if (offset <= 1) 0L else FAR_DELAY_MS
            resolve(item, delayMs)
            if (offset <= META_AHEAD) scheduleMeta(item, delayMs)
        }
    }

    private fun wanted(url: String): Boolean {
        val index = items.indexOfFirst { it.url == url }
        return index >= current - 1 && index <= current + INFO_AHEAD
    }

    private fun scheduleMeta(item: VideoItem, delayMs: Long) {
        val id = idOf(item) ?: return
        if (delayMs <= 0L) {
            preloadMeta(id)
            return
        }
        scope.launch {
            delay(delayMs)
            if (wanted(item.url)) preloadMeta(id)
        }
    }

    /** Finds the streams of [item] (once), and starts the player that is waiting for them. */
    private fun resolve(item: VideoItem, delayMs: Long) {
        val url = item.url
        if (infos[url] != null || !resolving.add(url)) return
        scope.launch {
            if (delayMs > 0L) {
                delay(delayMs)
                if (!wanted(url)) {
                    resolving.remove(url)
                    return@launch
                }
            }
            val result: Load<PlayableInfo> = attempt { YouTubeRepo.load(url) }
                .fold({ Load.Ready(it) }, { Load.Failed(friendly(it)) })
            resolving.remove(url)
            infos[url] = result
            slots.firstOrNull { it.url == url }?.let { slot -> begin(slot, result) }
        }
    }

    private fun begin(slot: Slot, result: Load<PlayableInfo>) {
        when (result) {
            is Load.Ready -> start(
                slot,
                result.data,
                defaultQualityIndex(result.data, preferredMaxHeight(appContext, true))
            )
            is Load.Failed -> slot.error = result.message
            Load.Loading -> Unit
        }
    }

    private fun start(slot: Slot, info: PlayableInfo, quality: Int, resumeMs: Long = 0L) {
        val source = buildSource(info, quality)
        if (source == null) {
            slot.error = "No playable stream was found for this Short."
            return
        }
        slot.quality = quality
        slot.error = null
        slot.player.volume = if (muted) 0f else 1f
        slot.player.setPlaybackSpeed(speed)
        if (resumeMs > 0L) slot.player.setMediaSource(source, resumeMs) else slot.player.setMediaSource(source)
        slot.player.prepare()
        applyPlayState()
    }

    /** Only the Short on screen plays. Everything else is stopped first, so just one player holds the audio focus. */
    private fun applyPlayState() {
        val url = items.getOrNull(current)?.url
        val shouldPlay = foreground && !paused
        slots.forEach { if (it.url != url || !shouldPlay) it.player.playWhenReady = false }
        slots.forEach { if (it.url == url && shouldPlay) it.player.playWhenReady = true }
    }

    private fun listenerFor(slot: Slot) = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            slot.firstFrame = true
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            slot.buffering = playbackState == Player.STATE_BUFFERING
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                slot.aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val url = slot.url ?: return
            val info = (infos[url] as? Load.Ready)?.data
            val next = slot.quality + 1
            if (info != null && slot.retries < 2 && next <= info.qualities.lastIndex) {
                slot.retries++
                start(slot, info, next)
            } else {
                YouTubeRepo.invalidate(url)
                slot.error = "This Short couldn't be played."
            }
        }
    }

    // ------------------------------------------------------------------ the endless list

    private fun loadMore() {
        if (loadingMore || current < items.size - PREFETCH_AHEAD) return
        loadingMore = true
        scope.launch {
            val fresh = fetchMore()
            fresh.forEach { idOf(it)?.let(ids::add) }
            items.addAll(fresh)
            loadingMore = false
            sync()
        }
    }

    private suspend fun fetchMore(): List<VideoItem> {
        val sources: List<suspend () -> List<VideoItem>> = listOf(
            { fromSequence() },
            { fromRelated() },
            { fromSearch() },
            { fromSearch() }
        )
        for (source in sources) {
            val fresh = attempt { source() }.getOrNull().orEmpty().keepNew()
            if (fresh.isNotEmpty()) return fresh
        }
        return emptyList()
    }

    /**
     * The platform's own sequence. A saved token continues it; when that gives nothing new (or the token
     * is refused) the sequence is started again from the last Short, so it heals itself.
     */
    private suspend fun fromSequence(): List<VideoItem> {
        val token = reelToken
        if (token != null) {
            val page = attempt { ShortsFeed.more(token) }.getOrNull()
            reelToken = page?.token
            val fresh = page?.items.orEmpty().keepNew()
            if (fresh.isNotEmpty()) return fresh
        }
        val seed = videoIdOf(items.last().url) ?: return emptyList()
        val page = ShortsFeed.after(seed)
        reelToken = page.token
        return page.items
    }

    private fun fromRelated(): List<VideoItem> =
        items.takeLast(6)
            .flatMap { (infos[it.url] as? Load.Ready)?.data?.related.orEmpty() }
            .filter { it.durationSec in 1..MAX_SHORT_SECONDS && !it.live }
            .map { it.asShort() }

    private suspend fun fromSearch(): List<VideoItem> {
        val previous = searchPage
        val page = if (previous != null) {
            YouTubeRepo.more(previous)
        } else {
            YouTubeRepo.search(QUERIES[queryIndex++ % QUERIES.size])
        }
        searchPage = if (page.next != null) page else null
        return page.items
            .filter { it.durationSec in 1..MAX_SHORT_SECONDS && !it.live }
            .map { it.asShort() }
    }

    private fun List<VideoItem>.keepNew(): List<VideoItem> =
        filter { idOf(it)?.let { id -> id !in ids } == true }.distinctBy { it.url }

    // ------------------------------------------------------------------ watch history

    /** After a short pause on a Short, reports the watch so it reaches the account's history and recommendations. */
    private fun restartWatch(item: VideoItem) {
        finishWatch()
        if (!Session.loggedIn) return
        val id = idOf(item) ?: return
        watchUrl = item.url
        watchJob = scope.launch {
            delay(1_500)
            val session = attempt { InnerTube.beginWatch(id) }.getOrNull() ?: return@launch
            watchSession = session
            while (isActive) {
                delay(10_000)
                val player = slots.firstOrNull { it.url == item.url }?.player ?: break
                if (player.isPlaying) {
                    attempt { session.progress(player.currentPosition / 1000.0, playing = true, final = false) }
                }
            }
        }
    }

    private fun finishWatch() {
        watchJob?.cancel()
        watchJob = null
        val session = watchSession ?: return
        watchSession = null
        val position = slots.firstOrNull { it.url == watchUrl }?.player?.currentPosition?.div(1000.0) ?: 0.0
        reportScope.launch { attempt { session.progress(position, playing = false, final = true) } }
    }

    private fun idOf(item: VideoItem): String? = videoIdOf(item.url)

    private fun VideoItem.asShort(): VideoItem =
        videoIdOf(url)?.let { copy(url = "https://www.youtube.com/shorts/$it") } ?: this

    private companion object {
        const val SLOTS = 3

        /** More Shorts are added to the list when the viewer is this close to its end. */
        const val PREFETCH_AHEAD = 6

        /** Streams are looked up for the Short on screen and this many after it. */
        const val INFO_AHEAD = 3

        /** Page data (likes, channel, subscription) is fetched for this many Shorts after the one on screen. */
        const val META_AHEAD = 2

        /** The Shorts further ahead wait this long, so a fast scroll doesn't look up every Short it passes. */
        const val FAR_DELAY_MS = 450L

        const val MAX_SHORT_SECONDS = 180L
        val QUERIES = listOf(
            "#shorts", "shorts funny", "shorts music", "shorts sports", "shorts cooking",
            "shorts gaming", "shorts science", "shorts travel", "shorts news", "shorts comedy"
        )
    }
}
