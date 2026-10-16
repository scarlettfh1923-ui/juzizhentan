package com.scarlett.juzizhentan.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scarlett.juzizhentan.data.AppContainer
import com.scarlett.juzizhentan.data.model.QuoteCard
import com.scarlett.juzizhentan.ui.analyze.AnalyzeScreen
import com.scarlett.juzizhentan.ui.analyze.AnalyzeViewModel
import com.scarlett.juzizhentan.ui.critique.CritiqueScreen
import com.scarlett.juzizhentan.ui.critique.CritiqueViewModel
import com.scarlett.juzizhentan.ui.discover.DiscoverScreen
import com.scarlett.juzizhentan.ui.discover.DiscoverViewModel
import com.scarlett.juzizhentan.ui.history.HistoryScreen
import com.scarlett.juzizhentan.ui.history.HistoryViewModel
import com.scarlett.juzizhentan.ui.home.HomeScreen
import com.scarlett.juzizhentan.ui.home.HomeViewModel
import com.scarlett.juzizhentan.ui.settings.SettingsScreen
import com.scarlett.juzizhentan.ui.settings.SettingsViewModel
import com.scarlett.juzizhentan.ui.training.TrainingScreen
import com.scarlett.juzizhentan.ui.training.TrainingViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val Home = "home"
    const val Discover = "discover"
    const val Analyze = "analyze/{payload}"
    const val Training = "training?technique={technique}&seed={seed}&theme={theme}&random={random}"
    const val Critique = "critique"
    const val History = "history"
    const val Settings = "settings"
}

private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
private val quoteAdapter = moshi.adapter(QuoteCard::class.java)

fun encodeQuote(card: QuoteCard): String {
    val json = quoteAdapter.toJson(card)
    return URLEncoder.encode(json, StandardCharsets.UTF_8.toString())
}

fun decodeQuote(payload: String): QuoteCard? {
    return try {
        val json = URLDecoder.decode(payload, StandardCharsets.UTF_8.toString())
        quoteAdapter.fromJson(json)
    } catch (_: Exception) {
        null
    }
}

@Composable
fun JuziNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val repo = container.repository
    // Shared training session holder for critique
    val session = remember { TrainingSession() }

    NavHost(navController = nav, startDestination = Routes.Home) {
        composable(Routes.Home) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repo))
            HomeScreen(
                vm = vm,
                onDiscover = { nav.navigate(Routes.Discover) },
                onTrain = { nav.navigate("training?technique=&seed=&theme=&random=false") },
                onRandom = { nav.navigate("training?technique=&seed=&theme=&random=true") },
                onHistory = { nav.navigate(Routes.History) },
                onSettings = { nav.navigate(Routes.Settings) },
                onOpenFavorite = { card ->
                    nav.navigate("analyze/${encodeQuote(card)}")
                }
            )
        }
        composable(Routes.Discover) {
            val vm: DiscoverViewModel = viewModel(factory = DiscoverViewModel.factory(repo))
            DiscoverScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onAnalyze = { card -> nav.navigate("analyze/${encodeQuote(card)}") },
                onTrain = { card ->
                    val seed = URLEncoder.encode(card.excerpt, "UTF-8")
                    nav.navigate("training?technique=&seed=$seed&theme=&random=false")
                }
            )
        }
        composable(
            Routes.Analyze,
            arguments = listOf(navArgument("payload") { type = NavType.StringType })
        ) { entry ->
            val payload = entry.arguments?.getString("payload").orEmpty()
            val card = decodeQuote(payload)
            val vm: AnalyzeViewModel = viewModel(factory = AnalyzeViewModel.factory(repo, card))
            AnalyzeScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onTrain = { excerpt ->
                    val seed = URLEncoder.encode(excerpt, "UTF-8")
                    nav.navigate("training?technique=&seed=$seed&theme=&random=false")
                }
            )
        }
        composable(
            Routes.Training,
            arguments = listOf(
                navArgument("technique") { type = NavType.StringType; defaultValue = "" },
                navArgument("seed") { type = NavType.StringType; defaultValue = "" },
                navArgument("theme") { type = NavType.StringType; defaultValue = "" },
                navArgument("random") { type = NavType.StringType; defaultValue = "false" }
            )
        ) { entry ->
            val technique = entry.arguments?.getString("technique")?.takeIf { it.isNotBlank() }
            val seed = entry.arguments?.getString("seed")?.let {
                runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull()?.takeIf { s -> s.isNotBlank() }
            }
            val theme = entry.arguments?.getString("theme")?.takeIf { it.isNotBlank() }
            val random = entry.arguments?.getString("random") == "true"
            val vm: TrainingViewModel = viewModel(
                factory = TrainingViewModel.factory(repo, technique, theme, seed, random)
            )
            TrainingScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSubmit = { level, selections, seedExcerpt, tech ->
                    session.level = level
                    session.selections = selections
                    session.seed = seedExcerpt
                    session.technique = tech
                    nav.navigate(Routes.Critique)
                }
            )
        }
        composable(Routes.Critique) {
            val vm: CritiqueViewModel = viewModel(
                factory = CritiqueViewModel.factory(
                    repo,
                    session.level,
                    session.selections,
                    session.seed,
                    session.technique
                )
            )
            CritiqueScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onRetry = {
                    nav.popBackStack()
                },
                onNext = {
                    nav.navigate("training?technique=&seed=&theme=&random=true") {
                        popUpTo(Routes.Home)
                    }
                }
            )
        }
        composable(Routes.History) {
            val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(repo))
            HistoryScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.Settings) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(repo))
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

class TrainingSession {
    var level: com.scarlett.juzizhentan.data.model.TrainingLevel? = null
    var selections: Map<String, String> = emptyMap()
    var seed: String? = null
    var technique: String? = null
}
