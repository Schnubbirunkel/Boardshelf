package io.github.schnubbirunkel.ludothek

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import io.github.schnubbirunkel.ludothek.demo.DemoData
import io.github.schnubbirunkel.ludothek.ui.add.AddGameScreen
import io.github.schnubbirunkel.ludothek.ui.detail.GameDetailScreen
import io.github.schnubbirunkel.ludothek.ui.manuals.ManualBrowserScreen
import io.github.schnubbirunkel.ludothek.ui.manuals.ManualViewerScreen
import io.github.schnubbirunkel.ludothek.ui.manuals.PhotoViewerScreen
import io.github.schnubbirunkel.ludothek.ui.manuals.ScanManualScreen
import io.github.schnubbirunkel.ludothek.ui.settings.SettingsScreen
import io.github.schnubbirunkel.ludothek.ui.shelf.ShelfScreen
import io.github.schnubbirunkel.ludothek.ui.shelf.ShelvesScreen
import io.github.schnubbirunkel.ludothek.ui.theme.LudothekTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleDebugExtras(savedInstanceState == null)

        setContent {
            LudothekTheme {
                val nav = rememberNavController()
                LaunchedEffect(pendingRoute) {
                    pendingRoute?.let { nav.navigate(it); pendingRoute = null }
                }
                NavHost(navController = nav, startDestination = "shelf") {
                    composable("shelf") {
                        ShelfScreen(
                            onOpenGame = { nav.navigate("game/$it") },
                            onAdd = { nav.navigate("add") },
                            onSettings = { nav.navigate("settings") },
                            onManageShelves = { nav.navigate("shelves") },
                        )
                    }
                    composable(
                        "game/{id}?tab={tab}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                        )
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: return@composable
                        GameDetailScreen(
                            gameId = id,
                            initialTab = entry.arguments?.getInt("tab") ?: 0,
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
        val repo = (application as LudothekApp).repository
        val seed = intent.getBooleanExtra("seed_demo", false)
        val route = intent.getStringExtra("route")
        if (intent.hasExtra("spine")) {
            val spine = intent.getBooleanExtra("spine", false)
            repo.settings.update { it.copy(spineMode = spine) }
        }
        lifecycleScope.launch {
            if (seed && repo.db.games().count() == 0) DemoData.seed(this@MainActivity, repo)
            if (route != null) pendingRoute = route
        }
    }
}
