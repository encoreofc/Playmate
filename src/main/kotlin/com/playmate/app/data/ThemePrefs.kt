package com.playmate.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which look the app uses: follow the phone, or always light, or always dark. */
enum class ThemeMode { System, Light, Dark }

/** The chosen [ThemeMode], remembered across app starts. Reading [mode] in a composable updates it live. */
object ThemePrefs {

    var mode by mutableStateOf(ThemeMode.System)
        private set

    private var loaded = false

    /** Reads the saved choice. Called once, from MainActivity, before the first screen is drawn. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).getString(KEY, null)
        mode = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        prefs(context).edit().putString(KEY, value.name).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("playmate", Context.MODE_PRIVATE)

    private const val KEY = "theme_mode"
}
