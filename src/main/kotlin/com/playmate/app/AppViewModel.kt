package com.playmate.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playmate.app.data.Load
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.Playing
import com.playmate.app.data.RecentSearches
import com.playmate.app.data.SearchPage
import com.playmate.app.data.VideoItem
import com.playmate.app.data.YouTubeRepo
import com.playmate.app.data.attempt
import com.playmate.app.data.friendly
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab { Home, Search }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val recentStore = RecentSearches(app)

    var tab by mutableStateOf(Tab.Home); private set

    var feed by mutableStateOf<Load<List<VideoItem>>>(Load.Loading); private set
    var refreshing by mutableStateOf(false); private set

    var query by mutableStateOf(""); private set
    var editing by mutableStateOf(false); private set
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set
    var results by mutableStateOf<Load<SearchPage>?>(null); private set
    var loadingMore by mutableStateOf(false); private set
    var recents by mutableStateOf(recentStore.all()); private set

    var playing by mutableStateOf<Playing?>(null); private set

    private var feedJob: Job? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var playJob: Job? = null

    init {
        loadFeed()
    }

    fun select(target: Tab) {
        tab = target
    }

    fun loadFeed(force: Boolean = false) {
        if (!force && feed is Load.Ready) return
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            if (force) refreshing = true else feed = Load.Loading
            val result = attempt { YouTubeRepo.trending() }
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

    fun open(item: VideoItem) {
        playing = Playing(item, Load.Loading)
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val outcome: Load<PlayableInfo> = attempt { YouTubeRepo.load(item.url) }
                .fold({ Load.Ready(it) }, { Load.Failed(friendly(it)) })
            playing = playing?.takeIf { it.seed.url == item.url }?.copy(info = outcome)
        }
    }

    fun openUrl(url: String) {
        open(VideoItem(url, "", "", 0, -1, null, null))
    }

    fun retryPlayer() {
        playing?.seed?.let { open(it) }
    }

    fun closePlayer() {
        playJob?.cancel()
        playing = null
    }

    private fun looksLikeYouTubeLink(text: String) =
        Regex("^https?://(www\\.|m\\.|music\\.)?(youtube\\.com|youtu\\.be)/\\S+$").matches(text)
}
