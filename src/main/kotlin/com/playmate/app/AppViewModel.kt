package com.playmate.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.playmate.app.data.Account
import com.playmate.app.data.ChannelInfo
import com.playmate.app.data.ChannelRepo
import com.playmate.app.data.CommentItem
import com.playmate.app.data.Engage
import com.playmate.app.data.FeedChip
import com.playmate.app.data.FeedPage
import com.playmate.app.data.FeedRepo
import com.playmate.app.data.InnerTube
import com.playmate.app.data.Load
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.Playing
import com.playmate.app.data.PlaylistOption
import com.playmate.app.data.Reaction
import com.playmate.app.data.RecentSearches
import com.playmate.app.data.SeenStore
import com.playmate.app.data.SearchPage
import com.playmate.app.data.Session
import com.playmate.app.data.SortOption
import com.playmate.app.data.VideoItem
import com.playmate.app.data.WatchMeta
import com.playmate.app.data.YouTubeRepo
import com.playmate.app.data.attempt
import com.playmate.app.data.friendly
import com.playmate.app.data.videoIdOf
import com.playmate.app.ui.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import com.playmate.app.data.NowPlayingStore
import com.playmate.app.ui.player.ActivePlayback
import com.playmate.app.ui.player.PlaybackRecovery
import com.playmate.app.ui.player.setVideoEnabled

enum class Tab { Home, Search, Downloads, You }

/** The channel page on screen. [tab] indexes [ChannelInfo.tabs]; [startTab] is the one that came with the first answer. */
data class ChannelState(
    val id: String,
    val seedName: String?,
    val seedAvatar: String?,
    val info: Load<ChannelInfo>,
    val tab: Int,
    val startTab: Int,
    val videos: Load<List<VideoItem>>,
    val token: String?,
    val firstPage: FeedPage? = null
)

/** The replies of one comment, as loaded so far. */
data class RepliesState(
    val items: List<CommentItem>,
    val next: String?,
    val loading: Boolean,
    val open: Boolean,
    val error: String? = null
)

