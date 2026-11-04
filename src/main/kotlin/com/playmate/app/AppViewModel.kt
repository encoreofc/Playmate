package com.playmate.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playmate.app.data.Account
import com.playmate.app.data.FeedPage
import com.playmate.app.data.FeedRepo
import com.playmate.app.data.InnerTube
import com.playmate.app.data.Load
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.Playing
import com.playmate.app.data.RecentSearches
import com.playmate.app.data.SearchPage
import com.playmate.app.data.Session
import com.playmate.app.data.VideoItem
import com.playmate.app.data.YouTubeRepo
import com.playmate.app.data.attempt
import com.playmate.app.data.friendly
import com.playmate.app.data.videoIdOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab { Home, Search, You }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val recentStore = RecentSearches(app)

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
    var trackingNote by mutableStateOf<String?>(null); private set

    var playing by mutableStateOf<Playing?>(null); private set
    var playerExpanded by mutableStateOf(true); private set

    private var feedJob: Job? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var playJob: Job? = null
    private var historyJob: Job? = null
    private var loadingMoreFeed = false

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
        feedJob = viewModelScope.launch {
            if (force) refreshing = true else feed = Load.Loading
            feedNote = null
            var token: String? = null
            var why: String? = null

            // Signed in: the account's own recommendations. Otherwise (or if that fails): trending.
            val personal: FeedPage? = if (Session.loggedIn) {
                attempt { FeedRepo.home() }
                    .onFailure { why = it.message }
                    .getOrNull()
                    ?.also { if (it.items.isEmpty()) why = it.note }
                    ?.takeIf { it.items.isNotEmpty() }
            } else null

            val result = if (personal != null) {
                token = personal.token
                Result.success(personal.items)
            } else {
                if (Session.loggedIn) {
                    feedNote = "Couldn't load your personal feed (${why ?: "empty answer"}). Showing trending instead."
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
        viewModelScope.launch {
            attempt { FeedRepo.more(token) }
                .onSuccess { page ->
                    val known = current.map { it.url }.toHashSet()
                    feed = Load.Ready(current + page.items.filter { it.url !in known })
                    feedToken = page.token
                }
                .onFailure { feedToken = null }
            loadingMoreFeed = false
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
        historyNote = null
        trackingNote = null
        history = Load.Ready(emptyList())
        feed = Load.Loading
        loadFeed()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            account = attempt { InnerTube.account() }.getOrNull()
        }
    }

    fun loadHistory() {
        if (!Session.loggedIn) return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            if (history !is Load.Ready) history = Load.Loading
            attempt { FeedRepo.history() }
                .onSuccess {
                    history = Load.Ready(it.items)
                    historyNote = if (it.items.isEmpty()) it.note else null
                }
                .onFailure {
                    if (history !is Load.Ready) {
                        history = Load.Failed(friendly(it) + (it.message?.let { m -> " ($m)" } ?: ""))
                    }
                }
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
        playerExpanded = true
        playing = Playing(item, Load.Loading)
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val outcome: Load<PlayableInfo> = attempt { YouTubeRepo.load(item.url) }
                .fold({ Load.Ready(it) }, { Load.Failed(friendly(it)) })
            playing = playing?.takeIf { it.seed.url == item.url }?.copy(info = outcome)

            // Signed in: tell YouTube it was watched, so it shows up in history and recommendations.
            if (outcome is Load.Ready && Session.loggedIn) {
                videoIdOf(item.url)?.let { id ->
                    attempt { InnerTube.registerPlayback(id) }
                        .onSuccess { trackingNote = null }
                        .onFailure { trackingNote = it.message ?: "unknown error" }
                }
            }
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
        playJob?.cancel()
        playing = null
        playerExpanded = true
    }

    private fun looksLikeYouTubeLink(text: String) =
        Regex("^https?://(www\\.|m\\.|music\\.)?(youtube\\.com|youtu\\.be)/\\S+$").matches(text)
}
