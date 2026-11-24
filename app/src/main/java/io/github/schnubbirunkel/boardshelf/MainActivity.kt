package io.github.schnubbirunkel.boardshelf

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import io.github.schnubbirunkel.boardshelf.ui.add.BarcodeScreen
import io.github.schnubbirunkel.boardshelf.ui.theme.LiquidBackground
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
import io.github.schnubbirunkel.boardshelf.ui.manuals.CoverSearchScreen
import io.github.schnubbirunkel.boardshelf.ui.manuals.CropScreen
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
                Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav, startDestination = "shelf",
                    // One UI style: new screens glide in from the right with a soft spring;
                    // the shelf → game transition is a plain fade (the box animation does the rest).
                    enterTransition = {
                        if (targetState.arguments?.getBoolean("shelf") == true) fadeIn(tween(220))
                        else slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 4 } + fadeIn(tween(220))
                    },
                    exitTransition = {
                        if (targetState.arguments?.getBoolean("shelf") == true) fadeOut(tween(220))
                        else fadeOut(tween(200)) + scaleOut(targetScale = 0.94f, animationSpec = tween(260))
                    },
                    popEnterTransition = { fadeIn(tween(240)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f)) },
                    popExitTransition = { slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 4 } + fadeOut(tween(200)) },
                ) {
                    screen("shelf") {
                        ShelfScreen(
                            onOpenGame = { nav.navigate("game/$it?shelf=true") },
                            onAdd = { nav.navigate("add") },
                            onScan = { nav.navigate("barcode") },
                            onSettings = { nav.navigate("settings") },
                            onManageShelves = { nav.navigate("shelves") },
                        )
                    }
                    screen(
                        "game/{id}?tab={tab}&shelf={shelf}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                            navArgument("shelf") { type = NavType.BoolType; defaultValue = false },
                        )
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: return@screen
                        GameDetailScreen(
                            gameId = id,
                            initialTab = entry.arguments?.getInt("tab") ?: 0,
                            fromShelf = entry.arguments?.getBoolean("shelf") ?: false,
                            onBack = { nav.popBackStack() },
                            onOpenManual = { nav.navigate("manual/$it") },
                            onOpenPhoto = { stateId, index -> nav.navigate("photo/$stateId?index=$index") },
                            onBrowseManuals = { nav.navigate("browser/$it") },
                            onScanManual = { nav.navigate("scan/$it") },
                            onSearchCover = { nav.navigate("cover/$it") },
                            onCropCover = { path -> nav.navigate("crop/$id?path=${Uri.encode(path)}") },
                        )
                    }
                    screen("add") {
                        AddGameScreen(
                            onBack = { nav.popBackStack() },
                            onAdded = { id -> nav.navigate("game/$id") { popUpTo("shelf") } },
                            onSettings = { nav.navigate("settings") },
                            onScan = { nav.navigate("barcode") },
                        )
                    }
                    screen("barcode?code={code}", arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                        BarcodeScreen(
                            initialCode = it.arguments?.getString("code"),
                            onBack = { nav.popBackStack() },
                            onOpenGame = { id -> nav.navigate("game/$id") { popUpTo("shelf") } },
                        )
                    }
                    screen("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    screen("shelves") { ShelvesScreen(onBack = { nav.popBackStack() }) }
                    screen("manual/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ManualViewerScreen(it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                    screen(
                        "photo/{id}?index={index}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("index") { type = NavType.IntType; defaultValue = 0 })
                    ) {
                        PhotoViewerScreen(it.arguments?.getLong("id") ?: 0L, it.arguments?.getInt("index") ?: 0, onBack = { nav.popBackStack() })
                    }
                    screen("browser/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ManualBrowserScreen(it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                    screen("cover/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        val gid = it.arguments?.getLong("id") ?: 0L
                        CoverSearchScreen(gid, onDone = { nav.popBackStack() }, onPicked = { path ->
                            nav.navigate("crop/$gid?path=${Uri.encode(path)}") { popUpTo("cover/{id}") { inclusive = true } }
                        })
                    }
                    screen(
                        "crop/{id}?path={path}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("path") { type = NavType.StringType; defaultValue = "" },
                        )
                    ) {
                        CropScreen(it.arguments?.getLong("id") ?: 0L, it.arguments?.getString("path") ?: "", onDone = { nav.popBackStack() })
                    }
                    screen("scan/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ScanManualScreen(it.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
                    }
                }
                }
            }
        }
    }

    /** Every destination sits on the animated liquid background (opaque, so screens can slide). */
    private fun NavGraphBuilder.screen(
        route: String,
        arguments: List<NamedNavArgument> = emptyList(),
        content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
    ) = composable(route, arguments) { entry ->
        val scope = this
        Box(Modifier.fillMaxSize()) {
            LiquidBackground()
            scope.content(entry)
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
            val cropGame = intent.getLongExtra("crop_game", -1L)
            intent.getStringExtra("barcode")?.let { pendingRoute = "barcode?code=$it"; return@launch }
            if (cropGame > 0) {
                repo.db.games().get(cropGame)?.coverPath?.let { rel ->
                    val f = repo.coverCandidateFrom(repo.files.resolve(rel), null)
                    pendingRoute = "crop/$cropGame?path=${Uri.encode(f.path)}"
                }
            } else if (route != null) pendingRoute = route
        }
    }
}