/** Opens the Shorts screen: the Shorts to start with and which one to show first. A new object per opening. */
class ShortsLaunch(val items: List<VideoItem>, val start: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val recentStore = RecentSearches(app)
    private val seen = SeenStore(app)

    var tab by mutableStateOf(Tab.Home); private set

    var feed by mutableStateOf<Load<List<VideoItem>>>(Load.Loading); private set
    var feedToken by mutableStateOf<String?>(null); private set
    var feedNote by mutableStateOf<String?>(null); private set
    var feedChips by mutableStateOf<List<FeedChip>>(emptyList()); private set
    var selectedChip by mutableStateOf(0); private set
    var refreshing by mutableStateOf(false); private set

    var query by mutableStateOf(""); private set
    var editing by mutableStateOf(false); private set
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set
    var results by mutableStateOf<Load<SearchPage>?>(null); private set
    var loadingMore by mutableStateOf(false); private set
    var recents by mutableStateOf(recentStore.all()); private set

    var signedIn by mutableStateOf(false); private set
    var account by mutableStateOf<Account?>(null); private set
    var showLogin by mutableStateOf(false); private set
    var history by mutableStateOf<Load<List<VideoItem>>>(Load.Loading); private set
    var historyNote by mutableStateOf<String?>(null); private set
    var historyToken by mutableStateOf<String?>(null); private set
    var historyRefreshing by mutableStateOf(false); private set
    var trackingNote by mutableStateOf<String?>(null); private set
    var trackingInfo by mutableStateOf<String?>(null); private set

    var playing by mutableStateOf<Playing?>(null); private set
    var playerExpanded by mutableStateOf(true); private set

    /**
     * True for the moment after the app was reopened while the sound was still going in the
     * background service: the player screen then picks the running playback up instead of restarting it.
     */
    var adoptPlayback = false

    /** What YouTube knows about the video on screen: reaction, channel, subscription, comments. */
    var meta by mutableStateOf<WatchMeta?>(null); private set

    /** Subscription state the app knows about (from YouTube, or from the user's own taps). */
    val subscriptions = mutableStateMapOf<String, Boolean>()

    var toast by mutableStateOf<String?>(null); private set

    var comments by mutableStateOf<Load<List<CommentItem>>?>(null); private set
    var commentsNext by mutableStateOf<String?>(null); private set
    var commentsTotal by mutableStateOf<String?>(null); private set
    var commentSorts by mutableStateOf<List<SortOption>>(emptyList()); private set
    var postingComment by mutableStateOf(false); private set
    val replies = mutableStateMapOf<String, RepliesState>()

    var playlists by mutableStateOf<Load<List<PlaylistOption>>?>(null); private set

    var channel by mutableStateOf<ChannelState?>(null); private set

    /** Set while the full-screen Shorts player is open. */
    var shorts by mutableStateOf<ShortsLaunch?>(null); private set
    private var shortFocus: String? = null

    private var feedJob: Job? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var playJob: Job? = null
    private var historyJob: Job? = null
    private var watchJob: Job? = null
    private var seenSaveJob: Job? = null
    private var metaJob: Job? = null
    private var commentsJob: Job? = null
    private var playlistsJob: Job? = null
    private var channelJob: Job? = null
    private var channelTabJob: Job? = null
    private var commentsFor: String? = null
    private var commentsGeneration = 0
    private var createParams: String? = null
    private var loadingMoreComments = false
    private var loadingMoreChannel = false
    private var watch: InnerTube.WatchSession? = null
    private var loadingMoreFeed = false
    private var loadingMoreHistory = false
    private var feedGeneration = 0
    private var defaultChip = 0

    init {
        Session.init(app)
        signedIn = Session.loggedIn
        if (signedIn) loadAccount()
        loadFeed()
        // Posted, so every property of this class is initialised before the playback is picked up.
        viewModelScope.launch(Dispatchers.Main) { restoreRunningPlayback() }
    }

    /**
     * The app was swiped away but the service kept playing (or the system brought the sound back
     * from the lock screen): show the mini player again for what is still playing.
     */
    private fun restoreRunningPlayback() {
        val player = PlayerHolder.peek() ?: return
        if (player.mediaItemCount == 0) return
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return
        val snapshot = NowPlayingStore.read(getApplication()) ?: return
        if (player.currentMediaItem?.mediaId != snapshot.url) return
        val seed = VideoItem(
            url = snapshot.url,
            title = snapshot.title,
            channel = snapshot.channel,
            durationSec = snapshot.durationSec,
            views = 0L,
            thumb = snapshot.thumb,
            uploaded = null,
            live = snapshot.live
        )
        adoptPlayback = true
        open(seed)
        playerExpanded = false
    }

    fun select(target: Tab) {
        tab = target
        closeChannel()
    }

    // ---------------------------------------------------------------- home feed

    fun loadFeed(force: Boolean = false) {
        if (!force && feed is Load.Ready) return
        feedJob?.cancel()
        feedGeneration++
        feedJob = viewModelScope.launch {
            if (force) refreshing = true else feed = Load.Loading
            feedNote = null
            var token: String? = null
            var why: String? = null
            var accountEmpty = false

            // Signed in: the account's own recommendations. Otherwise (or if that fails): trending.
            val personal: FeedPage? = if (Session.loggedIn) {
                attempt { FeedRepo.home() }
                    .onFailure { why = it.message }
                    .getOrNull()
                    ?.also {
                        if (it.items.isEmpty()) {
                            why = it.note
                            accountEmpty = it.accountEmpty
                        }
                    }
                    ?.takeIf { it.items.isNotEmpty() }
            } else null

            feedChips = personal?.chips.orEmpty()
            defaultChip = feedChips.indexOfFirst { it.selected }.coerceAtLeast(0)
            selectedChip = defaultChip

            val result = if (personal != null) {
                // Videos not shown before come first; if the page is mostly repeats, more pages are pulled.
                val page = freshen(personal, known = emptySet(), dropSeen = false)
                token = page.token
                Result.success(page.items)
            } else {
                if (Session.loggedIn) {
                    feedNote = if (accountEmpty) {
                        "Your account has no watch history yet, so there is nothing personal to recommend. " +
                            "Watch a few videos and this feed will build up. Showing trending for now."
                    } else {
                        "Couldn't load your personal feed (${why ?: "empty answer"}). Showing trending instead."
                    }
                }
                attempt { YouTubeRepo.trending() }
            }

            feedToken = token
            result
                .onSuccess {
                    feed = if (it.isEmpty()) Load.Failed("Nothing to show right now.") else Load.Ready(it)
                }
                .onFailure {
                    if (!(force && feed is Load.Ready)) feed = Load.Failed(friendly(it))
                }
            refreshing = false
        }
    }

    /** Tap on one of YouTube's own topic chips: the feed becomes that topic. The first chip is the normal feed. */
    fun selectChip(index: Int) {
        val chip = feedChips.getOrNull(index) ?: return
        if (index == selectedChip) return
        val chipToken = chip.token
        if (index == defaultChip || chipToken == null) {
            loadFeed(force = true)
            return
        }
        feedJob?.cancel()
        feedGeneration++
        selectedChip = index
        feedNote = null
        feedToken = null
        feed = Load.Loading
        feedJob = viewModelScope.launch {
            attempt { FeedRepo.more(chipToken) }
                .onSuccess { page ->
                    feedToken = page.token
                    feed = if (page.items.isEmpty()) Load.Failed("Nothing to show for \"${chip.label}\" right now.")
                    else Load.Ready(page.items)
                }
                .onFailure { feed = Load.Failed(friendly(it)) }
        }
    }

    fun loadMoreFeed() {
        val token = feedToken ?: return
        val current = (feed as? Load.Ready)?.data ?: return
        if (loadingMoreFeed) return
        loadingMoreFeed = true
        val generation = feedGeneration
        viewModelScope.launch {
            attempt {
                freshen(FeedRepo.more(token), known = current.map { it.url }.toHashSet(), dropSeen = true)
            }
                .onSuccess { page ->
                    // A refresh that started meanwhile owns the list now.
                    if (generation == feedGeneration) {
                        val latest = (feed as? Load.Ready)?.data ?: current
                        val known = latest.map { it.url }.toHashSet()
                        feed = Load.Ready(latest + page.items.filter { it.url !in known })
                        feedToken = page.token
                    }
                }
                .onFailure { if (generation == feedGeneration) feedToken = null }
            loadingMoreFeed = false
        }
    }

    /**
     * Orders a feed page so videos not shown before come first. When the page holds fewer than a
     * handful of new videos, the next pages are fetched too (up to three). With [dropSeen] the
     * repeats are left out as long as there is anything new.
     */
    private suspend fun freshen(first: FeedPage, known: Set<String>, dropSeen: Boolean): FeedPage {
        val all = LinkedHashMap<String, VideoItem>()
        fun take(items: List<VideoItem>) {
            for (v in items) if (v.url !in known && v.url !in all) all[v.url] = v
        }
        take(first.items)
        var token = first.token
        var hops = 0
        while (all.values.count { !seen.has(it.url) } < MIN_FRESH && token != null && hops < 3) {
            val next = attempt { FeedRepo.more(token!!) }.getOrNull() ?: break
            take(next.items)
            token = next.token
            hops++
        }
        val unseen = all.values.filter { !seen.has(it.url) }
        val repeats = all.values.filter { seen.has(it.url) }
        val items = if (dropSeen && unseen.isNotEmpty()) unseen else unseen + repeats
        return FeedPage(items, token)
    }

    /** A video card was actually shown on Home: remember it, so the next refresh leads with other videos. */
    fun onImpression(url: String) {
        if (!seen.add(url)) return
        seenSaveJob?.cancel()
        seenSaveJob = viewModelScope.launch {
            delay(2_000)
            withContext(Dispatchers.IO) { seen.save() }
        }
    }

    // ---------------------------------------------------------------- account

    fun openLogin() {
        showLogin = true
    }

    fun cancelLogin() {
        showLogin = false
    }

    fun onSignedIn(cookie: String) {
        Session.save(cookie)
        showLogin = false
        signedIn = Session.loggedIn
        if (!signedIn) return
        loadAccount()
        history = Load.Loading
        feed = Load.Loading
        loadFeed()
        // The video on screen: ask again, now that its like / subscribe state belongs to the account.
        playing?.let { p -> videoIdOf(p.seed.url)?.let { loadMeta(it, p.seed.url) } }
    }

    fun signOut() {
        Session.clear()
        signedIn = false
        account = null
        subscriptions.clear()
        shortMetas.clear()
        meta = meta?.copy(reaction = Reaction.None, subscribed = null)
        finishWatch()
        historyNote = null
        historyToken = null
        trackingNote = null
        trackingInfo = null
        history = Load.Ready(emptyList())
        feed = Load.Loading
        loadFeed()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            account = attempt { InnerTube.account() }.getOrNull()
        }
    }

    /** [force]: a pull-to-refresh. The list stays on screen while the new one loads. */
    fun loadHistory(force: Boolean = false) {
        if (!Session.loggedIn) return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            if (force) historyRefreshing = true
            else if (history !is Load.Ready) history = Load.Loading
            attempt { FeedRepo.history() }
                .onSuccess {
                    history = Load.Ready(it.items)
                    historyToken = it.token
                    historyNote = if (it.items.isEmpty()) it.note else null
                }
                .onFailure {
                    if (history !is Load.Ready) {
                        history = Load.Failed(friendly(it) + (it.message?.let { m -> " ($m)" } ?: ""))
                    }
                }
            historyRefreshing = false
        }
    }

    fun loadMoreHistory() {
        val token = historyToken ?: return
        val current = (history as? Load.Ready)?.data ?: return
        if (loadingMoreHistory || historyRefreshing) return
        loadingMoreHistory = true
        viewModelScope.launch {
            attempt { FeedRepo.more(token) }
                .onSuccess { page ->
                    val latest = (history as? Load.Ready)?.data ?: current
                    val known = latest.map { it.url }.toHashSet()
                    history = Load.Ready(latest + page.items.filter { it.url !in known })
                    historyToken = page.token
                }
                .onFailure { historyToken = null }
            loadingMoreHistory = false
        }
    }

    // ---------------------------------------------------------------- search

    fun onQueryChange(value: String) {
        query = value
        editing = true
        suggestJob?.cancel()
        if (value.trim().length < 2) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(200)
            suggestions = attempt { YouTubeRepo.suggestions(value.trim()) }
                .getOrDefault(emptyList())
                .take(8)
        }
    }

    fun startEditing() {
        editing = true
    }

    fun submit(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        if (looksLikeYouTubeLink(q)) {
            openUrl(q)
            return
        }
        query = q
        editing = false
        suggestions = emptyList()
        tab = Tab.Search
        recentStore.add(q)
        recents = recentStore.all()

        suggestJob?.cancel()
        searchJob?.cancel()
        results = Load.Loading
        searchJob = viewModelScope.launch {
            results = attempt { YouTubeRepo.search(q) }
                .fold({ Load.Ready(it) }, { Load.Failed(friendly(it)) })
        }
    }

    fun loadMore() {
        val page = when (val r = results) {
            is Load.Ready -> r.data
            else -> return
        }
        if (loadingMore || page.next == null) return
        loadingMore = true
        viewModelScope.launch {
            attempt { YouTubeRepo.more(page) }.onSuccess { results = Load.Ready(it) }
            loadingMore = false
        }
    }

    fun removeRecent(text: String) {
        recentStore.remove(text)
        recents = recentStore.all()
    }

    fun clearRecents() {
        recentStore.clear()
        recents = emptyList()
    }

    // ---------------------------------------------------------------- player

    fun open(item: VideoItem) {
        if (item.isShort) {
            openShorts(listOf(item), 0)
            return
        }
        finishWatch()
        playerExpanded = true
        playing = Playing(item, Load.Loading)
        resetWatchExtras()
        videoIdOf(item.url)?.let { id ->
            meta = WatchMeta.blank(id)
            loadMeta(id, item.url)
        }
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val outcome: Load<PlayableInfo> = attempt { YouTubeRepo.load(item.url) }
                .fold({ Load.Ready(it) }, { Load.Failed(friendly(it)) })
            playing = playing?.takeIf { it.seed.url == item.url }?.copy(info = outcome)

            // Signed in: tell YouTube it is being watched, so it shows up in history and recommendations.
            if (outcome is Load.Ready && Session.loggedIn) {
                videoIdOf(item.url)?.let { beginWatch(it, item.url) }
            }
        }
    }

    /**
     * Reports the watch to YouTube: a start report now, then the real playback position every
     * ten seconds while the video plays (also in the mini player and in the background).
     */
    private fun beginWatch(videoId: String, url: String) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            val session = attempt { InnerTube.beginWatch(videoId) }.getOrElse {
                trackingNote = it.message ?: "unknown error"
                trackingInfo = null
                return@launch
            }
            watch = session
            trackingNote = null
            trackingInfo = "Watch history: reporting to YouTube (via ${session.source})."

            val player = PlayerHolder.get(getApplication())
            delay(5_000)
            while (isActive && playing?.seed?.url == url) {
                val ended = player.playbackState == Player.STATE_ENDED
                if (player.isPlaying || ended) {
                    val position = player.currentPosition / 1000.0
                    attempt { session.progress(position, playing = !ended, final = ended) }
                        .onFailure { trackingNote = it.message ?: "unknown error" }
                }
                if (ended) {
                    watch = null
                    break
                }
                delay(10_000)
            }
        }
    }

    /** Sends the closing report for the video that was being watched. */
    private fun finishWatch() {
        watchJob?.cancel()
        watchJob = null
        val session = watch ?: return
        watch = null
        val position = PlayerHolder.get(getApplication()).currentPosition / 1000.0
        viewModelScope.launch {
            attempt { session.progress(position, playing = false, final = true) }
        }
    }

    // ---------------------------------------------------------------- Shorts

    /** Opens the full-screen Shorts player at [index] of [items]; it keeps going with more Shorts after them. */
    fun openShorts(items: List<VideoItem>, index: Int = 0) {
        if (items.isEmpty()) return
        // A normal video that is playing steps aside (the Shorts screen pauses it).
        if (playing != null) playerExpanded = false
        shortFocus = null
        resetWatchExtras()
        shorts = ShortsLaunch(items, index.coerceIn(0, items.lastIndex))
    }

    /** Page data (like count, channel, subscription) of Shorts that are about to come on screen. */
    private val shortMetas = mutableStateMapOf<String, WatchMeta>()
    private val shortMetaLoading = HashSet<String>()

    /** The page data of a Short that was fetched ahead of time, or null while it is still on its way. */
    fun cachedMeta(videoId: String?): WatchMeta? = videoId?.let { shortMetas[it] }

    /**
     * Fetches the page data of a Short before it is on screen, so its like count, channel and
     * Subscribe button are already there when the viewer swipes to it.
     */
    fun preloadMeta(videoId: String) {
        if (shortMetas.containsKey(videoId) || !shortMetaLoading.add(videoId)) return
        viewModelScope.launch {
            attempt { Engage.watchMeta(videoId) }.onSuccess { m ->
                shortMetas[videoId] = m
                if (shortMetas.size > SHORT_META_KEEP) {
                    shortMetas.keys.firstOrNull { it != shortFocus && it != videoId }?.let { shortMetas.remove(it) }
                }
                val channelId = m.channelId
                val subscribed = m.subscribed
                if (channelId != null && subscribed != null && !subscriptions.containsKey(channelId)) {
                    subscriptions[channelId] = subscribed
                }
            }
            shortMetaLoading.remove(videoId)
        }
    }

    /** Keeps the fetched-ahead copy of the Short on screen in step with a like or dislike. */
    private fun syncShortMeta() {
        val current = meta ?: return
        if (shortMetas.containsKey(current.videoId)) shortMetas[current.videoId] = current
    }

    /** The Short that is now on screen: loads its reactions, channel and comment entry. */
    fun focusShort(item: VideoItem) {
        val id = videoIdOf(item.url) ?: return
        if (shortFocus == id) return
        shortFocus = id
        resetWatchExtras()
        val ready = shortMetas[id]
        if (ready != null) {
            // Fetched ahead of time: nothing to wait for.
            meta = ready
            commentsTotal = ready.commentCount
            val channelId = ready.channelId
            val subscribed = ready.subscribed
            if (channelId != null && subscribed != null && !subscriptions.containsKey(channelId)) {
                subscriptions[channelId] = subscribed
            }
            return
        }
        meta = WatchMeta.blank(id)
        metaJob = viewModelScope.launch {
            attempt { Engage.watchMeta(id) }.onSuccess { m ->
                if (shortFocus != id) return@onSuccess
                meta = m
                commentsTotal = commentsTotal ?: m.commentCount
                val channelId = m.channelId
                val subscribed = m.subscribed
                if (channelId != null && subscribed != null) subscriptions[channelId] = subscribed
            }
        }
    }

    fun closeShorts() {
        shorts = null
        shortFocus = null
        shortMetas.clear()
        shortMetaLoading.clear()
        resetWatchExtras()
        // Back to the normal video that was playing before: its page data comes back too.
        playing?.seed?.let { seed ->
            videoIdOf(seed.url)?.let { id ->
                meta = WatchMeta.blank(id)
                loadMeta(id, seed.url)
            }
        }
    }

    fun openUrl(url: String) {
        open(VideoItem(url, "", "", 0, -1, null, null))
    }

    /** Plays a video that was downloaded inside the app. Needs no internet. */
    fun openOffline(video: com.playmate.app.data.OfflineVideo) {
        finishWatch()
        playJob?.cancel()
        resetWatchExtras()
        playerExpanded = true
        val seed = VideoItem(video.url, video.title, video.channel, video.durationMs / 1000, -1, null, null)
        playing = Playing(seed, Load.Ready(video.playable(getApplication<Application>())))
    }

    fun retryPlayer() {
        playing?.seed?.let { open(it) }
    }

    /** Back / down-arrow: the video keeps playing in a mini player above the tab bar. */
    fun minimizePlayer() {
        playerExpanded = false
    }

    fun expandPlayer() {
        playerExpanded = true
    }

    fun closePlayer() {
        finishWatch()
        playJob?.cancel()
        playing = null
        playerExpanded = true
        resetWatchExtras()
        stopBackgroundPlayback()
    }

    /** The one place where playback really ends: the person closed the player. */
    private fun stopBackgroundPlayback() {
        val app = getApplication<Application>()
        adoptPlayback = false
        PlayerHolder.peek()?.let { player ->
            player.stop()
            player.clearMediaItems()
            player.setVideoEnabled(true)
        }
        PlaybackRecovery.reset()
        ActivePlayback.clear()
        NowPlayingStore.clear(app)
        runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
    }

    // ---------------------------------------------------------------- the video's own page: reactions, channel, save

    fun consumeToast() {
        toast = null
    }

    /** True when signed in. Otherwise tells the user and opens the sign-in page. */
    fun ensureSignedIn(what: String): Boolean {
        if (Session.loggedIn) return true
        toast = "Sign in to $what."
        showLogin = true
        return false
    }

    private fun resetWatchExtras() {
        metaJob?.cancel()
        commentsJob?.cancel()
        playlistsJob?.cancel()
        commentsGeneration++
        meta = null
        comments = null
        commentsNext = null
        commentsTotal = null
        commentSorts = emptyList()
        commentsFor = null
        createParams = null
        loadingMoreComments = false
        postingComment = false
        replies.clear()
        playlists = null
    }

    private fun loadMeta(videoId: String, url: String) {
        metaJob?.cancel()
        metaJob = viewModelScope.launch {
            attempt { Engage.watchMeta(videoId) }.onSuccess { m ->
                if (playing?.seed?.url != url) return@onSuccess
                meta = m
                commentsTotal = commentsTotal ?: m.commentCount
                val channelId = m.channelId
                val subscribed = m.subscribed
                if (channelId != null && subscribed != null) subscriptions[channelId] = subscribed
            }
        }
    }

    /** Like or dislike. Tapping the one that is already on takes it back. */
    fun react(target: Reaction) {
        val current = meta ?: return
        if (!ensureSignedIn("like or dislike videos")) return
        val next = if (current.reaction == target) Reaction.None else target
        meta = current.copy(reaction = next, likeCount = bumpedCount(current.likeCount, current.reaction, next))
        syncShortMeta()
        viewModelScope.launch {
            attempt {
                when (next) {
                    Reaction.Liked -> Engage.like(current.videoId)
                    Reaction.Disliked -> Engage.dislike(current.videoId)
                    Reaction.None -> Engage.clearReaction(current.videoId)
                }
            }.onFailure {
                if (meta?.videoId == current.videoId) {
                    meta = current
                    syncShortMeta()
                }
                toast = "Couldn't update your reaction: ${it.message ?: "unknown error"}"
            }
        }
    }

    /** Only a plain number can be bumped; "1.2K" stays as YouTube printed it. */
    private fun bumpedCount(text: String?, from: Reaction, to: Reaction): String? {
        val n = text?.replace(",", "")?.toLongOrNull() ?: return text
        val delta = (if (to == Reaction.Liked) 1 else 0) - (if (from == Reaction.Liked) 1 else 0)
        return (n + delta).coerceAtLeast(0).toString()
    }

    fun toggleSubscribe(channelId: String?, currentlySubscribed: Boolean) {
        if (channelId.isNullOrBlank()) {
            toast = "This channel couldn't be found."
            return
        }
        if (!ensureSignedIn("subscribe")) return
        val target = !currentlySubscribed
        subscriptions[channelId] = target
        viewModelScope.launch {
            attempt { if (target) Engage.subscribe(channelId) else Engage.unsubscribe(channelId) }
                .onFailure {
                    subscriptions[channelId] = currentlySubscribed
                    toast = "Couldn't ${if (target) "subscribe" else "unsubscribe"}: ${it.message ?: "unknown error"}"
                }
        }
    }

    // ---- save to playlist

    fun loadPlaylists() {
        val id = meta?.videoId ?: return
        playlistsJob?.cancel()
        playlists = Load.Loading
        playlistsJob = viewModelScope.launch {
            playlists = attempt { Engage.playlistsFor(id) }
                .fold({ Load.Ready(it) }, { Load.Failed(it.message ?: "Couldn't load your playlists.") })
        }
    }

    fun togglePlaylist(option: PlaylistOption) {
        val id = meta?.videoId ?: return
        val list = (playlists as? Load.Ready)?.data ?: return
        val add = !option.contains
        playlists = Load.Ready(list.map { if (it.id == option.id) PlaylistOption(it.id, it.title, add) else it })
        viewModelScope.launch {
            attempt { Engage.setInPlaylist(option.id, id, add) }.onFailure {
                val latest = (playlists as? Load.Ready)?.data
                if (latest != null) {
                    playlists = Load.Ready(
                        latest.map { p -> if (p.id == option.id) PlaylistOption(p.id, p.title, !add) else p }
                    )
                }
                toast = "Couldn't ${if (add) "save to" else "remove from"} ${option.title}: ${it.message ?: "unknown error"}"
            }
        }
    }

    // ---- comments

    /** Loads the first page of comments ([token] picks a sort order). Does nothing if they are already loaded. */
    fun loadComments(token: String? = null, force: Boolean = false) {
        val videoId = meta?.videoId ?: return
        if (!force && token == null && commentsFor == videoId && comments is Load.Ready) return
        commentsJob?.cancel()
        val generation = ++commentsGeneration
        commentsFor = videoId
        comments = Load.Loading
        commentsNext = null
        replies.clear()
        commentsJob = viewModelScope.launch {
            val start = token ?: run {
                metaJob?.join()
                meta?.takeIf { it.videoId == videoId }?.commentsToken
            }
            if (start == null) {
                if (generation == commentsGeneration) {
                    comments = Load.Failed("Comments are turned off or unavailable for this video.")
                }
                return@launch
            }
            attempt { Engage.comments(start) }
                .onSuccess { page ->
                    if (generation != commentsGeneration) return@onSuccess
                    comments = Load.Ready(page.items)
                    commentsNext = page.next
                    if (page.total != null) commentsTotal = page.total
                    if (page.sorts.isNotEmpty()) commentSorts = page.sorts
                    if (page.createParams != null) createParams = page.createParams
                }
                .onFailure {
                    if (generation == commentsGeneration) {
                        comments = Load.Failed(it.message ?: "Couldn't load comments.")
                    }
                }
        }
    }

    fun loadMoreComments() {
        val token = commentsNext ?: return
        if (loadingMoreComments || comments !is Load.Ready) return
        loadingMoreComments = true
        val generation = commentsGeneration
        viewModelScope.launch {
            attempt { Engage.comments(token) }
                .onSuccess { page ->
                    if (generation == commentsGeneration) {
                        val latest = (comments as? Load.Ready)?.data.orEmpty()
                        val known = latest.map { it.id }.toHashSet()
                        comments = Load.Ready(latest + page.items.filter { it.id !in known })
                        commentsNext = page.next
                    }
                }
                .onFailure { if (generation == commentsGeneration) commentsNext = null }
            loadingMoreComments = false
        }
    }

    fun toggleReplies(comment: CommentItem) {
        val token = comment.repliesToken ?: return
        val current = replies[comment.id]
        when {
            current != null && current.open -> replies[comment.id] = current.copy(open = false)
            current != null && current.items.isNotEmpty() -> replies[comment.id] = current.copy(open = true)
            else -> fetchReplies(comment.id, token, RepliesState(emptyList(), null, loading = true, open = true))
        }
    }

    fun loadMoreReplies(comment: CommentItem) {
        val current = replies[comment.id] ?: return
        val token = current.next ?: return
        if (current.loading) return
        fetchReplies(comment.id, token, current.copy(loading = true, error = null))
    }

    private fun fetchReplies(commentId: String, token: String, start: RepliesState) {
        replies[commentId] = start
        val generation = commentsGeneration
        viewModelScope.launch {
            attempt { Engage.comments(token, replies = true) }
                .onSuccess { page ->
                    if (generation != commentsGeneration) return@onSuccess
                    val current = replies[commentId] ?: start
                    val known = current.items.map { it.id }.toHashSet()
                    replies[commentId] = current.copy(
                        items = current.items + page.items.filter { it.id !in known },
                        next = page.next,
                        loading = false
                    )
                }
                .onFailure {
                    if (generation != commentsGeneration) return@onFailure
                    val current = replies[commentId] ?: start
                    replies[commentId] = current.copy(loading = false, error = it.message ?: "Couldn't load replies.")
                }
        }
    }

    fun postComment(text: String, onPosted: () -> Unit) {
        val body = text.trim()
        if (body.isEmpty() || postingComment) return
        if (!ensureSignedIn("comment")) return
        val params = createParams ?: run {
            toast = "Commenting isn't available on this video."
            return
        }
        postingComment = true
        val generation = commentsGeneration
        viewModelScope.launch {
            attempt { Engage.postComment(params, body) }
                .onSuccess {
                    if (generation == commentsGeneration) {
                        val mine = CommentItem(
                            id = "mine-${System.nanoTime()}",
                            author = account?.handle ?: account?.name ?: "You",
                            avatar = account?.avatar,
                            text = body,
                            published = "just now",
                            likes = null,
                            replies = null,
                            repliesToken = null
                        )
                        comments = Load.Ready(listOf(mine) + (comments as? Load.Ready)?.data.orEmpty())
                    }
                    onPosted()
                }
                .onFailure { toast = "Couldn't post your comment: ${it.message ?: "unknown error"}" }
            postingComment = false
        }
    }

    // ---------------------------------------------------------------- channel page

    /** Opens a channel. A playing video moves to the mini player and keeps playing. */
    fun openChannel(id: String?, name: String? = null, avatar: String? = null) {
        if (id.isNullOrBlank()) {
            toast = "This channel couldn't be found."
            return
        }
        if (playing != null) playerExpanded = false
        channelTabJob?.cancel()
        channel = ChannelState(id, name, avatar, Load.Loading, 0, 0, Load.Loading, null)
        channelJob?.cancel()
        channelJob = viewModelScope.launch {
            attempt { ChannelRepo.open(id) }
                .onSuccess { data ->
                    val start = data.info.tabs.indexOfFirst { it.selected }.coerceAtLeast(0)
                    channel = channel?.takeIf { it.id == id }?.copy(
                        info = Load.Ready(data.info),
                        tab = start,
                        startTab = start,
                        videos = Load.Ready(withChannelName(data.page.items, data.info.name)),
                        token = data.page.token,
                        firstPage = data.page
                    )
                }
                .onFailure {
                    val message = friendly(it) + (it.message?.let { m -> " ($m)" } ?: "")
                    channel = channel?.takeIf { it.id == id }
                        ?.copy(info = Load.Failed(message), videos = Load.Failed(message))
                }
        }
    }

    fun retryChannel() {
        val state = channel ?: return
        openChannel(state.id, state.seedName, state.seedAvatar)
    }

    fun closeChannel() {
        channelJob?.cancel()
        channelTabJob?.cancel()
        channel = null
        loadingMoreChannel = false
    }

    fun selectChannelTab(index: Int) {
        val state = channel ?: return
        val info = (state.info as? Load.Ready)?.data ?: return
        if (index == state.tab || index !in info.tabs.indices) return
        channelTabJob?.cancel()
        loadingMoreChannel = false

        val first = state.firstPage
        if (index == state.startTab && first != null) {
            channel = state.copy(
                tab = index,
                videos = Load.Ready(withChannelName(first.items, info.name)),
                token = first.token
            )
            return
        }
        val params = info.tabs[index].params
        if (params == null) {
            channel = state.copy(tab = index, videos = Load.Failed("This tab isn't available."), token = null)
            return
        }
        channel = state.copy(tab = index, videos = Load.Loading, token = null)
        channelTabJob = viewModelScope.launch {
            attempt { ChannelRepo.tab(state.id, params) }
                .onSuccess { page ->
                    val now = channel
                    if (now != null && now.id == state.id && now.tab == index) {
                        channel = now.copy(
                            videos = Load.Ready(withChannelName(page.items, info.name)),
                            token = page.token
                        )
                    }
                }
                .onFailure {
                    val now = channel
                    if (now != null && now.id == state.id && now.tab == index) {
                        channel = now.copy(
                            videos = Load.Failed(friendly(it) + (it.message?.let { m -> " ($m)" } ?: "")),
                            token = null
                        )
                    }
                }
        }
    }

    fun loadMoreChannel() {
        val state = channel ?: return
        val token = state.token ?: return
        val current = (state.videos as? Load.Ready)?.data ?: return
        if (loadingMoreChannel) return
        loadingMoreChannel = true
        viewModelScope.launch {
            attempt { FeedRepo.more(token) }
                .onSuccess { page ->
                    val now = channel
                    if (now != null && now.id == state.id && now.tab == state.tab) {
                        val have = (now.videos as? Load.Ready)?.data ?: current
                        val known = have.map { it.url }.toHashSet()
                        val name = (now.info as? Load.Ready)?.data?.name.orEmpty()
                        channel = now.copy(
                            videos = Load.Ready(have + withChannelName(page.items, name).filter { it.url !in known }),
                            token = page.token
                        )
                    }
                }
                .onFailure {
                    val now = channel
                    if (now != null && now.id == state.id && now.tab == state.tab) channel = now.copy(token = null)
                }
            loadingMoreChannel = false
        }
    }

    /** Videos on a channel's own page carry no channel name; fill it in. */
    private fun withChannelName(items: List<VideoItem>, name: String): List<VideoItem> =
        if (name.isBlank()) items else items.map { if (it.channel.isBlank()) it.copy(channel = name) else it }

    private fun looksLikeYouTubeLink(text: String) =
        Regex("^https?://(www\\.|m\\.|music\\.)?(youtube\\.com|youtu\\.be)/\\S+$").matches(text)

    private companion object {
        const val MIN_FRESH = 6

        /** How many fetched-ahead Short pages are kept while the Shorts screen is open. */
        const val SHORT_META_KEEP = 24
    }
}
