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
                            onBggImport = { nav.navigate("bggimport") },
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
                    screen("settings") { SettingsScreen(onBack = { nav.popBackStack() }, onBggImport = { nav.navigate("bggimport") }) }
                    screen("bggimport") { io.github.schnubbirunkel.boardshelf.ui.settings.BggImportScreen(onBack = { nav.popBackStack() }) }
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
            if (intent.getBooleanExtra("csv_test", false)) {
                runCatching { repo.importBggCsv(TEST_CSV, includeExpansions = false, onlyOwned = true) { _, _ -> } }
                    .onFailure { Log.e("Boardshelf", "csv test failed", it) }
                return@launch
            }
            intent.getStringExtra("barcode_import")?.let { code ->
                try {
                    val r = repo.lookupBarcode(code)
                    val id = r.existing?.id ?: repo.importFromBarcode(r.hits.firstOrNull(), r)
                    kotlinx.coroutines.delay(4000)
                    pendingRoute = "game/$id"
                } catch (t: Throwable) {
                    Log.e("Boardshelf", "barcode import failed", t)
                }
                return@launch
            }
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

private const val TEST_CSV = "objectname,objectid,rating,numplays,weight,own,fortrade,want,wanttobuy,wanttoplay,prevowned,preordered,wishlist,wishlistpriority,wishlistcomment,comment,conditiontext,haspartslist,wantpartslist,collid,baverage,average,avgweight,rank,numowned,objecttype,originalname,minplayers,maxplayers,playingtime,maxplaytime,minplaytime,yearpublished,bggrecplayers,bggbestplayers,bggrecagerange,bgglanguagedependence,publisherid,imageid,year,language,other,itemtype,pricepaid,pp_currency,currvalue,cv_currency,acquisitiondate,acquiredfrom,quantity,privatecomment\nCatan,13,7,0,0,1,0,0,0,0,0,0,0,,,,,,,1,6.9,7.1,2.3,500,1,thing,Catan,3,4,120,120,60,1995,3-4,4,10+,,,,,,,standalone,,,,,,,1,\n7 Wonders Duel,173346,9,0,0,1,0,0,0,0,0,0,0,,,Lieblingsspiel zu zweit,,,,2,7.9,8.1,2.2,20,1,thing,7 Wonders Duel,2,2,30,30,30,2015,2,2,10+,,,,,,,standalone,,,,,,,1,\nAzul,230802,8,0,0,1,0,0,0,0,0,0,0,,,,,,,3,7.6,7.7,1.8,80,1,thing,Azul,2,4,45,45,30,2017,2-4,2,8+,,,,,,,standalone,,,,,,,1,\nWingspan,266192,8.5,0,0,1,0,0,0,0,0,0,0,,,,,,,4,7.9,8.0,2.5,30,1,thing,Wingspan,1,5,70,70,40,2019,1-5,3,10+,,,,,,,standalone,,,,,,,1,\nTerraforming Mars,167791,9,0,0,1,0,0,0,0,0,0,0,,,,,,,5,8.2,8.4,3.3,10,1,thing,Terraforming Mars,1,5,120,120,120,2016,1-5,3,12+,,,,,,,standalone,,,,,,,1,\nCarcassonne,822,7,0,0,1,0,0,0,0,0,0,0,,,,,,,6,7.3,7.4,1.9,200,1,thing,Carcassonne,2,5,45,45,30,2000,2-5,2,7+,,,,,,,standalone,,,,,,,1,\nTicket to Ride,9209,7,0,0,1,0,0,0,0,0,0,0,,,,,,,7,7.3,7.4,1.8,220,1,thing,Ticket to Ride,2,5,60,60,30,2004,2-5,4,8+,,,,,,,standalone,,,,,,,1,\nPandemic,30549,7.5,0,0,1,0,0,0,0,0,0,0,,,,,,,8,7.5,7.6,2.4,120,1,thing,Pandemic,2,4,45,45,45,2008,2-4,4,8+,,,,,,,standalone,,,,,,,1,\nCodenames,178900,7,0,0,1,0,0,0,0,0,0,0,,,,,,,9,7.5,7.6,1.3,140,1,thing,Codenames,2,8,15,15,15,2015,2-8,6,14+,,,,,,,standalone,,,,,,,1,\nDominion,36218,7,0,0,1,0,0,0,0,0,0,0,,,,,,,10,7.5,7.6,2.4,130,1,thing,Dominion,2,4,30,30,30,2008,2-4,3,13+,,,,,,,standalone,,,,,,,1,\nSplendor,148228,7,0,0,1,0,0,0,0,0,0,0,,,,,,,11,7.3,7.4,1.8,210,1,thing,Splendor,2,4,30,30,30,2014,2-4,3,10+,,,,,,,standalone,,,,,,,1,\nAgricola,31260,8,0,0,1,0,0,0,0,0,0,0,,,,,,,12,7.8,7.9,3.6,40,1,thing,Agricola,1,5,150,150,30,2007,1-5,4,12+,,,,,,,standalone,,,,,,,1,\nBrass: Birmingham,224517,9.5,0,0,1,0,0,0,0,0,0,0,,,,,,,13,8.4,8.6,3.9,1,1,thing,Brass: Birmingham,2,4,120,120,60,2018,2-4,4,14+,,,,,,,standalone,,,,,,,1,\nCascadia,295947,8,0,0,1,0,0,0,0,0,0,0,,,,,,,14,7.8,7.9,1.9,60,1,thing,Cascadia,1,4,45,45,30,2021,1-4,3,10+,,,,,,,standalone,,,,,,,1,\nCatan: Seafarers,325,6,0,0,1,0,0,0,0,0,0,0,,,,,,,15,7.0,7.1,2.4,600,1,thing,Catan: Seafarers,3,4,90,90,60,1997,3-4,4,10+,,,,,,,expansion,,,,,,,1,\n"
