package com.playmate.app.data

import android.content.Context

/** One saved search: the words, and the picture of its top result (shown beside it, like YouTube does). */
data class RecentSearch(val text: String, val thumb: String? = null)

/**
 * The saved searches of one kind of user. Signed-out searches and a signed-in account's searches are
 * kept in two separate lists ([account] picks which), so they never mix.
 */
class RecentSearches(context: Context, private val account: Boolean = false) {

    private val prefs = context.getSharedPreferences("playmate", Context.MODE_PRIVATE)
    private val key = if (account) KEY_ACCOUNT else KEY_GUEST

    // Saved as one line per search: "words<TAB>thumbnail". Older saves have no tab and still load fine.
    fun all(): List<RecentSearch> =
        prefs.getString(key, "")!!
            .split('\n')
            .filter { it.isNotBlank() }
            .map { line ->
                val cut = line.indexOf(SEP)
                if (cut < 0) RecentSearch(line)
                else RecentSearch(line.substring(0, cut), line.substring(cut + 1).ifBlank { null })
            }

    /** Puts [query] at the top. Keeps the picture it already had until a new one arrives. */
    fun add(query: String) {
        val old = all()
        val kept = old.firstOrNull { it.text.equals(query, ignoreCase = true) }?.thumb
        if (account) unhide(query)
        save((listOf(RecentSearch(query, kept)) + old.filterNot { it.text.equals(query, ignoreCase = true) }).take(MAX))
    }

    /** Attaches the top result's picture to a saved search, once the results have loaded. */
    fun setThumb(query: String, thumb: String?) {
        if (thumb.isNullOrBlank()) return
        save(all().map { if (it.text.equals(query, ignoreCase = true)) it.copy(thumb = thumb) else it })
    }

    fun remove(query: String) {
        if (account) hide(listOf(query))
        save(all().filterNot { it.text == query })
    }

    fun clear() {
        if (account) hide(all().map { it.text })
        save(emptyList())
    }

    /**
     * Account only. Takes the search history YouTube itself holds for the account ([remote], newest first).
     * Searches made here that YouTube has not listed yet stay on top; ones removed here stay hidden.
     */
    fun merge(remote: List<String>) {
        val hidden = hiddenSet()
        val local = all()
        val remoteKeys = remote.map { it.lowercase() }.toHashSet()
        val onlyHere = local.filter { it.text.lowercase() !in remoteKeys }
        val fromYouTube = remote
            .filter { it.isNotBlank() && it.lowercase() !in hidden }
            .map { text -> RecentSearch(text, local.firstOrNull { it.text.equals(text, ignoreCase = true) }?.thumb) }
        save((onlyHere + fromYouTube).distinctBy { it.text.lowercase() }.take(MAX))
    }

    /** Account only. Forgets everything saved for the account (used when signing out). */
    fun wipe() {
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_HIDDEN).apply()
    }

    private fun save(list: List<RecentSearch>) {
        prefs.edit()
            .putString(key, list.joinToString("\n") { it.text.replace('\n', ' ').replace(SEP, ' ') + SEP + (it.thumb ?: "") })
            .apply()
    }

    private fun hiddenSet(): Set<String> =
        prefs.getString(KEY_HIDDEN, "")!!.split('\n').filter { it.isNotBlank() }.toHashSet()

    private fun hide(texts: List<String>) {
        val set = hiddenSet().toMutableSet()
        texts.forEach { set += it.lowercase().replace('\n', ' ') }
        prefs.edit().putString(KEY_HIDDEN, set.take(200).joinToString("\n")).apply()
    }

    private fun unhide(text: String) {
        val set = hiddenSet().toMutableSet()
        if (set.remove(text.lowercase())) prefs.edit().putString(KEY_HIDDEN, set.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY_GUEST = "recent_searches"
        const val KEY_ACCOUNT = "recent_searches_account"
        const val KEY_HIDDEN = "recent_searches_account_hidden"
        const val SEP = '\t'
        const val MAX = 30
    }
}
