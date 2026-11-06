package com.playmate.app.data

import android.content.Context

/** One saved search: the words, and the picture of its top result (shown beside it, like YouTube does). */
data class RecentSearch(val text: String, val thumb: String? = null)

class RecentSearches(context: Context) {

    private val prefs = context.getSharedPreferences("playmate", Context.MODE_PRIVATE)

    // Saved as one line per search: "words<TAB>thumbnail". Older saves have no tab and still load fine.
    fun all(): List<RecentSearch> =
        prefs.getString(KEY, "")!!
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
        save((listOf(RecentSearch(query, kept)) + old.filterNot { it.text.equals(query, ignoreCase = true) }).take(MAX))
    }

    /** Attaches the top result's picture to a saved search, once the results have loaded. */
    fun setThumb(query: String, thumb: String?) {
        if (thumb.isNullOrBlank()) return
        save(all().map { if (it.text.equals(query, ignoreCase = true)) it.copy(thumb = thumb) else it })
    }

    fun remove(query: String) = save(all().filterNot { it.text == query })

    fun clear() = save(emptyList())

    private fun save(list: List<RecentSearch>) {
        prefs.edit()
            .putString(KEY, list.joinToString("\n") { it.text.replace('\n', ' ').replace(SEP, ' ') + SEP + (it.thumb ?: "") })
            .apply()
    }

    private companion object {
        const val KEY = "recent_searches"
        const val SEP = '\t'
        const val MAX = 25
    }
}
