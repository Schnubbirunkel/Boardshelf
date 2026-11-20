package io.github.schnubbirunkel.boardshelf

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.schnubbirunkel.boardshelf.demo.DemoData
import io.github.schnubbirunkel.boardshelf.ui.add.AddGameScreen
import io.github.schnubbirunkel.boardshelf.ui.detail.GameDetailScreen
import io.github.schnubbirunkel.boardshelf.ui.manuals.ManualBrowserScreen
import io.github.schnubbirunkel.boardshelf.ui.manuals.ManualViewerScreen
import io.github.schnubbirunkel.boardshelf.ui.manuals.PhotoViewerScreen
import io.github.schnubbirunkel.boardshelf.ui.manuals.ScanManualScreen
import io.github.schnubbirunkel.boardshelf.ui.settings.SettingsScreen
import io.github.schnubbirunkel.boardshelf.ui.shelf.ShelfScreen
import io.github.schnubbirunkel.boardshelf.ui.shelf.ShelvesScreen
import io.github.schnubbirunkel.boardshelf.ui.theme.BoardshelfTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleDebugExtras(savedInstanceState == null)

        setContent {
            BoardshelfTheme {
                val nav = rememberNavController()
                LaunchedEffect(pendingRoute) {
                    pendingRoute?.let { nav.navigate(it); pendingRoute = null }
                }
                NavHost(
                    navController = nav, startDestination = "shelf",
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(220)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(220)) },
                ) {
                    composable("shelf") {
                        ShelfScreen(
                            onOpenGame = { nav.navigate("game/$it?shelf=true") },
                            onAdd = { nav.navigate("add") },
                            onSettings = { nav.navigate("settings") },
                            onManageShelves = { nav.navigate("shelves") },
                        )
                    }
                    composable(
                        "game/{id}?tab={tab}&shelf={shelf}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                            navArgument("shelf") { type = NavType.BoolType; defaultValue = false },
                        )
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: return@composable
                        GameDetailScreen(
                            gameId = id,
                            initialTab = entry.arguments?.getInt("tab") ?: 0,
                            fromShelf = entry.arguments?.getBoolean("shelf") ?: false,
                            onBack = { nav.popBackStack() },
                            onOpenManual = { nav.navigate("manual/$it") },
                            onOpenPhoto = { nav.navigate("photo/$it") },
                            onBrowseManuals = { nav.navigate("browser/$it") },
                            onScanManual = { nav.navigate("scan/$it") },
                        )
                    }
                    composable("add") {
                        AddGameScreen(
                            onBack = { nav.popBackStack() },
                            onAdded = { id -> nav.navigate("game/$id") { popUpTo("shelf") } },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable("shelves") { ShelvesScreen(onBack = { nav.popBackStack() }) }
                    composable("manual/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ManualViewerScreen(it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                    composable("photo/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        PhotoViewerScreen(it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                    composable("browser/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ManualBrowserScreen(it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                    composable("scan/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ScanManualScreen(it.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    /**
     * Debug builds only – used by the screenshot workflow:
     * `--ez seed_demo true --es route game/1?tab=2 --ez spine true`
     */
    private fun handleDebugExtras(fresh: Boolean) {
        if (!BuildConfig.DEBUG || !fresh) return
        val repo = (application as BoardshelfApp).repository
        val seed = intent.getBooleanExtra("seed_demo", false)
        val route = intent.getStringExtra("route")
        if (intent.hasExtra("spine")) {
            val spine = intent.getBooleanExtra("spine", false)
            repo.settings.update { it.copy(spineMode = spine) }
        }
        intent.getStringExtra("lang")?.let { tag ->
            val wanted = LocaleListCompat.forLanguageTags(tag)
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
                AppCompatDelegate.setApplicationLocales(wanted)
            }
        }
        lifecycleScope.launch {
            try {
                if (seed && repo.db.games().count() == 0) DemoData.seed(this@MainActivity, repo)
            } catch (t: Throwable) {
                Log.e("Boardshelf", "Demo seeding failed", t)
            }
            if (route != null) pendingRoute = route
        }
    }
}
