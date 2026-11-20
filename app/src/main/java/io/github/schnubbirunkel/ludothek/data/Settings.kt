package io.github.schnubbirunkel.ludothek.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val bggToken: String = "",
    val bggUsername: String = "",
    val spineMode: Boolean = false,
    val useWikidata: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    private fun read() = AppSettings(
        bggToken = prefs.getString("bggToken", "") ?: "",
        bggUsername = prefs.getString("bggUsername", "") ?: "",
        spineMode = prefs.getBoolean("spineMode", false),
        useWikidata = prefs.getBoolean("useWikidata", true),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_state.value)
        prefs.edit()
            .putString("bggToken", s.bggToken.trim())
            .putString("bggUsername", s.bggUsername.trim())
            .putBoolean("spineMode", s.spineMode)
            .putBoolean("useWikidata", s.useWikidata)
            .apply()
        _state.value = s
    }
}
