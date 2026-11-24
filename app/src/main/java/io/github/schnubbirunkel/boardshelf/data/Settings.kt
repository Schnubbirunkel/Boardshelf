package io.github.schnubbirunkel.boardshelf.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val bggToken: String = "",
    val bggUsername: String = "",
    val spineMode: Boolean = false,
    val useWikidata: Boolean = true,
    val aiProvider: io.github.schnubbirunkel.boardshelf.net.AiProvider = io.github.schnubbirunkel.boardshelf.net.AiProvider.NONE,
    val aiKey: String = "",
    val aiModel: String = "",
    val aiBaseUrl: String = "",
    val gameUpcKey: String = "",
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
        aiProvider = runCatching { io.github.schnubbirunkel.boardshelf.net.AiProvider.valueOf(prefs.getString("aiProvider", "NONE")!!) }
            .getOrDefault(io.github.schnubbirunkel.boardshelf.net.AiProvider.NONE),
        aiKey = prefs.getString("aiKey", "") ?: "",
        aiModel = prefs.getString("aiModel", "") ?: "",
        aiBaseUrl = prefs.getString("aiBaseUrl", "") ?: "",
        gameUpcKey = prefs.getString("gameUpcKey", "") ?: "",
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_state.value)
        prefs.edit()
            .putString("bggToken", s.bggToken.trim())
            .putString("bggUsername", s.bggUsername.trim())
            .putBoolean("spineMode", s.spineMode)
            .putBoolean("useWikidata", s.useWikidata)
            .putString("aiProvider", s.aiProvider.name)
            .putString("aiKey", s.aiKey.trim())
            .putString("aiModel", s.aiModel.trim())
            .putString("aiBaseUrl", s.aiBaseUrl.trim())
            .putString("gameUpcKey", s.gameUpcKey.trim())
            .apply()
        _state.value = s
    }
}
