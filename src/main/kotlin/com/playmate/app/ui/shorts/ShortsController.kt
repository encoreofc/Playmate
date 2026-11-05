package com.playmate.app.ui.shorts

import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Everything behind the Shorts screen except drawing it.
 *
 *  - three players in a ring: the Short on screen, the next one (already prepared, so a swipe
 *    shows a picture at once) and the previous one;
 *  - the endless list: YouTube's own Shorts sequence first, then related Shorts, then search;
 *  - watch-history reports for the signed-in account.
 *
 * Must be created and used on the main thread.
 */
class ShortsController(
    context: Context,
    private val scope: CoroutineScope,
    private val reportScope: CoroutineScope,
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
        var quality = -1
        var retries = 0
    }

    val items = mutableStateListOf<VideoItem>().apply { addAll(initial) }
    val infos = mutableStateMapOf<String, Load<PlayableInfo>>()

    var current by mutableIntStateOf(startPage.coerceIn(0, (initial.size - 1).coerceAtLeast(0)))
        private set
    var paused by mutableStateOf(false)
        private set
    var muted by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private val ids = HashSet<String>().apply { initial.forEach { idOf(it)?.let(::add) } }

    val slots: List<Slot> = List(SLOTS) {
        val player = createPlayer(context)
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

    fun slotFor(page: Int): Slot = slots[page.mod(SLOTS)]

    // ------------------------------------------------------------------ paging and playback

    /** The pager settled on [page]. */
    fun onPage(page: Int) {
        if (page !in items.indices) return
        current = page
        paused = false
        progress = 0f
        sync()
        loadMore()
        restartWatch(items[page])
    }

    fun togglePause() {
        paused = !paused
        applyPlayState()
    }

    fun toggleMute() {
        muted = !muted
        slots.forEach { it.player.volume = if (muted) 0f else 1f }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        applyPlayState()
    }

    fun retry(page: Int) {
        val item = items.getOrNull(page) ?: return
        infos.remove(item.url)
        slotFor(page).url = null
        sync()
    }

    /** Called about ten times a second by the screen; keeps the thin progress line moving. */
    fun tick() {
        val slot = slots.firstOrNull { it.url == items.getOrNull(current)?.url } ?: return
        val duration = slot.player.duration
        progress = if (duration > 0) (slot.player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    fun release() {
        finishWatch()
        slots.forEach { runCatching { it.player.release() } }
    }

    /** Gives the Short on screen and its two neighbours a player each, and plays only the one on screen. */
    private fun sync() {
        for (page in current - 1..current + 1) assign(page)
        val from = (current - 6).coerceAtLeast(0)
        val to = (current + 8).coerceAtMost(items.size)
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
        slot.player.stop()
        slot.player.clearMediaItems()
        scope.launch {
            val info = infoFor(item)
            if (slot.url != item.url) return@launch
            when (info) {
                is Load.Ready -> start(slot, info.data, defaultQualityIndex(info.data))
                is Load.Failed -> slot.error = info.message
                Load.Loading -> Unit
            }
        }
    }

    private suspend fun infoFor(item: VideoItem): Load<PlayableInfo> {
        val cached = infos[item.url]
        if (cached is Load.Ready) return cached
        val result: Load<PlayableInfo> = attempt { YouTubeRepo.load(item.url) }
            .fold({ Load.Ready(it) }, { Load.Failed(friendly(it)) })
        infos[item.url] = result
        return result
    }

    private fun start(slot: Slot, info: PlayableInfo, quality: Int) {
        val source = buildSource(info, quality)
        if (source == null) {
            slot.error = "No playable stream was found for this Short."
            return
        }
        slot.quality = quality
        slot.player.volume = if (muted) 0f else 1f
        slot.player.setMediaSource(source)
        slot.player.prepare()
        applyPlayState()
    }

    private fun applyPlayState() {
        val url = items.getOrNull(current)?.url
        slots.forEach { it.player.playWhenReady = foreground && !paused && it.url == url }
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
     * YouTube's own sequence. A saved token continues it; when that gives nothing new (or the token
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

    /** After a short pause on a Short, reports the watch to YouTube so it reaches history and recommendations. */
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
        const val PREFETCH_AHEAD = 4
        const val MAX_SHORT_SECONDS = 180L
        val QUERIES = listOf(
            "#shorts", "shorts funny", "shorts music", "shorts sports", "shorts cooking",
            "shorts gaming", "shorts science", "shorts travel", "shorts news", "shorts comedy"
        )
    }
}
