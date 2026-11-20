package io.github.schnubbirunkel.ludothek

import android.app.Application
import io.github.schnubbirunkel.ludothek.data.AppDatabase
import io.github.schnubbirunkel.ludothek.data.Repository
import io.github.schnubbirunkel.ludothek.data.SettingsStore
import io.github.schnubbirunkel.ludothek.net.BggApi
import io.github.schnubbirunkel.ludothek.net.WikiApi
import io.github.schnubbirunkel.ludothek.net.createHttpClient
import io.github.schnubbirunkel.ludothek.ui.box3d.TextureCache
import io.github.schnubbirunkel.ludothek.util.AppFiles

class LudothekApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var textures: TextureCache
        private set

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsStore(this)
        val http = createHttpClient()
        val files = AppFiles(this)
        repository = Repository(
            context = this,
            db = AppDatabase.create(this),
            files = files,
            http = http,
            bgg = BggApi(http) { settings.current.bggToken },
            wiki = WikiApi(http),
            settings = settings,
        )
        textures = TextureCache(this, files)
    }
}
