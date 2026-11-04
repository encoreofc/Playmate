package com.playmate.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.playmate.app.data.Account
import com.playmate.app.data.FeedPage
import com.playmate.app.data.FeedRepo
import com.playmate.app.data.InnerTube
import com.playmate.app.data.Load
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.Playing
import com.playmate.app.data.RecentSearches
import com.playmate.app.data.SeenStore
import com.playmate.app.data.SearchPage
import com.playmate.app.data.Session
import com.playmate.app.data.VideoItem
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

enum class Tab { Home, Search, You }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val recentStore = RecentSearches(app)
    private val seen = SeenStore(app)

    var tab by mutableStateOf(Tab.Home); private set

    var feed by mutableStateOf<Load<List<VideoItem>>>(Load.Loading); private set
    var feedToken by mutableStateOf<String?>(null); private set
    var feedNote by mutableStateOf<String?>(null); private set
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

    private var feedJob: Job? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var playJob: Job? = null
    private var historyJob: Job? = null
    private var watchJob: Job? = null
    private var seenSaveJob: Job? = null
    private var watch: InnerTube.WatchSession? = null
    private var loadingMoreFeed = false
    private var loadingMoreHistory = false
    private var feedGeneration = 0

    init {
        Session.init(app)
        signedIn = Session.loggedIn
        if (signedIn) loadAccount()
        loadFeed()
    }

    fun select(target: Tab) {
        tab = target
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
    }

    fun signOut() {
        Session.clear()
        signedIn = false
        account = null
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
        finishWatch()
        playerExpanded = true
        playing = Playing(item, Load.Loading)
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

    fun openUrl(url: String) {
        open(VideoItem(url, "", "", 0, -1, null, null))
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
    }

    private fun looksLikeYouTubeLink(text: String) =
        Regex("^https?://(www\\.|m\\.|music\\.)?(youtube\\.com|youtu\\.be)/\\S+$").matches(text)

    private companion object {
        const val MIN_FRESH = 6
    }
}
