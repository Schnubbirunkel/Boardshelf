package io.github.schnubbirunkel.boardshelf

import android.app.Application
import io.github.schnubbirunkel.boardshelf.data.AppDatabase
import io.github.schnubbirunkel.boardshelf.data.Repository
import io.github.schnubbirunkel.boardshelf.data.SettingsStore
import io.github.schnubbirunkel.boardshelf.net.BggApi
import io.github.schnubbirunkel.boardshelf.net.WikiApi
import io.github.schnubbirunkel.boardshelf.net.createHttpClient
import io.github.schnubbirunkel.boardshelf.ui.box3d.TextureCache
import io.github.schnubbirunkel.boardshelf.util.AppFiles

class BoardshelfApp : Application() {
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
            bgg = BggApi(http) { settings.current.bggToken.ifBlank { BuildConfig.BGG_TOKEN } },
            products = io.github.schnubbirunkel.boardshelf.net.ProductApi(http),
            wiki = WikiApi(http),
            settings = settings,
        )
        textures = TextureCache(this, files)
    }
}
