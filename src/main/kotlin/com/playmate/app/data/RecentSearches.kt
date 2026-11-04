package com.playmate.app.data

import android.content.Context

class RecentSearches(context: Context) {

    private val prefs = context.getSharedPreferences("playmate", Context.MODE_PRIVATE)

    fun all(): List<String> =
        prefs.getString(KEY, "")!!.split('\n').filter { it.isNotBlank() }

    fun add(query: String) {
        val updated = listOf(query) + all().filterNot { it.equals(query, ignoreCase = true) }
        save(updated.take(MAX))
    }

    fun remove(query: String) = save(all().filterNot { it == query })

    fun clear() = save(emptyList())

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY, list.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY = "recent_searches"
        const val MAX = 15
    }
}
